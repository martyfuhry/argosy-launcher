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
     * Places [start]'s game, which records its display, then finishes [ticket] as launched and
     * requests [intent]'s window with that placement after the hold. A ticket cancelled while the
     * placement resolves opens nothing. A start that fails clears Launching at once and reaches
     * [GameStart.onFailure]. A null ticket is a launch continued from a picker, where
     * LaunchGameUseCase opened and finished its own ticket and so has already published Launching;
     * it still holds. Launching outlasts a stop of the launching screen until the window has been
     * requested.
     */
    suspend fun handOff(
        ticket: LaunchProgressTracker.Ticket?,
        intent: Intent,
        start: GameStart
    ) {
        val request = startRouter.place(start.gameId, intent, start.overrideDisplayId)
        if (ticket?.isCancelled == true) return
        val held = if (ticket != null) {
            tracker.holdForWindow(ticket)
            tracker.finish(ticket, launched = true)
            ticket
        } else {
            tracker.holdLiveForWindow()
        }
        soundManager.play(SoundType.LAUNCH_GAME)
        delay(holdMs)
        val failure = request.fold(onSuccess = startRouter::startPlaced, onFailure = { Result.failure(it) })
            .exceptionOrNull()
        if (failure == null) {
            held?.let(tracker::windowRequested)
            return
        }
        held?.let(tracker::openFailed)
        start.onFailure?.invoke(failure)
    }
}
