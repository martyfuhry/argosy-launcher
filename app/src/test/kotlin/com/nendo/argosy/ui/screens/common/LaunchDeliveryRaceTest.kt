package com.nendo.argosy.ui.screens.common

import android.content.Context
import android.content.Intent
import com.nendo.argosy.DualScreenManager
import com.nendo.argosy.DualScreenManagerHolder
import com.nendo.argosy.data.emulator.ActiveSession
import com.nendo.argosy.data.emulator.GameLauncher
import com.nendo.argosy.data.emulator.LaunchResult
import com.nendo.argosy.data.emulator.PlaySessionTracker
import com.nendo.argosy.data.local.dao.GameDao
import com.nendo.argosy.data.preferences.SessionStateStore
import com.nendo.argosy.data.repository.EmulatorConfigRepository
import com.nendo.argosy.domain.usecase.game.LaunchGameUseCase
import com.nendo.argosy.ui.screens.home.HomeEvent
import com.nendo.argosy.ui.screens.library.LibraryEvent
import com.nendo.argosy.util.DisplayAffinityHelper
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Instant

/**
 * The race between a tapped game's start and the dashboard swap that disposes the launch screen's
 * event collector, driven through the launch screens' view models with the placement lookup held
 * for [OPTIONS_LOOKUP_MS] and the session start taking [SESSION_BROADCAST_MS].
 */
@OptIn(ExperimentalCoroutinesApi::class)
class LaunchDeliveryRaceTest {

    private enum class Host(val displayId: Int, val showcaseDisplayId: Int) {
        COMPANION_BOTTOM(displayId = 4, showcaseDisplayId = 0),
        MAIN_TOP(displayId = 0, showcaseDisplayId = 4)
    }

    private enum class LaunchScreen { LIBRARY, HOME }

    private data class Outcome(
        val sessionStarted: Boolean,
        val started: List<Intent>
    )

    private val testDispatcher = StandardTestDispatcher()

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
    fun `a placement lookup outlasting the dashboard swap still starts the game`() {
        val outcome = race(Host.COMPANION_BOTTOM, LaunchScreen.LIBRARY, OPTIONS_LOOKUP_MS, SESSION_BROADCAST_MS)

        assertStartedOnce(outcome, "library tap on the companion, lookup finishing after the swap")
    }

    @Test
    fun `a placement lookup finishing before the dashboard swap starts the game`() {
        val outcome = race(Host.COMPANION_BOTTOM, LaunchScreen.LIBRARY, 0L, OPTIONS_LOOKUP_MS)

        assertStartedOnce(outcome, "library tap on the companion, lookup finishing before the swap")
    }

    @Test
    fun `a tap on the main screen with the game on the companion still starts the game`() {
        val outcome = race(Host.MAIN_TOP, LaunchScreen.LIBRARY, OPTIONS_LOOKUP_MS, SESSION_BROADCAST_MS)

        assertStartedOnce(outcome, "library tap on the main screen, game on the companion")
    }

    @Test
    fun `a game landing on the tapping screen keeps the launcher and starts the game`() {
        val outcome = race(
            Host.MAIN_TOP,
            LaunchScreen.LIBRARY,
            OPTIONS_LOOKUP_MS,
            SESSION_BROADCAST_MS,
            gameDisplayId = Host.MAIN_TOP.displayId
        )

        assertStartedOnce(outcome, "game on the tapping screen, no dashboard swap")
    }

    @Test
    fun `every launch screen on either screen starts the game whichever contestant wins`() {
        val failures = mutableListOf<String>()
        for (host in Host.values()) {
            for (screen in LaunchScreen.values()) {
                for ((lookupMs, broadcastMs) in listOf(OPTIONS_LOOKUP_MS to SESSION_BROADCAST_MS, 0L to OPTIONS_LOOKUP_MS)) {
                    val outcome = race(host, screen, lookupMs, broadcastMs)
                    if (!outcome.sessionStarted || outcome.started.size != 1) {
                        failures += "$host/$screen lookup=${lookupMs}ms session=${broadcastMs}ms: " +
                            "session=${outcome.sessionStarted}, started=${outcome.started.size}"
                    }
                }
            }
        }

        assertTrue("launches lost:\n" + failures.joinToString("\n"), failures.isEmpty())
    }

    private fun assertStartedOnce(outcome: Outcome, label: String) {
        assertTrue("$label: session opened", outcome.sessionStarted)
        assertEquals("$label: emulator starts", 1, outcome.started.size)
    }

