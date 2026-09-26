package com.nendo.argosy.ui.screens.common

import android.content.Context
import android.content.Intent
import android.os.Bundle
import com.nendo.argosy.DualScreenManager
import com.nendo.argosy.DualScreenManagerHolder
import com.nendo.argosy.data.emulator.GameLauncher
import com.nendo.argosy.data.emulator.LaunchResult
import com.nendo.argosy.data.emulator.PlaySessionTracker
import com.nendo.argosy.data.local.dao.GameDao
import com.nendo.argosy.data.preferences.SessionStateStore
import com.nendo.argosy.data.repository.EmulatorConfigRepository
import com.nendo.argosy.domain.usecase.game.LaunchGameUseCase
import com.nendo.argosy.util.DisplayAffinityHelper
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * The race between a tapped game's start being delivered and the session start swapping the
 * tapping screen for the in-game dashboard.
 *
 * Contestant A is the session start: it marks the game active on the dual-screen manager after
 * [SESSION_BROADCAST_MS], which is what makes the tapping host render the dashboard in place of
 * the launcher and so disposes the screen's launch-event collector. Contestant B is the launch
 * placement lookup, held for [OPTIONS_LOOKUP_MS] before the start is handed on. The screen is
 * modelled as a subscriber coroutine cancelled when its host swaps to the dashboard, which is
 * where the start was delivered before the dispatcher took it over.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class LaunchDeliveryRaceTest {

    private enum class Host(val displayId: Int, val showcaseDisplayId: Int) {
        COMPANION_BOTTOM(displayId = 4, showcaseDisplayId = 0),
        MAIN_TOP(displayId = 0, showcaseDisplayId = 4)
    }

    private enum class EntryPoint(val choosesDisplay: Boolean) {
        LIBRARY_PLAY(choosesDisplay = false),
        HOME_PLAY(choosesDisplay = false),
        HOME_PLAY_ON_DISPLAY(choosesDisplay = true),
        GAME_DETAIL_PLAY(choosesDisplay = false),
        GAME_DETAIL_LAUNCH_ON_DISPLAY(choosesDisplay = true)
    }

    private data class Outcome(
        val sessionStarted: Boolean,
        val dispatched: List<Intent>
    )

    private val testDispatcher = StandardTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        DualScreenManagerHolder.instance = null
        Dispatchers.resetMain()
    }

    @Test
    fun `a placement lookup outlasting the dashboard swap still starts the game`() = testScope.runTest {
        val outcome = race(
            host = Host.COMPANION_BOTTOM,
            entryPoint = EntryPoint.LIBRARY_PLAY,
            optionsLookupMs = OPTIONS_LOOKUP_MS,
            sessionBroadcastMs = SESSION_BROADCAST_MS
        )

        assertDispatchedOnce(outcome, "library tap on the companion, lookup finishing after the swap")
    }

    @Test
    fun `a placement lookup finishing before the dashboard swap starts the game`() = testScope.runTest {
        val outcome = race(
            host = Host.COMPANION_BOTTOM,
            entryPoint = EntryPoint.LIBRARY_PLAY,
            optionsLookupMs = 0L,
            sessionBroadcastMs = OPTIONS_LOOKUP_MS
        )

        assertDispatchedOnce(outcome, "library tap on the companion, lookup finishing before the swap")
    }

    @Test
    fun `a tap on the main screen with the game on the companion still starts the game`() = testScope.runTest {
        val outcome = race(
            host = Host.MAIN_TOP,
            entryPoint = EntryPoint.LIBRARY_PLAY,
            optionsLookupMs = OPTIONS_LOOKUP_MS,
            sessionBroadcastMs = SESSION_BROADCAST_MS
        )

        assertDispatchedOnce(outcome, "library tap on the main screen, game on the companion")
    }

    @Test
    fun `a game landing on the tapping screen keeps the launcher and starts the game`() = testScope.runTest {
        val outcome = race(
            host = Host.MAIN_TOP,
            entryPoint = EntryPoint.LIBRARY_PLAY,
            optionsLookupMs = OPTIONS_LOOKUP_MS,
            sessionBroadcastMs = SESSION_BROADCAST_MS,
            gameDisplayId = Host.MAIN_TOP.displayId
        )

        assertDispatchedOnce(outcome, "game on the tapping screen, no dashboard swap")
    }

    @Test
    fun `an app that is already running is brought forward rather than dropped`() = testScope.runTest {
        val outcome = race(
            host = Host.COMPANION_BOTTOM,
            entryPoint = EntryPoint.HOME_PLAY,
            optionsLookupMs = OPTIONS_LOOKUP_MS,
            sessionBroadcastMs = SESSION_BROADCAST_MS
        )

        assertDispatchedOnce(outcome, "already-running app tapped on Home")
    }

    @Test
    fun `every entry point on either screen starts the game whichever contestant wins`() = testScope.runTest {
        val failures = mutableListOf<String>()
        for (host in Host.values()) {
            for (entryPoint in EntryPoint.values()) {
                for ((lookupMs, broadcastMs) in listOf(OPTIONS_LOOKUP_MS to SESSION_BROADCAST_MS, 0L to OPTIONS_LOOKUP_MS)) {
                    val outcome = race(host, entryPoint, lookupMs, broadcastMs)
                    val label = "$host/$entryPoint lookup=${lookupMs}ms session=${broadcastMs}ms"
                    if (outcome.dispatched.size != 1) {
                        failures += "$label: started=${outcome.sessionStarted}, dispatched=${outcome.dispatched.size}"
                    }
                }
            }
        }

        assertTrue("launches lost:\n" + failures.joinToString("\n"), failures.isEmpty())
    }

    private fun assertDispatchedOnce(outcome: Outcome, label: String) {
        assertTrue("$label: session marked started", outcome.sessionStarted)
        assertEquals(
            "$label: session started but the emulator start was dispatched ${outcome.dispatched.size} times",
            1,
            outcome.dispatched.size
        )
    }

    private suspend fun TestScope.race(
        host: Host,
        entryPoint: EntryPoint,
        optionsLookupMs: Long,
        sessionBroadcastMs: Long,
        gameDisplayId: Int = host.showcaseDisplayId
    ): Outcome {
        val dispatched = mutableListOf<Intent>()
        val hostContext = mockk<Context>(relaxed = true) {
            every { startActivity(any(), any()) } answers { dispatched += firstArg<Intent>() }
            every { startActivity(any()) } answers { dispatched += firstArg<Intent>() }
        }
        val displayAffinityHelper = mockk<DisplayAffinityHelper>(relaxed = true) {
            every { getRoleDisplayIds(any()) } returns (host.displayId to host.showcaseDisplayId)
            every { getEmulatorDisplayId(any()) } returns gameDisplayId
            every { getDisplayTargetId(any(), any()) } returns gameDisplayId
            every { gameDisplayId(any(), any(), any(), any()) } answers { thirdArg<Int?>() ?: gameDisplayId }
            every { getActivityOptions(any(), any(), any()) } returns null
        }
        val gameDao = mockk<GameDao>(relaxed = true) {
            coEvery { getById(any()) } returns null
        }
        val managerScope = CoroutineScope(
            testDispatcher + kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.CoroutineExceptionHandler { _, _ -> }
        )
        val dsm = launchTestDualScreenManager(
            scope = managerScope,
            displayAffinityHelper = displayAffinityHelper,
            sessionStateStore = mockk<SessionStateStore>(relaxed = true) {
                every { hasActiveSession() } returns false
            },
            gameDao = gameDao
        )
        DualScreenManagerHolder.instance = dsm

        var sessionPrepared = false
        var sessionStarted = false
        val tracker = mockk<PlaySessionTracker>(relaxed = true) {
            every { prepareSession(GAME_ID, APP_PACKAGE, any(), any(), any()) } answers { sessionPrepared = true }
            every { startPreparedSession(GAME_ID, any(), any()) } answers {
                if (!sessionPrepared) return@answers null
                sessionStarted = true
                launch { broadcastSessionStart(dsm, firstArg(), sessionBroadcastMs) }
                APP_PACKAGE
            }
            every { activeSession } returns MutableStateFlow(null)
        }
        val intent = mockk<Intent>(relaxed = true) {
            every { component } returns mockk(relaxed = true) {
                every { packageName } returns APP_PACKAGE
            }
            every { getBooleanExtra(any(), any()) } returns false
        }
        val gameLauncher = mockk<GameLauncher>(relaxed = true) {
            coEvery { launch(GAME_ID, any(), any(), any(), any(), any(), any(), any(), any()) } returns LaunchResult.Success(intent)
        }
        val emulatorConfigRepository = mockk<EmulatorConfigRepository>(relaxed = true) {
            coEvery { getEffectiveDisplayTarget(any()) } coAnswers {
                delay(optionsLookupMs)
                null
            }
        }
        val resolver = EmulatorLaunchTargetResolver(
            context = mockk(relaxed = true),
            displayAffinityHelper = displayAffinityHelper,
            launchDisplayPlanner = com.nendo.argosy.data.emulator.LaunchDisplayPlanner(
                context = mockk(relaxed = true),
                displayAffinityHelper = displayAffinityHelper,
                emulatorConfigRepository = emulatorConfigRepository
            ),
            gameRepository = mockk(relaxed = true) {
                coEvery { getById(any()) } returns null
            },
            appLaunchScreenSettings = mockk(relaxed = true)
        )
        val launchGameUseCase = LaunchGameUseCase(gameLauncher, tracker)
        val dispatcher = GameLaunchDispatcher(
            context = hostContext,
            launchTargetResolver = resolver,
            playSessionTracker = tracker,
            permissionHelper = mockk(relaxed = true),
            notificationManager = mockk(relaxed = true),
            ioDispatcher = testDispatcher
        )
        val overrideDisplayId = if (entryPoint.choosesDisplay) gameDisplayId else null

        val events = MutableSharedFlow<Pair<Intent, Bundle?>>()
        val screen = launch {
            events.collect { (launchIntent, options) -> hostContext.startActivity(launchIntent, options) }
        }
        val hostJob = launch {
            dsm.swappedIsGameActive.first { it && dsm.primaryShowsDashboard(host.displayId) }
            screen.cancel()
        }
        advanceUntilIdle()

        val result = launchGameUseCase(GAME_ID) as LaunchResult.Success
        dispatcher.dispatch(GAME_ID, result.intent, overrideDisplayId)
        advanceUntilIdle()

        hostJob.cancelAndJoin()
        screen.cancelAndJoin()
        managerScope.coroutineContext[kotlinx.coroutines.Job]?.cancelAndJoin()
        return Outcome(sessionStarted = sessionStarted, dispatched = dispatched.toList())
    }

    private suspend fun broadcastSessionStart(dsm: DualScreenManager, gameId: Long, afterMs: Long) {
        delay(afterMs)
        dsm.assignEmulatorDisplayForSessionStart()
        dsm.onSessionChanged(gameId)
    }

    private companion object {
        const val GAME_ID = 42L
        const val APP_PACKAGE = "com.aure.banjorecomp"
        const val OPTIONS_LOOKUP_MS = 250L
        const val SESSION_BROADCAST_MS = 20L
    }
}
