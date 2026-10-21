package com.nendo.argosy.ui.screens.common

import android.content.Context
import android.content.Intent
import android.os.Bundle
import com.nendo.argosy.DualScreenManager
import com.nendo.argosy.DualScreenManagerHolder
import com.nendo.argosy.core.input.SoundType
import com.nendo.argosy.data.emulator.LaunchProgressTracker
import com.nendo.argosy.domain.model.LaunchStep
import com.nendo.argosy.ui.input.SoundFeedbackManager
import com.nendo.argosy.ui.theme.generated.ComponentDefaults
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class LaunchHandoffTest {

    private val holdMs = ComponentDefaults.LaunchOverlay.veilMs.toLong()
    private val gameId = 7L
    private val intent: Intent = mockk(relaxed = true)
    private val options: Bundle = mockk()
    private val soundManager: SoundFeedbackManager = mockk(relaxed = true)
    private val resolver: EmulatorLaunchTargetResolver = mockk()
    private val manager: DualScreenManager = mockk()

    @Before
    fun setUp() {
        coEvery { resolver.launchOptionsFor(gameId, any()) } returns options
        every { manager.startGame(any()) } returns Result.success(Unit)
        DualScreenManagerHolder.instance = manager
    }

    @After
    fun tearDown() {
        DualScreenManagerHolder.instance = null
    }

    private fun router(scope: CoroutineScope) = GameStartRouter(mockk<Context>(relaxed = true), resolver, scope)

    private fun handoff(tracker: LaunchProgressTracker, router: GameStartRouter) =
        LaunchHandoff(tracker, soundManager, router, holdMs)

    @Test
    fun `launching is published and the window waits out the veil before it is requested`() = runTest {
        val tracker = LaunchProgressTracker(backgroundScope)
        val router = router(backgroundScope)
        val ticket = tracker.begin(gameId, "Game")!!

        launch { handoff(tracker, router).handOff(ticket, intent) { router.start(gameId, it) } }
        runCurrent()

        assertEquals(LaunchStep.Launching, tracker.progress.value?.step)
        assertTrue(ticket.isFinished)
        verify(exactly = 0) { manager.startGame(any()) }

        advanceTimeBy(holdMs - 1)
        runCurrent()
        verify(exactly = 0) { manager.startGame(any()) }

        advanceTimeBy(1)
        runCurrent()
        verify(exactly = 1) { manager.startGame(GameLaunchRequest(intent, options)) }
    }

    @Test
    fun `the ticket is finished exactly once as launched`() = runTest {
        val tracker: LaunchProgressTracker = mockk(relaxed = true)
        val ticket: LaunchProgressTracker.Ticket = mockk(relaxed = true)
        val router = router(backgroundScope)

        handoff(tracker, router).handOff(ticket, intent) { router.start(gameId, it) }

        verify(exactly = 1) { tracker.finish(ticket, true) }
        verify(exactly = 0) { tracker.finish(ticket, false) }
    }

    @Test
    fun `the launch sound plays at the start of the hold`() = runTest {
        val tracker = LaunchProgressTracker(backgroundScope)
        val router = router(backgroundScope)
        val ticket = tracker.begin(gameId, "Game")!!

        launch { handoff(tracker, router).handOff(ticket, intent) { router.start(gameId, it) } }
        runCurrent()

        verify(exactly = 1) { soundManager.play(SoundType.LAUNCH_GAME) }
    }

    @Test
    fun `a picker launch whose ticket is already finished still holds for the veil`() = runTest {
        val tracker = LaunchProgressTracker(backgroundScope)
        val router = router(backgroundScope)
        val useCaseTicket = tracker.begin(gameId, "Game")!!
        tracker.finish(useCaseTicket, launched = true)
        val published = tracker.progress.value

        launch { handoff(tracker, router).handOff(null, intent) { router.start(gameId, it) } }
        runCurrent()

        assertSame(published, tracker.progress.value)
        verify(exactly = 0) { manager.startGame(any()) }
        verify(exactly = 1) { soundManager.play(SoundType.LAUNCH_GAME) }

        advanceTimeBy(holdMs)
        runCurrent()
        verify(exactly = 1) { manager.startGame(GameLaunchRequest(intent, options)) }
    }

    @Test
    fun `the game is placed before launching is published and the start uses that placement`() = runTest {
        val tracker = LaunchProgressTracker(backgroundScope)
        val router = router(backgroundScope)
        val ticket = tracker.begin(gameId, "Game")!!
        var stepWhenPlaced: LaunchStep? = LaunchStep.Launching
        coEvery { resolver.launchOptionsFor(gameId, 2) } answers {
            stepWhenPlaced = tracker.progress.value?.step
            options
        }

        launch { handoff(tracker, router).handOff(ticket, intent) { router.start(gameId, it, overrideDisplayId = 2) } }
        runCurrent()

        assertNull(stepWhenPlaced)
        coVerify(exactly = 1) { resolver.launchOptionsFor(gameId, 2) }
        assertEquals(LaunchStep.Launching, tracker.progress.value?.step)
        verify(exactly = 0) { manager.startGame(any()) }

        advanceTimeBy(holdMs)
        runCurrent()

        coVerify(exactly = 1) { resolver.launchOptionsFor(any(), any()) }
        verify(exactly = 1) { manager.startGame(GameLaunchRequest(intent, options)) }
    }

    @Test
    fun `a launch cancelled while its placement resolves opens nothing`() = runTest {
        val tracker = LaunchProgressTracker(backgroundScope)
        val router = router(backgroundScope)
        val ticket = tracker.begin(gameId, "Game")!!
        coEvery { resolver.launchOptionsFor(gameId, any()) } answers {
            tracker.cancel()
            options
        }

        launch { handoff(tracker, router).handOff(ticket, intent) { router.start(gameId, it) } }
        advanceTimeBy(holdMs)
        runCurrent()

        assertNull(tracker.progress.value)
        verify(exactly = 0) { soundManager.play(SoundType.LAUNCH_GAME) }
        verify(exactly = 0) { manager.startGame(any()) }
    }

    @Test
    fun `a refused start clears launching at once and reaches the caller's failure callback`() = runTest {
        val refusal = SecurityException("denied")
        every { manager.startGame(any()) } returns Result.failure(refusal)
        val tracker = LaunchProgressTracker(backgroundScope)
        val router = router(backgroundScope)
        val ticket = tracker.begin(gameId, "Game")!!
        var failure: Throwable? = null

        launch {
            handoff(tracker, router).handOff(ticket, intent) {
                router.start(gameId, it, onFailure = { e -> failure = e })
            }
        }
        advanceTimeBy(holdMs)
        runCurrent()

        assertSame(refusal, failure)
        assertNull(tracker.progress.value)
        assertNull(tracker.current)
        assertNotNull(tracker.begin(gameId, "Game"))
    }

    @Test
    fun `a picker launch that fails to start clears the launching the use case published`() = runTest {
        every { manager.startGame(any()) } returns Result.failure(IllegalStateException("gone"))
        val tracker = LaunchProgressTracker(backgroundScope)
        val router = router(backgroundScope)
        val useCaseTicket = tracker.begin(gameId, "Game")!!
        tracker.finish(useCaseTicket, launched = true)

        launch { handoff(tracker, router).handOff(null, intent) { router.start(gameId, it) } }
        advanceTimeBy(holdMs)
        runCurrent()

        assertNull(tracker.progress.value)
    }

    @Test
    fun `an unplaceable game clears launching and reaches the failure callback without a start`() = runTest {
        val broken = IllegalStateException("db closed")
        coEvery { resolver.launchOptionsFor(gameId, any()) } throws broken
        val tracker = LaunchProgressTracker(backgroundScope)
        val router = router(backgroundScope)
        val ticket = tracker.begin(gameId, "Game")!!
        var failure: Throwable? = null

        launch {
            handoff(tracker, router).handOff(ticket, intent) {
                router.start(gameId, it, onFailure = { e -> failure = e })
            }
        }
        advanceTimeBy(holdMs)
        runCurrent()

        assertSame(broken, failure)
        assertNull(tracker.progress.value)
        verify(exactly = 0) { manager.startGame(any()) }
    }
}
