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
    private val startRouter: GameStartRouter,
    private val holdMs: Long
) {

    @Inject constructor(
        tracker: LaunchProgressTracker,
        soundManager: SoundFeedbackManager,
        startRouter: GameStartRouter
    ) : this(tracker, soundManager, startRouter, ComponentDefaults.LaunchOverlay.veilMs.toLong())

    /**
     * Finishes [ticket] as launched, then requests the game window [onLaunch] asks of
     * [GameStartRouter.start] after the hold. A start that fails clears Launching at once and
     * reaches the failure callback its caller gave. A null ticket is a launch continued from a
     * picker, where LaunchGameUseCase opened and finished its own ticket and so has already
     * published Launching; it still holds.
     */
    suspend fun handOff(
        ticket: LaunchProgressTracker.Ticket?,
        intent: Intent,
        onLaunch: (Intent) -> Unit
    ) {
        val starts = startRouter.held { onLaunch(intent) }
        if (ticket != null) tracker.finish(ticket, launched = true)
        soundManager.play(SoundType.LAUNCH_GAME)
        delay(holdMs)
        starts.forEach { start ->
            startRouter.place(start.gameId, start.intent, start.overrideDisplayId)
                .fold(onSuccess = startRouter::startPlaced, onFailure = { Result.failure(it) })
                .exceptionOrNull()
                ?.let { failure ->
                    tracker.openFailed()
                    start.onFailure?.invoke(failure)
                }
        }
    }
}
