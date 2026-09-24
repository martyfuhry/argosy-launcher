package com.nendo.argosy.ui.screens.common

import android.content.Context
import com.nendo.argosy.data.preferences.EmulatorDisplayTarget
import com.nendo.argosy.data.preferences.SessionStateStore
import com.nendo.argosy.data.preferences.UserPreferencesRepository
import com.nendo.argosy.data.repository.EmulatorConfigRepository
import com.nendo.argosy.data.repository.GameRepository
import com.nendo.argosy.domain.usecase.game.ConfigureEmulatorUseCase
import com.nendo.argosy.util.DisplayAffinityHelper
import com.nendo.argosy.util.ScreenCatalog
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

data class LaunchScreenChoice(
    val displayId: Int,
    val number: Int,
    val screenKey: String
)

/**
 * The screen an Android app opens on from game details, the home rows and the library. Kept in
 * the same per-package pin the apps drawer and app bar use (`appDisplayTargets`), keyed by the
 * panel's stable screen key, so neither a role swap nor a restart moves it and both entry points
 * share one choice.
 */
@Singleton
class AppLaunchScreenSettings @Inject constructor(
    @ApplicationContext context: Context,
    private val screenCatalog: ScreenCatalog,
    private val displayAffinityHelper: DisplayAffinityHelper,
    private val preferencesRepository: UserPreferencesRepository,
    private val gameRepository: GameRepository,
    private val emulatorConfigRepository: EmulatorConfigRepository,
    private val configureEmulatorUseCase: ConfigureEmulatorUseCase
) {
    private val sessionStateStore by lazy { SessionStateStore(context) }

    fun choices(): List<LaunchScreenChoice> = screenCatalog.attachedScreens().map { screen ->
        LaunchScreenChoice(displayId = screen.displayId, number = screen.number, screenKey = screen.key)
    }

    /**
     * The attached screen pinned for [gameId]'s package, or null when none is pinned or it is not
     * attached. A per-game choice stored by an earlier fork build is moved into the pin on read.
     */
    suspend fun storedChoice(gameId: Long): LaunchScreenChoice? {
        val packageName = packageOf(gameId) ?: return null
        val choices = choices()
        val pinned = preferencesRepository.preferences.first().appDisplayTargets[packageName]
            ?: adoptLegacyChoice(gameId, packageName, choices)
            ?: return null
        return choices.find { it.screenKey == pinned }
    }

    suspend fun store(gameId: Long, displayId: Int?) {
        val packageName = packageOf(gameId) ?: return
        val key = displayId?.let { screenCatalog.screenFor(it)?.key }
        preferencesRepository.setAppDisplayTarget(packageName, key)
    }

    private suspend fun packageOf(gameId: Long): String? =
        gameRepository.getById(gameId)?.packageName?.takeIf { it.isNotBlank() }

    private suspend fun adoptLegacyChoice(
        gameId: Long,
        packageName: String,
        choices: List<LaunchScreenChoice>
    ): String? {
        val raw = emulatorConfigRepository.getDisplayTargetForGame(gameId) ?: return null
        val key = legacyScreenKeyOf(raw)
            ?: displayAffinityHelper.getDisplayTargetId(
                EmulatorDisplayTarget.fromString(raw),
                sessionStateStore.isRolesSwapped()
            )?.let { id -> choices.find { it.displayId == id }?.screenKey }
            ?: return null
        configureEmulatorUseCase.setDisplayTargetForGame(gameId, null)
        preferencesRepository.setAppDisplayTarget(packageName, key)
        return key
    }

    companion object {
        private const val LEGACY_SCREEN_TOKEN_PREFIX = "screen:"

        internal fun legacyScreenKeyOf(stored: String): String? =
            stored.takeIf { it.startsWith(LEGACY_SCREEN_TOKEN_PREFIX) }
                ?.removePrefix(LEGACY_SCREEN_TOKEN_PREFIX)
                ?.takeIf { it.isNotEmpty() }
    }
}
