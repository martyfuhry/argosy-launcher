package com.nendo.argosy.data.emulator

import android.app.Application
import com.nendo.argosy.DualScreenManager
import com.nendo.argosy.DualScreenManagerHolder
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class PlaySessionTrackerLaunchTest {

    private val dsm = mockk<DualScreenManager>(relaxed = true)
    private lateinit var tracker: PlaySessionTracker

    @Before
    fun setUp() {
        DualScreenManagerHolder.instance = dsm
        tracker = PlaySessionTracker(
            application = mockk<Application>(relaxed = true) {
                every { packageName } returns "com.nendo.argosy"
            },
            gameDao = mockk(relaxed = true),
            overlayWriter = mockk(relaxed = true),
            activeSaveRepository = mockk(relaxed = true),
            playSessionDao = mockk(relaxed = true),
            saveCacheDao = mockk(relaxed = true),
            pendingSyncQueueDao = mockk(relaxed = true),
            syncSaveOnSessionEndUseCase = mockk(relaxed = true),
            syncStatesOnSessionEndUseCase = mockk(relaxed = true),
            saveCacheManager = mockk(relaxed = true),
            saveSyncRepository = mockk(relaxed = true),
            romMRepository = mockk(relaxed = true),
            preferencesRepository = mockk(relaxed = true),
            permissionHelper = mockk(relaxed = true),
            gameUpdateBus = mockk(relaxed = true),
            emulatorResolver = mockk(relaxed = true),
            notificationManager = mockk(relaxed = true),
            fileAccessLayer = mockk(relaxed = true),
            socialRepository = mockk(relaxed = true),
            saveRecoveryGate = mockk(relaxed = true),
            reconcileAchievementsOnSessionEndUseCase = mockk(relaxed = true),
            savePathAuthority = mockk(relaxed = true),
            saveAccessNotices = mockk(relaxed = true)
        )
    }

    @After
    fun tearDown() {
        DualScreenManagerHolder.instance = null
    }

    @Test
    fun `a prepared session stays closed until its start is dispatched`() {
        prepare(GAME_ID)

        assertNull(tracker.activeSession.value)
    }

    @Test
    fun `starting a prepared session opens it once with what the launch prepared`() {
        prepare(GAME_ID, variantFileId = 9L, origin = LaunchOrigin.EXTERNAL)

        assertEquals(PACKAGE, tracker.startPreparedSession(GAME_ID, isNewGame = false))
        val session = tracker.activeSession.value!!
        assertEquals(GAME_ID, session.gameId)
        assertEquals(PACKAGE, session.emulatorPackage)
        assertEquals(9L, session.variantFileId)
        assertEquals(LaunchOrigin.EXTERNAL, session.origin)
        assertEquals(false, session.isNewGame)
        assertNull(tracker.startPreparedSession(GAME_ID))
    }

    @Test
    fun `a start for another game does not open the prepared session`() {
        prepare(GAME_ID)

        assertNull(tracker.startPreparedSession(OTHER_GAME_ID))
        assertNull(tracker.activeSession.value)
        assertEquals(PACKAGE, tracker.startPreparedSession(GAME_ID))
    }

    @Test
    fun `a discarded prepared session is never opened`() {
        prepare(GAME_ID)

        tracker.discardPreparedSession(GAME_ID)

        assertNull(tracker.startPreparedSession(GAME_ID))
        assertNull(tracker.activeSession.value)
    }

    @Test
    fun `a later launch replaces the session an earlier one prepared`() {
        prepare(GAME_ID)
        prepare(OTHER_GAME_ID)

        assertNull(tracker.startPreparedSession(GAME_ID))
        assertEquals(PACKAGE, tracker.startPreparedSession(OTHER_GAME_ID))
    }

    @Test
    fun `force stopping with no session clears the game display at once`() {
        tracker.forceStopService()

        verify { dsm.setEmulatorDisplay(null) }
    }

    @Test
    fun `force stopping leaves a session opened meanwhile its display`() {
        prepare(GAME_ID)
        tracker.startPreparedSession(GAME_ID)

        tracker.forceStopService()

        verify(exactly = 0) { dsm.setEmulatorDisplay(null) }
    }

    @Test
    fun `cancelling a session clears the game display at once`() {
        prepare(GAME_ID)
        tracker.startPreparedSession(GAME_ID)

        tracker.cancelSession()

        verify { dsm.setEmulatorDisplay(null) }
        assertNull(tracker.activeSession.value)
    }

    private fun prepare(
        gameId: Long,
        variantFileId: Long? = null,
        origin: LaunchOrigin = LaunchOrigin.INTERNAL
    ) = tracker.prepareSession(gameId, PACKAGE, coreName = null, variantFileId = variantFileId, origin = origin)

    private companion object {
        const val GAME_ID = 3L
        const val OTHER_GAME_ID = 4L
        const val PACKAGE = "com.aure.banjorecomp"
    }
}
