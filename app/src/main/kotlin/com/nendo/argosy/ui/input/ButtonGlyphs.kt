package com.nendo.argosy.ui.input

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import com.nendo.argosy.core.input.ControllerDetector
import com.nendo.argosy.core.input.DetectedLayout
import com.nendo.argosy.data.preferences.UserPreferences
import kotlinx.coroutines.flow.Flow

data class ButtonGlyphSwaps(
    val ab: Boolean,
    val xy: Boolean,
    val startSelect: Boolean
)

fun UserPreferences.buttonGlyphSwaps(detected: DetectedLayout?): ButtonGlyphSwaps {
    val nintendo = ControllerDetector.isNintendoLayout(controllerLayout, detected)
    return ButtonGlyphSwaps(
        ab = nintendo xor swapAB,
        xy = nintendo xor swapXY,
        startSelect = swapStartSelect
    )
}

/**
 * Supplies the glyph swap locals to [content] for a surface that is not hosted inside `ArgosyApp`.
 */
@Composable
fun ProvideButtonGlyphs(preferences: Flow<UserPreferences>, content: @Composable () -> Unit) {
    val prefs by preferences.collectAsState(initial = null)
    val swaps = remember(prefs) {
        prefs?.buttonGlyphSwaps(ControllerDetector.getDetectedLayout())
            ?: ButtonGlyphSwaps(ab = false, xy = false, startSelect = false)
    }
    CompositionLocalProvider(
        LocalABIconsSwapped provides swaps.ab,
        LocalXYIconsSwapped provides swaps.xy,
        LocalSwapStartSelect provides swaps.startSelect,
        content = content
    )
}
