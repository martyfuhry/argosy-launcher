package com.nendo.argosy.ui.screens.common

import android.content.Context
import android.content.Intent
import android.os.PowerManager
import com.nendo.argosy.DualScreenManager
import com.nendo.argosy.DualScreenManagerHolder
import com.nendo.argosy.core.notification.NotificationManager
import com.nendo.argosy.core.notification.NotificationType
import com.nendo.argosy.data.emulator.ActiveSession
import com.nendo.argosy.data.emulator.PlaySessionTracker
import com.nendo.argosy.data.emulator.PresenceEvent
import com.nendo.argosy.data.emulator.PresenceEventKind
import com.nendo.argosy.util.PermissionHelper
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.mockk.verifyOrder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class GameLaunchDispatcherTest {

    private val testDispatcher = StandardTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    private val started = mutableListOf<Intent>()
    private var interactive = true
    private val powerManager = mockk<PowerManager> {
        every { isInteractive } answers { interactive }
    }
    private val context = mockk<Context>(relaxed = true) {
        every { packageName } returns OWN_PACKAGE
        every { getSystemService(Context.POWER_SERVICE) } returns powerManager
        every { startActivity(any(), any()) } answers { started += firstArg<Intent>() }
    }
    private val sessionFlow = MutableStateFlow<ActiveSession?>(null)
    private val tracker = mockk<PlaySessionTracker>(relaxed = true) {
        every { this@mockk.activeSession } returns sessionFlow
        every { startPreparedSession(GAME_ID, any(), any()) } answers {
            ActiveSession(GAME_ID, Instant.EPOCH, GAME_PACKAGE).also { sessionFlow.value = it }
        }
        every { cancelSession() } answers { sessionFlow.value = null }
    }
    private val permissionHelper = mockk<PermissionHelper>(relaxed = true) {
        every { canObservePresence(any()) } returns true
        every { presenceEvents(any(), any(), any()) } returns emptyList()
        every { isPackageOnScreen(any(), any(), any()) } returns false
    }
    private val notificationManager = mockk<NotificationManager>(relaxed = true)
    private val dsm = mockk<DualScreenManager>(relaxed = true)
    private val resolver = mockk<EmulatorLaunchTargetResolver> {
        coEvery { launchOptionsFor(any(), any()) } returns null
    }
    private val intent = mockk<Intent>(relaxed = true) {
        every { component } returns mockk(relaxed = true) {
            every { packageName } returns GAME_PACKAGE
        }
        every { getBooleanExtra(any(), any()) } returns false
    }

    private lateinit var dispatcher: GameLaunchDispatcher

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        DualScreenManagerHolder.instance = dsm
        dispatcher = GameLaunchDispatcher(
            context = context,
            launchTargetResolver = resolver,
            playSessionTracker = tracker,
            permissionHelper = permissionHelper,
            notificationManager = notificationManager,
            ioDispatcher = testDispatcher
        )
    }

    @After
    fun tearDown() {
        DualScreenManagerHolder.instance = null
        Dispatchers.resetMain()
    }

    @Test
    fun `the session opens only once the start has been handed to the system`() = testScope.runTest {
        dispatcher.dispatch(GAME_ID, intent)
        runCurrent()

        verifyOrder {
            context.startActivity(intent, null)
            tracker.startPreparedSession(GAME_ID, any(), any())
        }
    }

    @Test
    fun `every start is a new task carrying the placement it resolved`() = testScope.runTest {
        val placement = mockk<android.os.Bundle>()
        coEvery { resolver.launchOptionsFor(GAME_ID, any()) } returns placement

        dispatcher.dispatch(GAME_ID, intent)
        runCurrent()

        verifyOrder {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent, placement)
        }
    }

    @Test
    fun `a start nothing can deliver opens no session and reports the failure`() = testScope.runTest {
        every { context.startActivity(any(), any()) } throws SecurityException("blocked")

        dispatcher.dispatch(GAME_ID, intent)
        advanceUntilIdle()

        verify(exactly = 0) { tracker.startPreparedSession(any(), any(), any()) }
        verify { tracker.discardPreparedSession(GAME_ID) }
        verify { dsm.setEmulatorDisplay(null) }
        verify { notificationManager.show(any(), any(), NotificationType.ERROR, any(), any(), any(), any(), any(), any()) }
    }

    @Test
    fun `a shell-started emulator is not started a second time but still gets its session`() = testScope.runTest {
        every { intent.getBooleanExtra("argosy.already_launched", false) } returns true

        dispatcher.dispatch(GAME_ID, intent)
        runCurrent()

        assertTrue(started.isEmpty())
        verify { tracker.startPreparedSession(GAME_ID, any(), any()) }
    }

    @Test
    fun `a game that never comes to the front has its session ended after the watchdog`() = testScope.runTest {
        dispatcher.dispatch(GAME_ID, intent)
        runCurrent()

        advanceTimeBy(WATCHDOG_MS - 1)
        runCurrent()
        verify(exactly = 0) { tracker.cancelSession() }

        advanceTimeBy(2)
        runCurrent()
        verify(exactly = 1) { tracker.cancelSession() }
        assertEquals(null, sessionFlow.value)
    }

    @Test
    fun `a game that resumed after the start keeps its session`() = testScope.runTest {
        every { permissionHelper.presenceEvents(any(), any(), any()) } returns listOf(
            PresenceEvent(1L, PresenceEventKind.ACTIVITY_RESUMED, GAME_PACKAGE, "$GAME_PACKAGE.Main")
        )

        dispatcher.dispatch(GAME_ID, intent)
        advanceUntilIdle()

        verify(exactly = 0) { tracker.cancelSession() }
    }

    @Test
    fun `a trampoline handing off to another app counts as arrived`() = testScope.runTest {
        every { permissionHelper.presenceEvents(any(), any(), any()) } returns listOf(
            PresenceEvent(1L, PresenceEventKind.ACTIVITY_RESUMED, "com.other.game", "com.other.game.Play")
        )

        dispatcher.dispatch(GAME_ID, intent)
        advanceUntilIdle()

        verify(exactly = 0) { tracker.cancelSession() }
    }

    @Test
    fun `the launcher resuming itself is not the game arriving`() = testScope.runTest {
        every { permissionHelper.presenceEvents(any(), any(), any()) } returns listOf(
            PresenceEvent(1L, PresenceEventKind.ACTIVITY_RESUMED, OWN_PACKAGE, "$OWN_PACKAGE.MainActivity")
        )

        dispatcher.dispatch(GAME_ID, intent)
        advanceUntilIdle()

        verify(exactly = 1) { tracker.cancelSession() }
    }

    @Test
    fun `a game already on screen keeps its session`() = testScope.runTest {
        every { permissionHelper.isPackageOnScreen(any(), GAME_PACKAGE, any()) } returns true

        dispatcher.dispatch(GAME_ID, intent)
        advanceUntilIdle()

        verify(exactly = 0) { tracker.cancelSession() }
    }

    @Test
    fun `without usage access the watchdog cannot tell and leaves the session alone`() = testScope.runTest {
        every { permissionHelper.canObservePresence(any()) } returns false

        dispatcher.dispatch(GAME_ID, intent)
        advanceUntilIdle()

        verify(exactly = 0) { tracker.cancelSession() }
    }

    @Test
    fun `a screen turned off during the start leaves the session alone`() = testScope.runTest {
        every { permissionHelper.presenceEvents(any(), any(), any()) } returns listOf(
            PresenceEvent(1L, PresenceEventKind.SCREEN_NON_INTERACTIVE)
        )

        dispatcher.dispatch(GAME_ID, intent)
        advanceUntilIdle()

        verify(exactly = 0) { tracker.cancelSession() }
    }

    @Test
    fun `a keyguard shown during the start leaves the session alone`() = testScope.runTest {
        every { permissionHelper.presenceEvents(any(), any(), any()) } returns listOf(
            PresenceEvent(1L, PresenceEventKind.KEYGUARD_SHOWN)
        )

        dispatcher.dispatch(GAME_ID, intent)
        advanceUntilIdle()

        verify(exactly = 0) { tracker.cancelSession() }
    }

    @Test
    fun `a screen that is off when the watchdog looks leaves the session alone`() = testScope.runTest {
        interactive = false

        dispatcher.dispatch(GAME_ID, intent)
        advanceUntilIdle()

        verify(exactly = 0) { tracker.cancelSession() }
    }

    @Test
    fun `an app that just exited is not started again while the screen is off`() = testScope.runTest {
        every { permissionHelper.isPackageOnScreenOrRecent(any(), GAME_PACKAGE, any()) } returns true
        asAppLaunchIntent()
        interactive = false

        dispatcher.dispatch(GAME_ID, intent)
        advanceUntilIdle()

        assertEquals(1, started.size)
        verify(exactly = 0) { tracker.cancelSession() }
    }

    @Test
    fun `a session the player already ended is not ended again`() = testScope.runTest {
        dispatcher.dispatch(GAME_ID, intent)
        runCurrent()
        sessionFlow.value = null

        advanceUntilIdle()

        verify(exactly = 0) { tracker.cancelSession() }
    }

    @Test
    fun `a later session of the same game outlives an earlier start's watchdog`() = testScope.runTest {
        dispatcher.dispatch(GAME_ID, intent)
        runCurrent()
        sessionFlow.value = ActiveSession(GAME_ID, Instant.EPOCH.plusSeconds(1), GAME_PACKAGE)

        advanceUntilIdle()

        verify(exactly = 0) { tracker.cancelSession() }
    }

    @Test
    fun `a session updated after its start is still the one watched`() = testScope.runTest {
        dispatcher.dispatch(GAME_ID, intent)
        runCurrent()
        sessionFlow.value = sessionFlow.value?.copy(channelName = "Slot 2", isOnOlderSave = true)

        advanceUntilIdle()

        verify(exactly = 1) { tracker.cancelSession() }
    }

    @Test
    fun `a resume with no prepared session is started but not watched`() = testScope.runTest {
        every { tracker.startPreparedSession(GAME_ID, any(), any()) } returns null

        dispatcher.dispatch(GAME_ID, intent)
        advanceUntilIdle()

        assertEquals(listOf(intent), started)
        verify(exactly = 0) { tracker.cancelSession() }
    }

    @Test
    fun `a new launch opens a new-game session`() = testScope.runTest {
        dispatcher.dispatch(GAME_ID, intent)
        runCurrent()

        verify { tracker.startPreparedSession(GAME_ID, GAME_PACKAGE, isNewGame = true) }
    }

    @Test
    fun `an app already running is brought forward and its session attached`() = testScope.runTest {
        every { permissionHelper.isPackageOnScreenOrRecent(any(), GAME_PACKAGE, any()) } returns true
        every { permissionHelper.isPackageOnScreen(any(), GAME_PACKAGE, any()) } returns true

        dispatcher.dispatch(GAME_ID, intent)
        advanceUntilIdle()

        assertEquals(listOf(intent), started)
        verify { tracker.startPreparedSession(GAME_ID, GAME_PACKAGE, isNewGame = false) }
        verify(exactly = 0) { tracker.cancelSession() }
    }

    @Test
    fun `an app that just exited is started once more before its session is given up`() = testScope.runTest {
        every { permissionHelper.isPackageOnScreenOrRecent(any(), GAME_PACKAGE, any()) } returns true
        asAppLaunchIntent()

        dispatcher.dispatch(GAME_ID, intent)
        runCurrent()
        advanceTimeBy(WATCHDOG_MS + 1)
        runCurrent()
        verify(exactly = 0) { tracker.cancelSession() }

        advanceUntilIdle()
        assertEquals(2, started.size)
        verify(exactly = 1) { tracker.cancelSession() }
    }

    @Test
    fun `an app that comes up on the second start keeps its session`() = testScope.runTest {
        every { permissionHelper.isPackageOnScreenOrRecent(any(), GAME_PACKAGE, any()) } returns true
        asAppLaunchIntent()
        every { context.startActivity(any(), any()) } answers {
            started += firstArg<Intent>()
            if (started.size == 2) {
                every { permissionHelper.isPackageOnScreen(any(), GAME_PACKAGE, any()) } returns true
            }
        }

        dispatcher.dispatch(GAME_ID, intent)
        advanceUntilIdle()

        assertEquals(2, started.size)
        verify(exactly = 0) { tracker.cancelSession() }
    }

    @Test
    fun `a second start of an app never clears its task`() = testScope.runTest {
        every { permissionHelper.isPackageOnScreenOrRecent(any(), GAME_PACKAGE, any()) } returns true
        asAppLaunchIntent()

        dispatcher.dispatch(GAME_ID, intent)
        advanceUntilIdle()

        verifyOrder {
            context.startActivity(intent, null)
            intent.removeFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK)
            context.startActivity(intent, null)
        }
    }

    @Test
    fun `an emulator that was running is never started a second time`() = testScope.runTest {
        every { permissionHelper.isPackageOnScreenOrRecent(any(), GAME_PACKAGE, any()) } returns true

        dispatcher.dispatch(GAME_ID, intent)
        advanceUntilIdle()

        assertEquals(1, started.size)
        verify(exactly = 1) { tracker.cancelSession() }
    }

    private fun asAppLaunchIntent() {
        every { intent.action } returns Intent.ACTION_MAIN
        every { intent.hasCategory(Intent.CATEGORY_LAUNCHER) } returns true
    }

    private companion object {
        const val GAME_ID = 7L
        const val GAME_PACKAGE = "com.aure.banjorecomp"
        const val OWN_PACKAGE = "com.nendo.argosy"
        const val WATCHDOG_MS = 5_000L
    }
}
