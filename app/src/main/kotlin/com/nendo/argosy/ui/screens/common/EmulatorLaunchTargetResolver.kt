package com.nendo.argosy.ui.screens.common

import android.content.Context
import android.content.Intent
import android.os.Bundle
import com.nendo.argosy.DualScreenManagerHolder
import com.nendo.argosy.data.emulator.LaunchDisplayPlanner
import com.nendo.argosy.data.model.GameSource
import com.nendo.argosy.data.preferences.SessionStateStore
import com.nendo.argosy.data.repository.GameRepository
import com.nendo.argosy.util.DisplayAffinityHelper
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

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
     * The display a launch of [gameId] is sent to, or null when it is placed the way any launch
     * is. An Android app's own launch screen names one physical panel while it is attached, and
     * [overrideDisplayId] outranks it.
     */
    suspend fun launchDisplayIdFor(
        gameId: Long,
        drawsSecondScreen: Boolean = false,
        overrideDisplayId: Int? = null
    ): Int? {
        val appScreen = if (overrideDisplayId == null &&
            gameRepository.getById(gameId)?.source == GameSource.ANDROID_APP
        ) {
            appLaunchScreenSettings.storedChoice(gameId)?.displayId
        } else {
            null
        }
        return launchDisplayPlanner.displayFor(gameId, drawsSecondScreen, overrideDisplayId ?: appScreen)
    }

    suspend fun launchOptionsFor(gameId: Long, intent: Intent, overrideDisplayId: Int? = null): Bundle? {
        val rolesSwapped = sessionStateStore.isRolesSwapped()
        val displayId = launchDisplayIdFor(
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
