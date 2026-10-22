package com.nendo.argosy.ui.screens.common

import android.content.Context
import android.content.Intent
import android.os.Bundle
import com.nendo.argosy.DualScreenManager
import com.nendo.argosy.DualScreenManagerHolder
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class GameStartRouterTest {

    private val appContext = mockk<Context>(relaxed = true)
    private val resolver = mockk<EmulatorLaunchTargetResolver>()
    private val options = mockk<Bundle>()
    private val intent = mockk<Intent>(relaxed = true)

    @After
    fun tearDown() {
        DualScreenManagerHolder.instance = null
    }

    @Test
    fun `the manager starts the resolved request with no screen listening`() = runTest {
        val manager = mockk<DualScreenManager> {
            every { startGame(any()) } returns Result.success(Unit)
        }
        DualScreenManagerHolder.instance = manager
        coEvery { resolver.launchOptionsFor(7L, 2) } returns options
        var failure: Throwable? = null

        GameStartRouter(appContext, resolver, this)
            .start(gameId = 7L, intent = intent, overrideDisplayId = 2, onFailure = { failure = it })
        advanceUntilIdle()

        coVerifyOrder {
            resolver.launchOptionsFor(7L, 2)
            manager.startGame(GameLaunchRequest(intent, options))
        }
        verify(exactly = 0) { appContext.startActivity(any(), any()) }
        assertNull(failure)
    }

    @Test
    fun `without a manager the application starts the game as a new task`() = runTest {
        coEvery { resolver.launchOptionsFor(7L, null) } returns options

        GameStartRouter(appContext, resolver, this).start(gameId = 7L, intent = intent)
        advanceUntilIdle()

        verify(exactly = 1) { intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
        verify(exactly = 1) { appContext.startActivity(intent, options) }
    }

    @Test
    fun `a refused start reaches the failure callback`() = runTest {
        val refusal = SecurityException("denied")
        DualScreenManagerHolder.instance = mockk<DualScreenManager> {
            every { startGame(any()) } returns Result.failure(refusal)
        }
        coEvery { resolver.launchOptionsFor(7L, null) } returns options
        var failure: Throwable? = null

        GameStartRouter(appContext, resolver, this)
            .start(gameId = 7L, intent = intent, onFailure = { failure = it })
        advanceUntilIdle()

        assertSame(refusal, failure)
    }

    @Test
    fun `placing resolves the display without starting the game`() = runTest {
        val manager = mockk<DualScreenManager>(relaxed = true)
        DualScreenManagerHolder.instance = manager
        coEvery { resolver.launchOptionsFor(7L, 2) } returns options
        val router = GameStartRouter(appContext, resolver, this)

        val placed = router.place(gameId = 7L, intent = intent, overrideDisplayId = 2)

        assertEquals(GameLaunchRequest(intent, options), placed.getOrNull())
        coVerify(exactly = 1) { resolver.launchOptionsFor(7L, 2) }
        verify(exactly = 0) { manager.startGame(any()) }
    }

    @Test
    fun `a placed request starts through the manager`() = runTest {
        val manager = mockk<DualScreenManager> {
            every { startGame(any()) } returns Result.success(Unit)
        }
        DualScreenManagerHolder.instance = manager
        val router = GameStartRouter(appContext, resolver, this)

        val result = router.startPlaced(GameLaunchRequest(intent, options))

        assertTrue(result.isSuccess)
        verify(exactly = 1) { manager.startGame(GameLaunchRequest(intent, options)) }
        coVerify(exactly = 0) { resolver.launchOptionsFor(any(), any()) }
    }

    @Test
    fun `an unresolvable placement reaches the failure callback without a start`() = runTest {
        val manager = mockk<DualScreenManager>(relaxed = true)
        DualScreenManagerHolder.instance = manager
        val broken = IllegalStateException("db closed")
        coEvery { resolver.launchOptionsFor(7L, null) } throws broken
        var failure: Throwable? = null

        GameStartRouter(appContext, resolver, this)
            .start(gameId = 7L, intent = intent, onFailure = { failure = it })
        advanceUntilIdle()

        assertSame(broken, failure)
        verify(exactly = 0) { manager.startGame(any()) }
    }

    @Test
    fun `the started game window opens without the system transition`() = runTest {
        val flagged = flagTrackingIntent(initialFlags = 0)
        coEvery { resolver.launchOptionsFor(7L, null) } returns options
        val started = slot<Intent>()
        every { appContext.startActivity(capture(started), options) } returns Unit

        GameStartRouter(appContext, resolver, this).start(gameId = 7L, intent = flagged.intent)
        advanceUntilIdle()

        assertSame(flagged.intent, started.captured)
        assertNotEquals(0, flagged.flags and Intent.FLAG_ACTIVITY_NO_ANIMATION)
    }

    @Test
    fun `the caller flags survive the transition flag`() = runTest {
        val callerFlags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        val flagged = flagTrackingIntent(initialFlags = callerFlags)
        val manager = mockk<DualScreenManager> {
            every { startGame(any()) } returns Result.success(Unit)
        }
        DualScreenManagerHolder.instance = manager

        GameStartRouter(appContext, resolver, this).startPlaced(GameLaunchRequest(flagged.intent, options))

        assertEquals(callerFlags or Intent.FLAG_ACTIVITY_NO_ANIMATION, flagged.flags)
        verify(exactly = 1) { manager.startGame(GameLaunchRequest(flagged.intent, options)) }
    }

    private class FlagTrackingIntent(val intent: Intent, initialFlags: Int) {
        var flags: Int = initialFlags
    }

    private fun flagTrackingIntent(initialFlags: Int): FlagTrackingIntent {
        val mock = mockk<Intent>(relaxed = true)
        val tracked = FlagTrackingIntent(mock, initialFlags)
        every { mock.flags } answers { tracked.flags }
        every { mock.addFlags(any()) } answers {
            tracked.flags = tracked.flags or firstArg<Int>()
            mock
        }
        every { mock.setFlags(any()) } answers {
            tracked.flags = firstArg()
            mock
        }
        return tracked
    }
}
