package com.nendo.argosy.ui.screens.common

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.Display
import com.nendo.argosy.DualScreenManagerHolder
import com.nendo.argosy.data.emulator.LaunchDisplayPlanner
import com.nendo.argosy.data.emulator.isAlreadyLaunched
import com.nendo.argosy.data.emulator.launchedDisplayId
import com.nendo.argosy.data.model.GameSource
import com.nendo.argosy.data.preferences.SessionStateStore
import com.nendo.argosy.data.repository.GameRepository
import com.nendo.argosy.util.DisplayAffinityHelper
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * A game intent with the placement its display target resolved to.
 */
data class GameLaunchRequest(
    val intent: android.content.Intent,
    val options: Bundle? = null
)

@Singleton
class EmulatorLaunchTargetResolver @Inject constructor(
    @ApplicationContext context: Context,
    private val displayAffinityHelper: DisplayAffinityHelper,
    private val launchDisplayPlanner: LaunchDisplayPlanner,
    private val gameRepository: GameRepository,
    private val appLaunchScreenSettings: AppLaunchScreenSettings
) {
    private val sessionStateStore by lazy { SessionStateStore(context) }

    /**
     * The screen chosen for this launch of [gameId]: [overrideDisplayId], else an Android app's
     * pinned launch screen while that panel is attached. Null leaves placement to the planner.
     */
    suspend fun chosenDisplayIdFor(gameId: Long, overrideDisplayId: Int? = null): Int? {
        overrideDisplayId?.let { return it }
        if (gameRepository.getById(gameId)?.source != GameSource.ANDROID_APP) return null
        return appLaunchScreenSettings.storedChoice(gameId)?.displayId
    }

    /**
     * The placement for starting [intent], with the display the game lands on recorded for the
     * dual-screen manager. A shell launch's stand-in intent keeps the display that launch used.
     */
    suspend fun launchOptionsFor(gameId: Long, intent: Intent, overrideDisplayId: Int? = null): Bundle? {
        val rolesSwapped = sessionStateStore.isRolesSwapped()
        val displayId = if (intent.isAlreadyLaunched()) {
            intent.launchedDisplayId()
        } else {
            launchDisplayPlanner.displayFor(
                gameId = gameId,
                drawsSecondScreen = LaunchDisplayPlanner.drawsSecondScreen(intent),
                overrideDisplayId = chosenDisplayIdFor(gameId, overrideDisplayId)
            )
        }
        DualScreenManagerHolder.instance?.setEmulatorDisplay(displayId ?: Display.DEFAULT_DISPLAY)
        return displayAffinityHelper.getActivityOptions(
            forEmulator = true,
            rolesSwapped = rolesSwapped,
            overrideDisplayId = displayId
        )
    }
}
