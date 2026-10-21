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
import io.mockk.verify
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
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
    fun `a start asked for while held is handed back unplaced and unstarted`() = runTest {
        val manager = mockk<DualScreenManager>(relaxed = true)
        DualScreenManagerHolder.instance = manager
        val onFailure: (Throwable) -> Unit = {}
        val router = GameStartRouter(appContext, resolver, this)

        val held = router.held { router.start(gameId = 7L, intent = intent, overrideDisplayId = 2, onFailure = onFailure) }
        advanceUntilIdle()

        val start = held.single()
        assertEquals(7L, start.gameId)
        assertSame(intent, start.intent)
        assertEquals(2, start.overrideDisplayId)
        assertSame(onFailure, start.onFailure)
        coVerify(exactly = 0) { resolver.launchOptionsFor(any(), any()) }
        verify(exactly = 0) { manager.startGame(any()) }
    }

    @Test
    fun `a start asked for after the hold ends starts as usual`() = runTest {
        val manager = mockk<DualScreenManager> {
            every { startGame(any()) } returns Result.success(Unit)
        }
        DualScreenManagerHolder.instance = manager
        coEvery { resolver.launchOptionsFor(7L, null) } returns options
        val router = GameStartRouter(appContext, resolver, this)

        router.held { }
        router.start(gameId = 7L, intent = intent)
        advanceUntilIdle()

        verify(exactly = 1) { manager.startGame(GameLaunchRequest(intent, options)) }
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
}
