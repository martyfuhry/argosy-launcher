package com.nendo.argosy.data.emulator

import android.content.Context
import android.content.Intent
import com.nendo.argosy.data.preferences.EmulatorDisplayTarget
import com.nendo.argosy.data.preferences.SessionStateStore
import com.nendo.argosy.data.repository.EmulatorConfigRepository
import com.nendo.argosy.libretro.DualScreenOutput
import com.nendo.argosy.libretro.LibretroActivity
import com.nendo.argosy.util.DisplayAffinityHelper
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LaunchDisplayPlanner @Inject constructor(
    @ApplicationContext context: Context,
    private val displayAffinityHelper: DisplayAffinityHelper,
    private val emulatorConfigRepository: EmulatorConfigRepository
) {
    private val sessionStateStore by lazy { SessionStateStore(context) }

    /**
     * The display a launch of [gameId] goes to, or null to leave it on the launching screen.
     * [overrideDisplayId] is a screen chosen for this launch alone, ahead of any stored target.
     */
    suspend fun displayFor(
        gameId: Long,
        drawsSecondScreen: Boolean,
        overrideDisplayId: Int? = null
    ): Int? {
        val rolesSwapped = sessionStateStore.isRolesSwapped()
        val explicit = overrideDisplayId ?: displayAffinityHelper.getDisplayTargetId(
            EmulatorDisplayTarget.fromString(emulatorConfigRepository.getEffectiveDisplayTarget(gameId)),
            rolesSwapped
        )
        return displayAffinityHelper.gameDisplayId(drawsSecondScreen, explicit, rolesSwapped)
    }

    companion object {
        /**
         * Whether what [intent] launches shows a console's second screen on a display of its
         * own: the built-in emulator with a core that splits its frame, or such an emulator app.
         */
        fun drawsSecondScreen(intent: Intent): Boolean = drawsSecondScreen(
            className = intent.component?.className,
            packageName = intent.component?.packageName ?: intent.`package`,
            coreId = intent.getStringExtra(LibretroActivity.EXTRA_CORE_NAME)
        )

        internal fun drawsSecondScreen(className: String?, packageName: String?, coreId: String?): Boolean {
            if (className == LibretroActivity::class.java.name) return DualScreenOutput.forCore(coreId) != null
            return packageName != null && EmulatorRegistry.drawsSecondScreen(packageName)
        }
    }
}
