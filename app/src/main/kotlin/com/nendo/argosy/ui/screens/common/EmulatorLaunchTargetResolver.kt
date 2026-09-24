package com.nendo.argosy.ui.screens.common

import android.content.Context
import android.content.Intent
import android.os.Bundle
import com.nendo.argosy.DualScreenManagerHolder
import com.nendo.argosy.data.emulator.LaunchDisplayPlanner
import com.nendo.argosy.data.preferences.SessionStateStore
import com.nendo.argosy.util.DisplayAffinityHelper
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class EmulatorLaunchTargetResolver @Inject constructor(
    @ApplicationContext context: Context,
    private val displayAffinityHelper: DisplayAffinityHelper,
    private val launchDisplayPlanner: LaunchDisplayPlanner
) {
    private val sessionStateStore by lazy { SessionStateStore(context) }

    suspend fun launchOptionsFor(gameId: Long, intent: Intent, overrideDisplayId: Int? = null): Bundle? {
        val rolesSwapped = sessionStateStore.isRolesSwapped()
        val displayId = launchDisplayPlanner.displayFor(
            gameId = gameId,
            drawsSecondScreen = LaunchDisplayPlanner.drawsSecondScreen(intent),
            overrideDisplayId = overrideDisplayId
        )
        DualScreenManagerHolder.instance?.setEmulatorDisplay(
            displayId ?: displayAffinityHelper.getEmulatorDisplayId(rolesSwapped)
        )
        return displayAffinityHelper.getActivityOptions(
            forEmulator = true,
            rolesSwapped = rolesSwapped,
            overrideDisplayId = displayId
        )
    }
}
