package com.nendo.argosy.ui.screens.common

import android.content.Intent
import com.nendo.argosy.core.input.SoundType
import com.nendo.argosy.data.emulator.LaunchProgressTracker
import com.nendo.argosy.domain.model.LaunchStep
import com.nendo.argosy.ui.input.SoundFeedbackManager
import com.nendo.argosy.ui.theme.generated.ComponentDefaults
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class LaunchHandoffTest {

    private val holdMs = ComponentDefaults.LaunchOverlay.veilMs.toLong()
    private val intent: Intent = mockk()
    private val soundManager: SoundFeedbackManager = mockk(relaxed = true)

    @Test
    fun `launching is published and the intent waits out the veil before it is handed on`() = runTest {
        val tracker = LaunchProgressTracker(backgroundScope)
        val ticket = tracker.begin(1L, "Game")!!
        val handedOn = mutableListOf<Intent>()

        launch { LaunchHandoff(tracker, soundManager, holdMs).handOff(ticket, intent) { handedOn += it } }
        runCurrent()

        assertEquals(LaunchStep.Launching, tracker.progress.value?.step)
        assertTrue(ticket.isFinished)
        assertTrue(handedOn.isEmpty())

        advanceTimeBy(holdMs - 1)
        runCurrent()
        assertTrue(handedOn.isEmpty())

        advanceTimeBy(1)
        runCurrent()
        assertEquals(1, handedOn.size)
        assertSame(intent, handedOn.single())
    }

    @Test
    fun `the ticket is finished exactly once as launched`() = runTest {
        val tracker: LaunchProgressTracker = mockk(relaxed = true)
        val ticket: LaunchProgressTracker.Ticket = mockk(relaxed = true)

        LaunchHandoff(tracker, soundManager, holdMs).handOff(ticket, intent) {}

        verify(exactly = 1) { tracker.finish(ticket, true) }
        verify(exactly = 0) { tracker.finish(ticket, false) }
    }

    @Test
    fun `the launch sound plays at the start of the hold`() = runTest {
        val tracker = LaunchProgressTracker(backgroundScope)
        val ticket = tracker.begin(1L, "Game")!!

        launch { LaunchHandoff(tracker, soundManager, holdMs).handOff(ticket, intent) {} }
        runCurrent()

        verify(exactly = 1) { soundManager.play(SoundType.LAUNCH_GAME) }
    }

    @Test
    fun `a picker launch whose ticket is already finished still holds for the veil`() = runTest {
        val tracker = LaunchProgressTracker(backgroundScope)
        val useCaseTicket = tracker.begin(1L, "Game")!!
        tracker.finish(useCaseTicket, launched = true)
        val published = tracker.progress.value
        val handedOn = mutableListOf<Intent>()

        launch { LaunchHandoff(tracker, soundManager, holdMs).handOff(null, intent) { handedOn += it } }
        runCurrent()

        assertSame(published, tracker.progress.value)
        assertTrue(handedOn.isEmpty())
        verify(exactly = 1) { soundManager.play(SoundType.LAUNCH_GAME) }

        advanceTimeBy(holdMs)
        runCurrent()
        assertSame(intent, handedOn.single())
    }
}
