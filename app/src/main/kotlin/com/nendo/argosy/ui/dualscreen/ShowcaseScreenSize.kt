package com.nendo.argosy.ui.dualscreen

import android.hardware.display.DisplayManager
import android.util.DisplayMetrics
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import com.nendo.argosy.DualScreenManagerHolder

/**
 * The full size of the screen currently describing the interactive one, or null when [enabled] is
 * false or the device shows no presentation screen.
 */
@Composable
fun rememberShowcaseScreenSize(enabled: Boolean): DpSize? {
    val manager = DualScreenManagerHolder.instance ?: return null
    val swapped by manager.isRolesSwapped.collectAsState()
    val hasPresentation by manager.hasPresentationScreen.collectAsState()
    val context = LocalContext.current
    return remember(enabled, swapped, hasPresentation) {
        if (!enabled || !hasPresentation) return@remember null
        val displayId = manager.showcaseDisplayId() ?: return@remember null
        val display = context.getSystemService(DisplayManager::class.java)?.getDisplay(displayId)
            ?: return@remember null
        val metrics = DisplayMetrics()
        @Suppress("DEPRECATION")
        display.getRealMetrics(metrics)
        DpSize((metrics.widthPixels / metrics.density).dp, (metrics.heightPixels / metrics.density).dp)
    }
}
