package com.nendo.argosy.data.emulator

import android.content.Context
import android.content.Intent
import com.nendo.argosy.data.preferences.EmulatorDisplayTarget
import com.nendo.argosy.data.preferences.SessionStateStore
import com.nendo.argosy.data.repository.EmulatorConfigRepository
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
        return displayAffinityHelper.gameDisplayId(
            drawsSecondScreen = drawsSecondScreen,
            target = EmulatorDisplayTarget.fromString(emulatorConfigRepository.getEffectiveDisplayTarget(gameId)),
            overrideDisplayId = overrideDisplayId,
            rolesSwapped = sessionStateStore.isRolesSwapped()
        )
    }

    companion object {
        /**
         * Whether the emulator app [intent] launches shows a console's second screen on a display
         * of its own. The built-in emulator is not one: it follows the screen roles, and the
         * screen swap moves it while it runs.
         */
        fun drawsSecondScreen(intent: Intent): Boolean = drawsSecondScreen(
            className = intent.component?.className,
            packageName = intent.component?.packageName ?: intent.`package`
        )

        internal fun drawsSecondScreen(className: String?, packageName: String?): Boolean {
            if (className == LibretroActivity::class.java.name) return false
            return packageName != null && EmulatorRegistry.drawsSecondScreen(packageName)
        }
    }
}
