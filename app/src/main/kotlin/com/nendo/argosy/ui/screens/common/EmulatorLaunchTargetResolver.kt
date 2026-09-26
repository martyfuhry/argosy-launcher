package com.nendo.argosy.ui.screens.common

import android.content.Context
import android.os.Bundle
import com.nendo.argosy.DualScreenManagerHolder
import com.nendo.argosy.data.preferences.EmulatorDisplayTarget
import com.nendo.argosy.data.preferences.SessionStateStore
import com.nendo.argosy.data.repository.EmulatorConfigRepository
import com.nendo.argosy.util.DisplayAffinityHelper
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class EmulatorLaunchTargetResolver @Inject constructor(
    @ApplicationContext context: Context,
    private val displayAffinityHelper: DisplayAffinityHelper,
    private val emulatorConfigRepository: EmulatorConfigRepository
) {
    private val sessionStateStore by lazy { SessionStateStore(context) }

    suspend fun launchOptionsFor(gameId: Long, overrideDisplayId: Int? = null): Bundle? {
        val rolesSwapped = sessionStateStore.isRolesSwapped()
        val target = EmulatorDisplayTarget.fromString(
            emulatorConfigRepository.getEffectiveDisplayTarget(gameId)
        )
        val displayId = overrideDisplayId
            ?: displayAffinityHelper.getDisplayTargetId(target, rolesSwapped)
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
