package com.nendo.argosy

import android.content.Context
import com.nendo.argosy.data.emulator.LaunchOrigin
import com.nendo.argosy.data.preferences.DisplayRoleOverride
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

private const val LOWER_DISPLAY = 4

@OptIn(ExperimentalCoroutinesApi::class)
class DualScreenManagerRoleSwapTest {

    private val testDispatcher = StandardTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    private lateinit var sessionStateStore: com.nendo.argosy.data.preferences.SessionStateStore
    private lateinit var preferencesRepository:
        com.nendo.argosy.data.preferences.UserPreferencesRepository
    private lateinit var manager: DualScreenManager
    private val context = mockk<Context>(relaxed = true)

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        sessionStateStore = mockk(relaxed = true)
        preferencesRepository = mockk(relaxed = true)
        every { sessionStateStore.hasActiveSession() } returns false
        manager = newManager()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `a swap flips the live arrangement even when the stored override disagrees`() {
        manager = newManager(initialRolesSwapped = false)
        every { sessionStateStore.getDisplayRoleOverride() } returns "SWAPPED"

        manager.swapRoles()

        assertTrue(
            "swapRoles must derive the next arrangement from the live value, not the override",
            manager.isRolesSwapped.value
        )
    }

    @Test
    fun `a swap records the override that matches the arrangement it produced`() {
        manager = newManager(initialRolesSwapped = false)
        every { sessionStateStore.getDisplayRoleOverride() } returns "AUTO"

        manager.swapRoles()

        val stored = slot<String>()
        verify { sessionStateStore.setDisplayRoleOverride(capture(stored)) }
        assertEquals(DisplayRoleOverride.SWAPPED.name, stored.captured)
        assertTrue(manager.isRolesSwapped.value)
    }

    @Test
    fun `a swap is refused while a session is running`() {
        manager = newManager(initialRolesSwapped = false)
        every { sessionStateStore.hasActiveSession() } returns true

        manager.swapRoles()

        assertEquals(false, manager.isRolesSwapped.value)
    }

    @Test
    fun `clearing the override restores auto without touching the arrangement`() {
        manager = newManager(initialRolesSwapped = true)
        every { sessionStateStore.getDisplayRoleOverride() } returns "SWAPPED"

        manager.clearDisplayRoleOverride()

        verify { sessionStateStore.setDisplayRoleOverride(DisplayRoleOverride.AUTO.name) }
        assertTrue(manager.isRolesSwapped.value)
    }

    @Test
    fun `a stored layout moving the primary role leaves the override at auto`() {
        manager.setRolesSwapped(false)
        every { sessionStateStore.getDisplayRoleOverride() } returns "AUTO"

        manager.setPrimaryDisplayId(android.view.Display.DEFAULT_DISPLAY)
        testScope.testScheduler.advanceUntilIdle()

        assertTrue(manager.isRolesSwapped.value)
        verify(exactly = 0) { sessionStateStore.setDisplayRoleOverride(any()) }
        io.mockk.coVerify(exactly = 0) { preferencesRepository.setDisplayRoleOverride(any()) }
    }

    @Test
    fun `a stored layout returning the primary role to the second screen leaves the override at auto`() {
        manager.setRolesSwapped(true)
        every { sessionStateStore.getDisplayRoleOverride() } returns "AUTO"

        manager.setPrimaryDisplayId(2)
        testScope.testScheduler.advanceUntilIdle()

        assertEquals(false, manager.isRolesSwapped.value)
        verify(exactly = 0) { sessionStateStore.setDisplayRoleOverride(any()) }
        io.mockk.coVerify(exactly = 0) { preferencesRepository.setDisplayRoleOverride(any()) }
    }

    @Test
    fun `a layout applied during a session moves the launcher once the session ends`() {
        manager = newManager(initialRolesSwapped = false)
        every { sessionStateStore.hasActiveSession() } returns true

        manager.setPrimaryDisplayId(android.view.Display.DEFAULT_DISPLAY)
        assertEquals(false, manager.isRolesSwapped.value)

        every { sessionStateStore.hasActiveSession() } returns false
        manager.onSessionChanged(-1L)
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue(manager.isRolesSwapped.value)
    }

    @Test
    fun `a session ending with no layout waiting leaves the arrangement alone`() {
        manager = newManager(initialRolesSwapped = true)

        manager.onSessionChanged(-1L)
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue(manager.isRolesSwapped.value)
    }

    @Test
    fun `a layout matching the live arrangement cancels a waiting move`() {
        manager = newManager(initialRolesSwapped = false)
        every { sessionStateStore.hasActiveSession() } returns true
        manager.setPrimaryDisplayId(android.view.Display.DEFAULT_DISPLAY)
        manager.setPrimaryDisplayId(LOWER_DISPLAY)

        every { sessionStateStore.hasActiveSession() } returns false
        manager.onSessionChanged(-1L)
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(false, manager.isRolesSwapped.value)
    }

    private fun endSessionWithLayoutWaiting(origin: LaunchOrigin) {
        manager = newManager(initialRolesSwapped = false)
        every { sessionStateStore.hasActiveSession() } returns true
        manager.onSessionChanged(1L, origin = origin)
        manager.setPrimaryDisplayId(android.view.Display.DEFAULT_DISPLAY)

        every { sessionStateStore.hasActiveSession() } returns false
        manager.onSessionChanged(-1L)
        testDispatcher.scheduler.advanceUntilIdle()
    }

    @Test
    fun `a layout waiting on an externally launched session moves the launcher without taking focus`() {
        endSessionWithLayoutWaiting(LaunchOrigin.EXTERNAL)

        assertTrue(manager.isRolesSwapped.value)
        verify(exactly = 0) { context.startActivity(any(), any()) }
    }