    private fun race(
        host: Host,
        launchScreen: LaunchScreen,
        optionsLookupMs: Long,
        sessionBroadcastMs: Long,
        gameDisplayId: Int = host.showcaseDisplayId
    ): Outcome {
        val started = mutableListOf<Intent>()
        val appContext = mockk<Context>(relaxed = true) {
            every { startActivity(any(), any()) } answers { started += firstArg<Intent>() }
        }
        val screenContext = mockk<Context>(relaxed = true) {
            every { startActivity(any(), any()) } answers { started += firstArg<Intent>() }
            every { startActivity(any()) } answers { started += firstArg<Intent>() }
        }
        val displayAffinityHelper = mockk<DisplayAffinityHelper>(relaxed = true) {
            every { getRoleDisplayIds(any()) } returns (host.displayId to host.showcaseDisplayId)
            every { getEmulatorDisplayId(any()) } returns gameDisplayId
            every { getDisplayTargetId(any(), any()) } returns gameDisplayId
            every { getActivityOptions(any(), any(), any()) } returns null
        }
        val raceScope = CoroutineScope(testDispatcher + SupervisorJob() + CoroutineExceptionHandler { _, _ -> })
        val dsm = launchTestDualScreenManager(
            scope = raceScope,
            displayAffinityHelper = displayAffinityHelper,
            sessionStateStore = mockk<SessionStateStore>(relaxed = true) {
                every { hasActiveSession() } returns false
            },
            gameDao = mockk<GameDao>(relaxed = true) {
                coEvery { getById(any()) } returns null
            }
        )
        DualScreenManagerHolder.instance = dsm

        var sessionPrepared = false
        var sessionStarted = false
        val tracker = mockk<PlaySessionTracker>(relaxed = true) {
            every { prepareSession(GAME_ID, APP_PACKAGE, any(), any(), any()) } answers { sessionPrepared = true }
            every { startPreparedSession(GAME_ID, any(), any()) } answers {
                if (!sessionPrepared) return@answers null
                sessionStarted = true
                raceScope.launch { broadcastSessionStart(dsm, firstArg(), sessionBroadcastMs) }
                ActiveSession(GAME_ID, Instant.EPOCH, APP_PACKAGE)
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
            coEvery { launch(GAME_ID, any(), any(), any(), any(), any(), any(), any()) } returns LaunchResult.Success(intent)
        }
        val resolver = EmulatorLaunchTargetResolver(
            context = mockk(relaxed = true),
            displayAffinityHelper = displayAffinityHelper,
            emulatorConfigRepository = mockk<EmulatorConfigRepository>(relaxed = true) {
                coEvery { getEffectiveDisplayTarget(any()) } coAnswers {
                    delay(optionsLookupMs)
                    null
                }
            }
        )
        val launchGameUseCase = LaunchGameUseCase(gameLauncher, tracker)
        val gameLaunchDispatcher = GameLaunchDispatcher(
            context = appContext,
            launchTargetResolver = resolver,
            playSessionTracker = tracker,
            permissionHelper = mockk(relaxed = true),
            notificationManager = mockk(relaxed = true),
            ioDispatcher = testDispatcher
        )
        val gameLaunchDelegate = launchTestGameLaunchDelegate { _, gameId, onLaunch ->
            raceScope.launch { (launchGameUseCase(gameId) as? LaunchResult.Success)?.let { onLaunch(it.intent) } }
        }

        val screen: Job
        val tap: () -> Unit
        when (launchScreen) {
            LaunchScreen.LIBRARY -> {
                val viewModel = launchTestLibraryViewModel(gameLaunchDispatcher, gameLaunchDelegate, resolver)
                screen = raceScope.launch {
                    viewModel.events.collect { event ->
                        if (event is LibraryEvent.LaunchIntent) screenContext.startActivity(event.intent, event.options)
                    }
                }
                tap = { viewModel.launchGame(GAME_ID) }
            }
            LaunchScreen.HOME -> {
                val viewModel = launchTestHomeViewModel(gameLaunchDispatcher, gameLaunchDelegate, resolver)
                screen = raceScope.launch {
                    viewModel.events.collect { event ->
                        if (event is HomeEvent.LaunchIntent) screenContext.startActivity(event.intent, event.options)
                    }
                }
                tap = { viewModel.launchGame(GAME_ID, null) }
            }
        }
        val hostSwap = raceScope.launch {
            dsm.swappedIsGameActive.first { it && dsm.primaryShowsDashboard(host.displayId) }
            screen.cancel()
        }
        testDispatcher.scheduler.advanceUntilIdle()

        tap()
        testDispatcher.scheduler.advanceUntilIdle()

        raceScope.coroutineContext[Job]?.cancel()
        testDispatcher.scheduler.advanceUntilIdle()
        return Outcome(sessionStarted = sessionStarted, started = started.toList())
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
