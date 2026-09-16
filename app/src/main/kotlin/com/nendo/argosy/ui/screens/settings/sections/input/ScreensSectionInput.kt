package com.nendo.argosy.ui.screens.settings.sections.input

import com.nendo.argosy.core.input.SoundType
import com.nendo.argosy.ui.input.InputHandler
import com.nendo.argosy.ui.input.InputResult
import com.nendo.argosy.ui.screens.settings.SettingsViewModel
import com.nendo.argosy.ui.screens.settings.sections.screensMaxFocusIndex

internal class ScreensSectionInput(
    private val viewModel: SettingsViewModel
) : InputHandler {

    override fun onUp(): InputResult = move(-1)

    override fun onDown(): InputResult = move(1)

    override fun onLeft(): InputResult = move(-1)

    override fun onRight(): InputResult = move(1)

    private fun move(delta: Int): InputResult {
        val max = screensMaxFocusIndex(viewModel.uiState.value.display.screens)
        return if (viewModel.moveFocusWrapped(delta, max)) {
            InputResult.HANDLED
        } else {
            InputResult.handled(SoundType.BOUNDARY)
        }
    }
}
