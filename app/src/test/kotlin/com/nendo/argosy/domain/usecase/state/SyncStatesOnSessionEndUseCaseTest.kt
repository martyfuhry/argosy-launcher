package com.nendo.argosy.domain.usecase.state

import com.nendo.argosy.data.emulator.EmulatorDetector
import com.nendo.argosy.data.emulator.EmulatorRegistry
import com.nendo.argosy.data.local.dao.GameDao
import com.nendo.argosy.data.local.entity.GameEntity
import com.nendo.argosy.data.local.entity.StateCacheEntity
import com.nendo.argosy.data.preferences.AccountSwitchMarkerStore
import com.nendo.argosy.data.preferences.UserPreferences
import com.nendo.argosy.data.preferences.UserPreferencesRepository
import com.nendo.argosy.data.repository.DiscoveredState
import com.nendo.argosy.data.repository.StateCacheManager
import com.nendo.argosy.data.sync.StateClaim
import com.nendo.argosy.data.sync.StateOwnershipTracker
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.io.File
import java.time.Instant

/**
 * With Secure Saves off, states written outside a session are adopted, except a slot whose file is
 * byte-identical to the cached state for that slot: that file is one Argosy put there, and adopting
 * it would upload a second server copy of the same state.
 */
class SyncStatesOnSessionEndUseCaseTest {

    private val stateCacheManager = mockk<StateCacheManager>(relaxed = true)
    private val gameDao = mockk<GameDao>()
    private val emulatorDetector = mockk<EmulatorDetector>()
    private val preferencesRepository = mockk<UserPreferencesRepository>()
    private val ownershipTracker = mockk<StateOwnershipTracker>()
    private val accountSwitchMarkerStore = mockk<AccountSwitchMarkerStore>()
    private val preferences = MutableStateFlow(UserPreferences(secureSaves = false))

    private val file = File("slot1.state1")
    private val existing = StateCacheEntity(
        id = 11L,
        gameId = GAME_ID,
        platformSlug = "snes",
        emulatorId = EmulatorRegistry.BUILTIN_ID,
        slotNumber = 1,
        cachedAt = Instant.ofEpochSecond(1_000),
        stateSize = 1,
        cachePath = "cached"
    )

    private lateinit var useCase: SyncStatesOnSessionEndUseCase

    @Before
    fun setUp() {
        val game = mockk<GameEntity> {
            every { localPath } returns "/roms/game.sfc"
            every { platformSlug } returns "snes"
            every { rommId } returns null
        }
        coEvery { gameDao.getById(GAME_ID) } returns game
        every { preferencesRepository.userPreferences } returns preferences
        every { emulatorDetector.getByPackage(any()) } returns
            EmulatorRegistry.getByPackage(EmulatorRegistry.BUILTIN_PACKAGE)
        coEvery { ownershipTracker.claim(any(), any()) } returns StateClaim.Mine
        coEvery { accountSwitchMarkerStore.isSwitching() } returns false
        coEvery {
            stateCacheManager.discoverStatesForGame(any(), any(), any(), any(), any(), any())
        } returns listOf(DiscoveredState(file, slotNumber = 1, lastModified = Instant.ofEpochSecond(2_000)))
        coEvery { stateCacheManager.getStateBySlot(GAME_ID, any(), 1, any()) } returns existing
        coEvery {
            stateCacheManager.cacheState(any(), any(), any(), any(), any(), any(), any(), any(), any(), any())
        } returns 42L

        useCase = SyncStatesOnSessionEndUseCase(
            stateCacheManager = stateCacheManager,
            gameDao = gameDao,
            activeSaveRepository = mockk(relaxed = true),
            emulatorDetector = emulatorDetector,
            coreVersionExtractor = mockk(relaxed = true),
            preferencesRepository = preferencesRepository,
            stateOwnershipTracker = ownershipTracker,
            emulatorResolver = mockk(relaxed = true),
            accountSwitchMarkerStore = accountSwitchMarkerStore
        )
    }

