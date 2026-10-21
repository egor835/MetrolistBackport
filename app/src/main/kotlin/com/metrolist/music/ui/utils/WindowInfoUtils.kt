package com.metrolist.music.ui.utils

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.WindowInfo
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp

// Backport of WindowInfo.containerDpSize (Compose 1.10+, which requires minSdk 23).
// Converts with the window's real density instead of LocalDensity, which MainActivity rescales.
val WindowInfo.containerDpSize: DpSize
    @Composable
    @ReadOnlyComposable
    get() {
        val density = LocalContext.current.resources.displayMetrics.density
        return DpSize((containerSize.width / density).dp, (containerSize.height / density).dp)
    }
