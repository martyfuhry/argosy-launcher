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
    private val gameDao = mockk<com.nendo.argosy.data.local.dao.GameDao>(relaxed = true) {
        io.mockk.coEvery { getById(any()) } returns com.nendo.argosy.data.local.entity.GameEntity(
            id = GAME_ID,
            platformId = 1L,
            title = "Banjo",
            sortTitle = "banjo",
            localPath = null,
            rommId = null,
            igdbId = null,
            source = com.nendo.argosy.data.model.GameSource.ANDROID_APP
        )
    }
    private val emulatorResolver = mockk<EmulatorResolver>(relaxed = true)
    private lateinit var tracker: PlaySessionTracker

    @Before
    fun setUp() {
        DualScreenManagerHolder.instance = dsm
        tracker = PlaySessionTracker(
            application = mockk<Application>(relaxed = true) {
                every { packageName } returns "com.nendo.argosy"
            },
            gameDao = gameDao,
            overlayWriter = mockk(relaxed = true),
            activeSaveRepository = mockk(relaxed = true),
            playSessionDao = mockk(relaxed = true),
            saveCacheDao = mockk(relaxed = true),
            pendingSyncQueueDao = mockk(relaxed = true),
            syncSaveOnSessionEndUseCase = lazyOf<com.nendo.argosy.domain.usecase.save.SyncSaveOnSessionEndUseCase>(),
            syncStatesOnSessionEndUseCase = lazyOf<com.nendo.argosy.domain.usecase.state.SyncStatesOnSessionEndUseCase>(),
            saveCacheManager = lazyOf<com.nendo.argosy.data.repository.SaveCacheManager>(),
            saveSyncRepository = lazyOf<com.nendo.argosy.data.repository.SaveSyncRepository>(),
            romMRepository = lazyOf<com.nendo.argosy.data.remote.romm.RomMRepository>(),
            preferencesRepository = mockk(relaxed = true) {
                every { userPreferences } returns kotlinx.coroutines.flow.flowOf(
                    com.nendo.argosy.data.preferences.UserPreferences()
                )
            },
            permissionHelper = mockk(relaxed = true),
            gameUpdateBus = mockk(relaxed = true),
            emulatorResolver = emulatorResolver,
            notificationManager = mockk(relaxed = true),
            fileAccessLayer = mockk(relaxed = true),
            socialRepository = lazyOf<com.nendo.argosy.data.social.SocialRepository>(),
            saveRecoveryGate = mockk(relaxed = true),
            reconcileAchievementsOnSessionEndUseCase = lazyOf<com.nendo.argosy.domain.usecase.achievement.ReconcileAchievementsOnSessionEndUseCase>(),
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

        assertEquals(PACKAGE, tracker.startPreparedSession(GAME_ID, PACKAGE, isNewGame = false)?.emulatorPackage)
        val session = tracker.activeSession.value!!
        assertEquals(GAME_ID, session.gameId)
        assertEquals(PACKAGE, session.emulatorPackage)
        assertEquals(9L, session.variantFileId)
        assertEquals(LaunchOrigin.EXTERNAL, session.origin)
        assertEquals(false, session.isNewGame)
        assertNull(tracker.startPreparedSession(GAME_ID, PACKAGE))
    }

    @Test
    fun `a start for another game drops the prepared session instead of opening it`() {
        prepare(GAME_ID)

        assertNull(tracker.startPreparedSession(OTHER_GAME_ID, PACKAGE))
        assertNull(tracker.activeSession.value)
        assertNull(tracker.startPreparedSession(GAME_ID, PACKAGE))
    }

    @Test
    fun `a start of another package drops a stale prepare instead of opening it`() {
        prepare(GAME_ID)

        assertNull(tracker.startPreparedSession(GAME_ID, OWN_PACKAGE))
        assertNull(tracker.activeSession.value)
        assertNull(tracker.startPreparedSession(GAME_ID, PACKAGE))
    }

    @Test
    fun `an in-process launch leaves no earlier prepare for its own start to open`() = kotlinx.coroutines.test.runTest {
        val useCase = launchUseCase(
            LaunchResult.Success(appIntent(PACKAGE)),
            LaunchResult.Success(appIntent(OWN_PACKAGE), inProcess = true)
        )
        useCase(GAME_ID)
        useCase(GAME_ID)

        assertNull(tracker.startPreparedSession(GAME_ID, PACKAGE))
        assertNull(tracker.activeSession.value)
    }

    @Test
    fun `a resume leaves no earlier prepare for its own start to open`() = kotlinx.coroutines.test.runTest {
        val useCase = launchUseCase(
            LaunchResult.Success(appIntent(PACKAGE)),
            LaunchResult.Success(appIntent(PACKAGE))
        )
        useCase(GAME_ID)
        useCase(GAME_ID, forResume = true)

        assertNull(tracker.startPreparedSession(GAME_ID, PACKAGE))
        assertNull(tracker.activeSession.value)
    }

    @Test
    fun `a start refused behind a running session hands back no session to watch`() {
        prepare(GAME_ID)
        tracker.startPreparedSession(GAME_ID, PACKAGE)
        prepare(GAME_ID)

        assertNull(tracker.startPreparedSession(GAME_ID, PACKAGE))
    }

    @Test
    fun `a discarded prepared session is never opened`() {
        prepare(GAME_ID)

        tracker.discardPreparedSession(GAME_ID)

        assertNull(tracker.startPreparedSession(GAME_ID, PACKAGE))
        assertNull(tracker.activeSession.value)
    }

    @Test
    fun `a later launch replaces the session an earlier one prepared`() {
        prepare(GAME_ID)
        prepare(OTHER_GAME_ID)

        assertEquals(PACKAGE, tracker.startPreparedSession(OTHER_GAME_ID, PACKAGE)?.emulatorPackage)
        assertEquals(OTHER_GAME_ID, tracker.activeSession.value?.gameId)
    }

    @Test
    fun `force stopping with no session clears the game display at once`() {
        tracker.forceStopService()

        verify { dsm.setEmulatorDisplay(null) }
    }

    @Test
    fun `force stopping leaves a session opened meanwhile its display`() {
        prepare(GAME_ID)
        tracker.startPreparedSession(GAME_ID, PACKAGE)

        tracker.forceStopService()

        verify(exactly = 0) { dsm.setEmulatorDisplay(null) }
    }

    @Test
    fun `cancelling a session clears the game display at once`() {
        prepare(GAME_ID)
        tracker.startPreparedSession(GAME_ID, PACKAGE)

        tracker.cancelSession()

        verify { dsm.setEmulatorDisplay(null) }
        assertNull(tracker.activeSession.value)
    }

    @Test
    fun `an app with no save location tells the dashboard its saves do not sync`() {
        every { emulatorResolver.resolveEmulatorId(PACKAGE) } returns null
        prepare(GAME_ID)

        tracker.startPreparedSession(GAME_ID, PACKAGE)

        verify(timeout = 5_000) { dsm.updateCompanionSaveSyncApplicable(false) }
    }

    @Test
    fun `an emulator with a watched save location tells the dashboard its saves sync`() {
        every { emulatorResolver.resolveEmulatorId(PACKAGE) } returns "retroarch"
        prepare(GAME_ID)

        tracker.startPreparedSession(GAME_ID, PACKAGE)

        verify(timeout = 5_000) { dsm.updateCompanionSaveSyncApplicable(true) }
    }

    private fun appIntent(packageName: String) = mockk<android.content.Intent>(relaxed = true) {
        every { component } returns mockk(relaxed = true) {
            every { this@mockk.packageName } returns packageName
        }
    }

    private fun launchUseCase(vararg results: LaunchResult): com.nendo.argosy.domain.usecase.game.LaunchGameUseCase {
        val launcher = mockk<GameLauncher>(relaxed = true) {
            io.mockk.coEvery { launch(any(), any(), any(), any(), any(), any(), any(), any()) } returnsMany results.toList()
        }
        return com.nendo.argosy.domain.usecase.game.LaunchGameUseCase(launcher, tracker)
    }

    private fun prepare(
        gameId: Long,
        variantFileId: Long? = null,
        origin: LaunchOrigin = LaunchOrigin.INTERNAL
    ) = tracker.prepareSession(gameId, PACKAGE, coreName = null, variantFileId = variantFileId, origin = origin)

    private inline fun <reified T : Any> lazyOf(): dagger.Lazy<T> {
        val value = mockk<T>(relaxed = true)
        return dagger.Lazy { value }
    }

    private companion object {
        const val GAME_ID = 3L
        const val OTHER_GAME_ID = 4L
        const val PACKAGE = "com.aure.banjorecomp"
        const val OWN_PACKAGE = "com.nendo.argosy"
    }
}
