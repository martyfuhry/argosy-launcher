package com.nendo.argosy.ui.screens.common

import android.content.Intent
import com.nendo.argosy.core.input.SoundType
import com.nendo.argosy.data.emulator.ExternalSessionStart
import com.nendo.argosy.data.emulator.LaunchProgressTracker
import com.nendo.argosy.data.emulator.PlaySessionTracker
import com.nendo.argosy.data.emulator.startExternal
import com.nendo.argosy.ui.input.SoundFeedbackManager
import com.nendo.argosy.ui.theme.generated.ComponentDefaults
import kotlinx.coroutines.CancellationException
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
    private val playSessionTracker: PlaySessionTracker,
    private val holdMs: Long
) {

    @Inject constructor(
        tracker: LaunchProgressTracker,
        soundManager: SoundFeedbackManager,
        startRouter: GameStartRouter,
        playSessionTracker: PlaySessionTracker
    ) : this(
        tracker,
        soundManager,
        startRouter,
        playSessionTracker,
        ComponentDefaults.LaunchOverlay.veilMs.toLong()
    )

    /**
     * Places [start]'s game, which records its display, then finishes [ticket] as launched and
     * requests [intent]'s window with that placement after the hold. [session] starts only once
     * that window has been requested; a launch that opens nothing leaves no session. A ticket
     * cancelled while the placement resolves opens nothing. A game that cannot be placed fails at
     * once, with no sound and no hold. A start that fails clears Launching at once. Both reach
     * [GameStart.onFailure]. A hold cut short, as when its screen is popped, clears Launching at
     * once. Launching outlasts a stop of the launching screen until the window has been requested.
     */
    suspend fun handOff(
        ticket: LaunchProgressTracker.Ticket,
        intent: Intent,
        start: GameStart,
        session: ExternalSessionStart?
    ) {
        val request = startRouter.place(start.gameId, intent, start.overrideDisplayId)
        if (ticket.isCancelled) return
        tracker.holdForWindow(ticket)
        val placed = request.getOrElse { return fail(ticket, it, start) }
        tracker.finish(ticket, launched = true)
        soundManager.play(SoundType.LAUNCH_GAME)
        try {
            delay(holdMs)
        } catch (cancelled: CancellationException) {
            tracker.openFailed(ticket)
            throw cancelled
        }
        val failure = startRouter.startPlaced(placed).exceptionOrNull()
        if (failure == null) {
            session?.let(playSessionTracker::startExternal)
            tracker.windowRequested(ticket)
            return
        }
        fail(ticket, failure, start)
    }

    private fun fail(ticket: LaunchProgressTracker.Ticket, failure: Throwable, start: GameStart) {
        tracker.openFailed(ticket)
        start.onFailure?.invoke(failure)
    }
}
