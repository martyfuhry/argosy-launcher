package com.nendo.argosy.ui.screens.common

import com.nendo.argosy.data.preferences.EmulatorDisplayTarget
import com.nendo.argosy.data.repository.EmulatorConfigRepository
import com.nendo.argosy.domain.usecase.game.ConfigureEmulatorUseCase
import com.nendo.argosy.util.DisplayAffinityHelper
import com.nendo.argosy.util.ScreenCatalog
import javax.inject.Inject
import javax.inject.Singleton

data class LaunchScreenChoice(
    val displayId: Int,
    val number: Int,
    val target: EmulatorDisplayTarget
)

/**
 * The screen an Android app opens on from the home rows and the library, remembered per app.
 * Stored as the layout role the chosen screen holds, so the choice names the same physical
 * panel whether or not the roles are swapped.
 */
@Singleton
class AppLaunchScreenSettings @Inject constructor(
    private val screenCatalog: ScreenCatalog,
    private val displayAffinityHelper: DisplayAffinityHelper,
    private val emulatorConfigRepository: EmulatorConfigRepository,
    private val configureEmulatorUseCase: ConfigureEmulatorUseCase
) {
    fun choices(): List<LaunchScreenChoice> = screenCatalog.attachedScreens().mapNotNull { screen ->
        displayAffinityHelper.getLayoutDisplayTarget(screen.displayId)?.let { target ->
            LaunchScreenChoice(displayId = screen.displayId, number = screen.number, target = target)
        }
    }

    suspend fun storedChoice(gameId: Long): LaunchScreenChoice? {
        val raw = emulatorConfigRepository.getDisplayTargetForGame(gameId) ?: return null
        val target = EmulatorDisplayTarget.fromString(raw)
        if (target == EmulatorDisplayTarget.DEFAULT) return null
        return choices().find { it.target == target }
    }

    suspend fun store(gameId: Long, displayId: Int?) {
        val target = displayId?.let { displayAffinityHelper.getLayoutDisplayTarget(it) }
        configureEmulatorUseCase.setDisplayTargetForGame(gameId, target?.name)
    }
}
