/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.utils

import android.annotation.SuppressLint
import android.app.Application
import android.content.Context
import android.content.res.AssetManager
import android.net.Uri
import android.os.Build
import android.webkit.WebView
import dalvik.system.DexClassLoader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.util.zip.ZipFile

/**
 * Lets Android 5.x use a WebView engine from a user-supplied APK instead of the system one.
 *
 * Before Android 7 the WebView provider is hardwired by the ROM and often can't be updated at all,
 * so devices are stuck with an engine too old for Google sign-in. This mirrors what
 * android.webkit.WebViewFactory does on API 21/22, but loads the APK from app storage, and must
 * run before anything in the process touches WebView.
 */
object CustomWebView {
    private const val DIR = "webview"
    private const val APK = "webview.apk"
    private const val LIB_DIR = "lib"
    private const val OAT_DIR = "oat"
    private const val VERSION_FILE = "version"
    private const val NATIVE_LIB = "libwebviewchromium.so"
    private const val FACTORY_CLASS = "com.android.webview.chromium.WebViewChromiumFactoryProvider"

    val isSupported: Boolean
        get() = Build.VERSION.SDK_INT <= Build.VERSION_CODES.LOLLIPOP_MR1

    var isActive: Boolean = false
        private set

    fun installedVersion(context: Context): String? =
        File(rootDir(context), VERSION_FILE).takeIf { it.isFile }?.readText()

    @SuppressLint("PrivateApi", "DiscouragedPrivateApi")
    fun load(app: Application) {
        if (!isSupported) return
        val dir = rootDir(app)
        val apk = File(dir, APK)
        if (!apk.isFile) return

        var packageInfoField: java.lang.reflect.Field? = null
        try {
            val factory = Class.forName("android.webkit.WebViewFactory")
            packageInfoField = factory.staticField("sPackageInfo")
            synchronized(factory.staticField("sProviderLock").get(null)!!) {
                val providerField = factory.staticField("sProviderInstance")
                check(providerField.get(null) == null) { "System WebView is already initialized" }

                val packageInfo =
                    checkNotNull(app.packageManager.getPackageArchiveInfo(apk.path, 0)) { "Unreadable APK" }
                checkNotNull(packageInfo.applicationInfo).apply {
                    sourceDir = apk.path
                    publicSourceDir = apk.path
                    nativeLibraryDir = File(dir, LIB_DIR).path
                }
                val providerClass = Class.forName(FACTORY_CLASS, true, classLoader(dir))

                // The provider reads its package info back from WebViewFactory while constructing.
                packageInfoField.set(null, packageInfo)
                AssetManager::class.java
                    .getMethod("addAssetPath", String::class.java)
                    .invoke(app.assets, apk.path)
                val provider =
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
                        val delegateClass = Class.forName("android.webkit.WebViewDelegate")
                        val delegate = delegateClass.getDeclaredConstructor().apply { isAccessible = true }.newInstance()
                        providerClass.getConstructor(delegateClass).newInstance(delegate)
                    } else {
                        providerClass.getConstructor().newInstance()
                    }
                providerField.set(null, provider)
                isActive = true
                Timber.i("Using custom WebView ${packageInfo.packageName} ${packageInfo.versionName}")
            }
        } catch (e: Throwable) {
            runCatching { packageInfoField?.set(null, null) }
            Timber.e(e, "Failed to load custom WebView, falling back to the system one")
        }
    }

    /** Copies and prepares a WebView APK; it takes effect after the app restarts. */
    @SuppressLint("PrivateApi")
    suspend fun install(context: Context, uri: Uri): Result<String> =
        withContext(Dispatchers.IO) {
            val staging = File(context.cacheDir, "$DIR-staging")
            val dir = rootDir(context)
            runCatching {
                staging.deleteRecursively()
                staging.mkdirs()
                val apk = File(staging, APK)
                checkNotNull(context.contentResolver.openInputStream(uri)) { "Can't open file" }.use { input ->
                    apk.outputStream().use { input.copyTo(it) }
                }
                val packageInfo =
                    checkNotNull(context.packageManager.getPackageArchiveInfo(apk.path, 0)) { "Not a valid APK" }
                extractNativeLibs(apk, File(staging, LIB_DIR), processAbis(context))
                File(staging, VERSION_FILE).writeText(packageInfo.versionName ?: "?")

                dir.deleteRecursively()
                check(staging.renameTo(dir)) { "Can't move WebView into place" }
                // Optimizes the dex now so the next cold start doesn't block on it, and proves the APK is a WebView.
                classLoader(dir).loadClass(FACTORY_CLASS)
                packageInfo.versionName ?: "?"
            }.onFailure {
                Timber.e(it, "Failed to install custom WebView")
                staging.deleteRecursively()
                dir.deleteRecursively()
            }
        }

    fun reset(context: Context) {
        rootDir(context).deleteRecursively()
    }

    private fun rootDir(context: Context) = File(context.filesDir, DIR)

    private fun classLoader(dir: File) =
        DexClassLoader(
            File(dir, APK).path,
            File(dir, OAT_DIR).apply { mkdirs() }.path,
            File(dir, LIB_DIR).path,
            WebView::class.java.classLoader,
        )

    private fun processAbis(context: Context): Array<String> {
        val is64Bit =
            Build.SUPPORTED_64_BIT_ABIS.isNotEmpty() && context.applicationInfo.nativeLibraryDir.endsWith("64")
        return if (is64Bit) Build.SUPPORTED_64_BIT_ABIS else Build.SUPPORTED_32_BIT_ABIS
    }

    private fun extractNativeLibs(apk: File, libDir: File, abis: Array<String>) {
        ZipFile(apk).use { zip ->
            val abi =
                abis.firstOrNull { zip.getEntry("lib/$it/$NATIVE_LIB") != null }
                    ?: error("APK has no WebView library for ${abis.joinToString()}")
            libDir.mkdirs()
            zip.entries().asSequence()
                .filter { !it.isDirectory && it.name.startsWith("lib/$abi/") && it.name.endsWith(".so") }
                .forEach { entry ->
                    zip.getInputStream(entry).use { input ->
                        File(libDir, entry.name.substringAfterLast('/')).outputStream().use { input.copyTo(it) }
                    }
                }
        }
    }

    private fun Class<*>.staticField(name: String) = getDeclaredField(name).apply { isAccessible = true }
}
