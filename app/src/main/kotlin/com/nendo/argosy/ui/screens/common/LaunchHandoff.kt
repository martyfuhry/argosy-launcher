package com.nendo.argosy.ui.screens.common

import android.content.Intent
import com.nendo.argosy.core.input.SoundType
import com.nendo.argosy.data.emulator.LaunchProgressTracker
import com.nendo.argosy.ui.input.SoundFeedbackManager
import com.nendo.argosy.ui.theme.generated.ComponentDefaults
import kotlinx.coroutines.delay
import javax.inject.Inject

/**
 * The last step of a successful launch. Publishes Launching, which starts the veil or the
 * single-screen fade, plays the launch sound, and holds for [holdMs] so the fade has landed
 * before the game's window is requested.
 */
class LaunchHandoff internal constructor(
    private val tracker: LaunchProgressTracker,
    private val soundManager: SoundFeedbackManager,
    private val holdMs: Long
) {

    @Inject constructor(
        tracker: LaunchProgressTracker,
        soundManager: SoundFeedbackManager
    ) : this(tracker, soundManager, ComponentDefaults.LaunchOverlay.veilMs.toLong())

    /**
     * Finishes [ticket] as launched, then hands [intent] to [onLaunch] after the hold. A null
     * ticket is a launch continued from a picker, where LaunchGameUseCase opened and finished its
     * own ticket and so has already published Launching; it still holds.
     */
    suspend fun handOff(
        ticket: LaunchProgressTracker.Ticket?,
        intent: Intent,
        onLaunch: (Intent) -> Unit
    ) {
        if (ticket != null) tracker.finish(ticket, launched = true)
        soundManager.play(SoundType.LAUNCH_GAME)
        delay(holdMs)
        onLaunch(intent)
    }
}
