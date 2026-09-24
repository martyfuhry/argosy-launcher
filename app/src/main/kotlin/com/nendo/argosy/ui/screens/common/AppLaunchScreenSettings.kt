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
    val screenKey: String
)

/**
 * The screen an Android app opens on from the home rows and the library, remembered per app.
 * Stored as the chosen panel's stable screen key, so neither a role swap nor a restart moves it.
 */
@Singleton
class AppLaunchScreenSettings @Inject constructor(
    private val screenCatalog: ScreenCatalog,
    private val displayAffinityHelper: DisplayAffinityHelper,
    private val emulatorConfigRepository: EmulatorConfigRepository,
    private val configureEmulatorUseCase: ConfigureEmulatorUseCase
) {
    fun choices(): List<LaunchScreenChoice> = screenCatalog.attachedScreens().map { screen ->
        LaunchScreenChoice(displayId = screen.displayId, number = screen.number, screenKey = screen.key)
    }

    /**
     * The attached screen stored for [gameId], or null when none is stored or it is not attached.
     * A role name stored by an earlier build is read as the screen the stored layout gives it.
     */
    suspend fun storedChoice(gameId: Long): LaunchScreenChoice? {
        val raw = emulatorConfigRepository.getDisplayTargetForGame(gameId) ?: return null
        val choices = choices()
        screenKeyOf(raw)?.let { key -> return choices.find { it.screenKey == key } }
        val legacyDisplayId = layoutDisplayIdFor(EmulatorDisplayTarget.fromString(raw)) ?: return null
        return choices.find { it.displayId == legacyDisplayId }
    }

    suspend fun store(gameId: Long, displayId: Int?) {
        val key = displayId?.let { screenCatalog.screenFor(it)?.key }
        configureEmulatorUseCase.setDisplayTargetForGame(gameId, key?.let(::screenToken))
    }

    private fun layoutDisplayIdFor(target: EmulatorDisplayTarget): Int? {
        val (primary, presentation) = displayAffinityHelper.roleDisplayIds ?: return null
        return when (target) {
            EmulatorDisplayTarget.PRIMARY -> primary
            EmulatorDisplayTarget.PRESENTATION -> presentation
            EmulatorDisplayTarget.APP_SCREEN -> displayAffinityHelper.appTargetDisplayId
            EmulatorDisplayTarget.DEFAULT -> null
        }
    }

    companion object {
        private const val SCREEN_TOKEN_PREFIX = "screen:"

        internal fun screenToken(screenKey: String): String = SCREEN_TOKEN_PREFIX + screenKey

        internal fun screenKeyOf(stored: String): String? =
            stored.takeIf { it.startsWith(SCREEN_TOKEN_PREFIX) }
                ?.removePrefix(SCREEN_TOKEN_PREFIX)
                ?.takeIf { it.isNotEmpty() }
    }
}
