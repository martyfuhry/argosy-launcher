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
     * placement resolves opens nothing. A game that cannot be placed fails at once, with no sound
     * and no hold. A start that fails clears Launching at once. Both reach [GameStart.onFailure].
     * A null ticket is a launch continued from a picker, where LaunchGameUseCase opened and
     * finished its own ticket and so has already published Launching; it still holds. Launching
     * outlasts a stop of the launching screen until the window has been requested.
     */
    suspend fun handOff(
        ticket: LaunchProgressTracker.Ticket?,
        intent: Intent,
        start: GameStart
    ) {
        val request = startRouter.place(start.gameId, intent, start.overrideDisplayId)
        if (ticket?.isCancelled == true) return
        val held = ticket?.also(tracker::holdForWindow) ?: tracker.holdLiveForWindow()
        val placed = request.getOrElse { return fail(held, it, start) }
        if (ticket != null) tracker.finish(ticket, launched = true)
        soundManager.play(SoundType.LAUNCH_GAME)
        delay(holdMs)
        val failure = startRouter.startPlaced(placed).exceptionOrNull()
        if (failure == null) {
            held?.let(tracker::windowRequested)
            return
        }
        fail(held, failure, start)
    }

    private fun fail(held: LaunchProgressTracker.Ticket?, failure: Throwable, start: GameStart) {
        held?.let(tracker::openFailed)
        start.onFailure?.invoke(failure)
    }
}
