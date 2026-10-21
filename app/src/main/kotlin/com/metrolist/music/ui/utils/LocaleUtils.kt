package com.metrolist.music.ui.utils

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.core.os.ConfigurationCompat
import java.util.Locale

// Backport of LocalLocale.current.platformLocale (Compose 1.10+, which requires minSdk 23).
val currentPlatformLocale: Locale
    @Composable
    @ReadOnlyComposable
    get() = ConfigurationCompat.getLocales(LocalConfiguration.current)[0] ?: Locale.getDefault()
