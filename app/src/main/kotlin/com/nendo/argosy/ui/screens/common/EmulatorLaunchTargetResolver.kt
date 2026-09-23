package com.nendo.argosy.ui.screens.common

import android.content.Context
import android.os.Bundle
import com.nendo.argosy.DualScreenManagerHolder
import com.nendo.argosy.data.model.GameSource
import com.nendo.argosy.data.preferences.EmulatorDisplayTarget
import com.nendo.argosy.data.preferences.SessionStateStore
import com.nendo.argosy.data.repository.EmulatorConfigRepository
import com.nendo.argosy.data.repository.GameRepository
import com.nendo.argosy.util.DisplayAffinityHelper
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class EmulatorLaunchTargetResolver @Inject constructor(
    @ApplicationContext context: Context,
    private val displayAffinityHelper: DisplayAffinityHelper,
    private val emulatorConfigRepository: EmulatorConfigRepository,
    private val gameRepository: GameRepository
) {
    private val sessionStateStore by lazy { SessionStateStore(context) }

    /**
     * The display a launch of [gameId] is sent to, or null when it is placed the way any launch
     * is. An Android app's own launch screen names the screen the layout gives that role, so a
     * role swap does not move it; every other stored target follows the roles.
     */
    suspend fun launchDisplayIdFor(gameId: Long, overrideDisplayId: Int? = null): Int? {
        overrideDisplayId?.let { return it }
        if (gameRepository.getById(gameId)?.source == GameSource.ANDROID_APP) {
            val appTarget = EmulatorDisplayTarget.fromString(
                emulatorConfigRepository.getDisplayTargetForGame(gameId)
            )
            if (appTarget != EmulatorDisplayTarget.DEFAULT) {
                return displayAffinityHelper.getLayoutDisplayTargetId(appTarget)
            }
        }
        val target = EmulatorDisplayTarget.fromString(
            emulatorConfigRepository.getEffectiveDisplayTarget(gameId)
        )
        return displayAffinityHelper.getDisplayTargetId(target, sessionStateStore.isRolesSwapped())
    }

    suspend fun launchOptionsFor(gameId: Long, overrideDisplayId: Int? = null): Bundle? {
        val rolesSwapped = sessionStateStore.isRolesSwapped()
        val displayId = launchDisplayIdFor(gameId, overrideDisplayId)
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
