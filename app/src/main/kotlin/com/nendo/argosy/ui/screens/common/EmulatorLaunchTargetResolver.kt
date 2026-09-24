package com.nendo.argosy.ui.screens.common

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.Display
import com.nendo.argosy.DualScreenManagerHolder
import com.nendo.argosy.data.emulator.LaunchDisplayPlanner
import com.nendo.argosy.data.emulator.isAlreadyLaunched
import com.nendo.argosy.data.emulator.launchedDisplayId
import com.nendo.argosy.data.preferences.SessionStateStore
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
    private val launchDisplayPlanner: LaunchDisplayPlanner
) {
    private val sessionStateStore by lazy { SessionStateStore(context) }

    /**
     * The placement for starting [intent]. A shell launch has already started its emulator, so
     * its stand-in intent keeps the display that launch used instead of being placed again. The
     * display the game ends up on is recorded with the dual-screen manager either way.
     */
    suspend fun launchOptionsFor(gameId: Long, intent: Intent, overrideDisplayId: Int? = null): Bundle? {
        val rolesSwapped = sessionStateStore.isRolesSwapped()
        val displayId = if (intent.isAlreadyLaunched()) {
            intent.launchedDisplayId()
        } else {
            launchDisplayPlanner.displayFor(
                gameId = gameId,
                drawsSecondScreen = LaunchDisplayPlanner.drawsSecondScreen(intent),
                overrideDisplayId = overrideDisplayId
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