    @Test
    fun `a layout waiting on a session the launcher started brings the launcher forward`() {
        endSessionWithLayoutWaiting(LaunchOrigin.INTERNAL)

        assertTrue(manager.isRolesSwapped.value)
        verify(exactly = 1) { context.startActivity(any(), any()) }
    }

    @Test
    fun `a layout waiting when a dock blanks the panels is dropped at session end`() {
        manager = newManager(initialRolesSwapped = false)
        every { sessionStateStore.hasActiveSession() } returns true
        manager.setPrimaryDisplayId(android.view.Display.DEFAULT_DISPLAY)

        docked = true
        every { sessionStateStore.hasActiveSession() } returns false
        manager.onSessionChanged(-1L)
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(false, manager.isRolesSwapped.value)
    }

    private var docked = false

    private fun newManager(initialRolesSwapped: Boolean = false): DualScreenManager = DualScreenManager(
        context = context,
        scope = testScope,
        gameDao = mockk(relaxed = true),
        gameRepository = mockk(relaxed = true),
        activeSaveRepository = mockk(relaxed = true),
        prefetchGameSaveDataUseCase = mockk(relaxed = true),
        platformRepository = mockk(relaxed = true),
        collectionRepository = mockk(relaxed = true),
        socialRepository = mockk(relaxed = true),
        downloadQueueDao = mockk(relaxed = true),
        downloadQueueRepository = mockk(relaxed = true),
        gameFileDao = mockk(relaxed = true),
        downloadManager = mockk(relaxed = true),
        gameActionsDelegate = mockk(relaxed = true),
        platformSyncQueue = mockk(relaxed = true),
        gameLaunchDelegate = mockk(relaxed = true),
        saveCacheManager = mockk(relaxed = true),
        getUnifiedSavesUseCase = mockk(relaxed = true),
        getUnifiedStatesUseCase = mockk(relaxed = true),
        stateCacheManager = mockk(relaxed = true),
        restoreCachedSaveUseCase = mockk(relaxed = true),
        activateSaveChannelUseCase = mockk(relaxed = true),
        restoreSaveChannelPointUseCase = mockk(relaxed = true),
        createSaveChannelUseCase = mockk(relaxed = true),
        copySaveChannelUseCase = mockk(relaxed = true),
        renameSaveChannelUseCase = mockk(relaxed = true),
        deleteSaveChannelUseCase = mockk(relaxed = true),
        restoreStateUseCase = mockk(relaxed = true),
        emulatorResolver = mockk(relaxed = true),
        coreVersionExtractor = mockk(relaxed = true),
        fetchAchievementsUseCase = mockk(relaxed = true),
        raRepository = mockk(relaxed = true),
        raTileContentRepository = mockk(relaxed = true),
        achievementUpdateBus = mockk(relaxed = true),
        displayAffinityHelper = mockk<com.nendo.argosy.util.DisplayAffinityHelper>(relaxed = true) {
            every { getRoleDisplayIds(any()) } returns null
            every { isDockedDark } answers { docked }
        },
        sessionStateStore = sessionStateStore,
        preferencesRepository = preferencesRepository,
        imageCacheManager = mockk(relaxed = true),
        romMRepository = mockk(relaxed = true),
        gameDocumentLoader = mockk(relaxed = true),
        documentHighlightStore = mockk(relaxed = true),
        resolveGameEmulatorContext = mockk(relaxed = true),
        hapticManager = mockk(relaxed = true),
        soundManager = mockk(relaxed = true),
        syncPreferencesRepository = mockk(relaxed = true),
        homeTileRepository = mockk(relaxed = true),
        homeTilePromptQueue = mockk(relaxed = true),
        appsRepository = mockk(relaxed = true),
        appShortcutActions = mockk(relaxed = true),
        notificationManager = mockk(relaxed = true),
        titleIdDownloadObserver = mockk(relaxed = true),
        homeGridPageRepository = mockk(relaxed = true),
        pageChooserEntrySource = mockk(relaxed = true),
        ambientAudioManager = mockk(relaxed = true),
        emulatorConfigDao = mockk(relaxed = true),
        configureEmulatorUseCase = mockk(relaxed = true),
        builtinCoreResolver = mockk(relaxed = true),
        saveHandlerRegistry = mockk(relaxed = true),
        steamDownloadQueueDao = mockk(relaxed = true),
        steamRepository = mockk(relaxed = true),
        playSessionTracker = mockk(relaxed = true),
        permissionHelper = mockk(relaxed = true),
        steamContentManager = mockk(relaxed = true),
        repairImageCacheUseCase = mockk(relaxed = true),
        downloadFileStatusRepository = mockk(relaxed = true),
        gradientExtractionDelegate = mockk(relaxed = true),
        filePickerFlow = mockk(relaxed = true),
        gameThemeAudioCoordinator = mockk(relaxed = true),
        getPinnedCollectionsUseCase = mockk(relaxed = true),
        getGamesForPinnedCollectionUseCase = mockk(relaxed = true),
        advanceCollectionFocusUseCase = mockk(relaxed = true),
        prepareCollectionQueueUseCase = mockk(relaxed = true),
        mediaRepository = mockk(relaxed = true),
        getRelatedMediaUseCase = mockk(relaxed = true),
        resolveMediaPlayTargetUseCase = mockk(relaxed = true),
        mediaPlaybackTracker = mockk(relaxed = true) {
            every { activePlayback } returns kotlinx.coroutines.flow.MutableStateFlow(null)
        },
        mediaAvailabilityVerifier = mockk(relaxed = true),
        mediaDownloadDelegate = mockk(relaxed = true),
        mediaSeriesDelegate = mockk(relaxed = true),
        mediaSiblingsDelegate = mockk(relaxed = true),
        initialRolesSwapped = initialRolesSwapped
    )
}