    @Test
    fun `off-session state matching the cached slot is not adopted`() = runTest {
        coEvery { stateCacheManager.hasSameContent(existing, file) } returns true

        useCase.adoptOffSessionStates(GAME_ID, EmulatorRegistry.BUILTIN_PACKAGE, queueUploads = false)

        coVerify(exactly = 0) {
            stateCacheManager.cacheState(any(), any(), any(), any(), any(), any(), any(), any(), any(), any())
        }
    }

    @Test
    fun `off-session state that differs from the cached slot is adopted`() = runTest {
        coEvery { stateCacheManager.hasSameContent(existing, file) } returns false

        val result = useCase.adoptOffSessionStates(GAME_ID, EmulatorRegistry.BUILTIN_PACKAGE, queueUploads = false)

        assertEquals(StateSyncResult.Cached(1, 0), result)
        coVerify { stateCacheManager.markForUpload(42L) }
    }

    @Test
    fun `an off-session state older than the server copy uploads as its own state`() = runTest {
        coEvery { stateCacheManager.hasSameContent(any(), any()) } returns false
        coEvery { stateCacheManager.getStateBySlot(GAME_ID, any(), 1, any()) } returns existing.copy(
            rommSaveId = 99L,
            serverUpdatedAt = Instant.ofEpochSecond(3_000)
        )

        useCase.adoptOffSessionStates(GAME_ID, EmulatorRegistry.BUILTIN_PACKAGE, queueUploads = false)

        coVerify { stateCacheManager.clearServerLink(existing.id) }
    }

    @Test
    fun `an off-session state newer than the server copy keeps its server link`() = runTest {
        coEvery { stateCacheManager.hasSameContent(any(), any()) } returns false
        coEvery { stateCacheManager.getStateBySlot(GAME_ID, any(), 1, any()) } returns existing.copy(
            rommSaveId = 99L,
            serverUpdatedAt = Instant.ofEpochSecond(1_500)
        )

        useCase.adoptOffSessionStates(GAME_ID, EmulatorRegistry.BUILTIN_PACKAGE, queueUploads = false)

        coVerify(exactly = 0) { stateCacheManager.clearServerLink(any()) }
    }

    @Test
    fun `a slot no newer than its cache is not hashed`() = runTest {
        coEvery {
            stateCacheManager.discoverStatesForGame(any(), any(), any(), any(), any(), any())
        } returns listOf(DiscoveredState(file, slotNumber = 1, lastModified = Instant.ofEpochSecond(500)))

        useCase.adoptOffSessionStates(GAME_ID, EmulatorRegistry.BUILTIN_PACKAGE, queueUploads = false)

        coVerify(exactly = 0) { stateCacheManager.hasSameContent(any(), any()) }
    }

    @Test
    fun `nothing is adopted during an account switch`() = runTest {
        coEvery { accountSwitchMarkerStore.isSwitching() } returns true

        val result = useCase.adoptOffSessionStates(GAME_ID, EmulatorRegistry.BUILTIN_PACKAGE, queueUploads = false)

        assertEquals(StateSyncResult.NotConfigured, result)
        coVerify(exactly = 0) {
            stateCacheManager.discoverStatesForGame(any(), any(), any(), any(), any(), any())
        }
    }

    @Test
    fun `nothing is adopted while Secure Saves is on`() = runTest {
        preferences.value = UserPreferences(secureSaves = true)

        val result = useCase.adoptOffSessionStates(GAME_ID, EmulatorRegistry.BUILTIN_PACKAGE, queueUploads = false)

        assertEquals(StateSyncResult.NotConfigured, result)
        coVerify(exactly = 0) {
            stateCacheManager.discoverStatesForGame(any(), any(), any(), any(), any(), any())
        }
    }

    @Test
    fun `session end still caches a newer slot without comparing content`() = runTest {
        useCase(GAME_ID, EmulatorRegistry.BUILTIN_PACKAGE, queueUploads = false)

        coVerify(exactly = 0) { stateCacheManager.hasSameContent(any(), any()) }
        coVerify { stateCacheManager.markForUpload(42L) }
    }

    private companion object {
        const val GAME_ID = 5L
    }
}
