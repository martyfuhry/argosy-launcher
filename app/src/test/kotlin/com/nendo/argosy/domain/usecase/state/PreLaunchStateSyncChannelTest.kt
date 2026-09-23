package com.nendo.argosy.domain.usecase.state

import android.content.Context
import com.nendo.argosy.data.emulator.CoreVersionExtractor
import com.nendo.argosy.data.emulator.EmulatorRegistry
import com.nendo.argosy.data.emulator.EmulatorResolver
import com.nendo.argosy.data.emulator.VersionValidationResult
import com.nendo.argosy.data.local.dao.EmulatorConfigDao
import com.nendo.argosy.data.local.dao.GameDao
import com.nendo.argosy.data.local.entity.GameEntity
import com.nendo.argosy.data.local.entity.StateCacheEntity
import com.nendo.argosy.data.preferences.UserPreferences
import com.nendo.argosy.data.preferences.UserPreferencesRepository
import com.nendo.argosy.data.remote.romm.RomMApi
import com.nendo.argosy.data.remote.romm.RomMState
import com.nendo.argosy.data.repository.SaveSyncRepository
import com.nendo.argosy.data.repository.StateCacheManager
import com.nendo.argosy.libretro.LibretroStateSlots
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.spyk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.time.Instant

/**
 * A launch that names no channel plays the autosave slot, and the states another install uploaded
 * from that slot carry the literal autosave channel. Pre-launch sync has to file both under one
 * channel, or the state comes down into the cache and the game boots without it.
 */
class PreLaunchStateSyncChannelTest {

    @Rule
    @JvmField
    val temp = TemporaryFolder()

    private lateinit var statesDir: File
    private lateinit var manager: StateCacheManager
    private lateinit var useCase: PreLaunchStateSyncUseCase

    private val downloaded = mutableListOf<Long>()
    private val cachedRows = mutableMapOf<Long, StateCacheEntity>()

    @Before
    fun setUp() {
        statesDir = temp.newFolder("states")
        val context = mockk<Context>(relaxed = true).also { every { it.filesDir } returns temp.root }
        manager = spyk(
            StateCacheManager(
                context = context,
                gameDao = mockk(relaxed = true),
                stateCacheDao = mockk(relaxed = true),
                stateTombstoneDao = mockk(relaxed = true),
                saveCacheDao = mockk(relaxed = true),
                saveSyncDao = mockk(relaxed = true),
                pendingSyncQueueDao = mockk(relaxed = true),
                emulatorSaveConfigDao = mockk(relaxed = true),
                preferencesRepository = mockk(relaxed = true),
                syncPreferencesRepository = mockk(relaxed = true),
                coreVersionExtractor = mockk(relaxed = true),
                retroArchConfigParser = mockk(relaxed = true),
                retroArchPathResolver = mockk(relaxed = true),
                libretroStatePathResolver = mockk(relaxed = true),
                saveSyncApiClient = mockk(relaxed = true),
                payloadCodec = mockk(relaxed = true),
                attributionRepository = mockk(relaxed = true),
                stateOwnershipTracker = mockk(relaxed = true)
            )
        )
        coEvery {
            manager.downloadStateFromRomM(any(), any(), any(), any(), any(), any(), any(), any())
        } answers {
            val rommStateId = firstArg<Long>()
            val parsed = manager.parseStateFileName(secondArg())
            downloaded += rommStateId
            cachedRows[rommStateId] = cachedRow(
                id = 100L + rommStateId,
                slotNumber = parsed.slotNumber,
                channelName = parsed.channelName,
                rommSaveId = rommStateId
            )
            StateCacheManager.StateCloudResult.Success
        }
        coEvery { manager.getByRommSaveId(any()) } answers { cachedRows[firstArg()] }
        coEvery { manager.getStateById(any()) } answers { cachedRows.values.firstOrNull { it.id == firstArg<Long>() } }
        coEvery { manager.validateCoreVersion(any(), any(), any()) } returns VersionValidationResult.Compatible
        coEvery {
            manager.buildStateTargetPath(any(), any(), any(), any(), any(), any(), any(), any())
        } answers { File(statesDir, LibretroStateSlots.fileName(arg(2), arg(3))).absolutePath }
        coEvery { manager.restoreState(any(), any()) } answers {
            File(secondArg<String>()).writeText(firstArg<Long>().toString())
            true
        }
        coEvery { manager.clearServerLink(any()) } returns Unit

        val game = mockk<GameEntity> {
            every { id } returns GAME_ID
            every { title } returns "Mother 3"
            every { rommId } returns ROMM_ID
            every { platformId } returns 5L
            every { platformSlug } returns "gba"
            every { localPath } returns "/roms/gba/$ROM_BASE.gba"
        }
        val gameDao = mockk<GameDao> { coEvery { getById(GAME_ID) } returns game }
        val emulatorConfigDao = mockk<EmulatorConfigDao> {
            coEvery { getByGameId(any()) } returns null
            coEvery { getDefaultForPlatform(any()) } returns null
        }
        val emulatorResolver = mockk<EmulatorResolver> {
            every { resolveEmulatorId(EmulatorRegistry.BUILTIN_PACKAGE) } returns EmulatorRegistry.BUILTIN_ID
        }
        val coreVersionExtractor = mockk<CoreVersionExtractor> {
            every { getCoreIdForEmulator(any(), any()) } returns "mgba"
        }
        val preferencesRepository = mockk<UserPreferencesRepository> {
            every { userPreferences } returns MutableStateFlow(UserPreferences(saveSyncEnabled = true))
        }
        val saveSyncRepository = mockk<SaveSyncRepository> {
            every { getApi() } returns mockk<RomMApi>()
        }

        useCase = PreLaunchStateSyncUseCase(
            stateCacheManager = manager,
            saveSyncRepository = saveSyncRepository,
            gameDao = gameDao,
            emulatorConfigDao = emulatorConfigDao,
            emulatorResolver = emulatorResolver,
            coreVersionExtractor = coreVersionExtractor,
            preferencesRepository = preferencesRepository,
            restoreStateUseCase = RestoreStateUseCase(manager)
        )
    }

    @Test
    fun `a fresh install resumes from the server's autosave state on a launch with no channel`() = runTest {
        serverHas(serverState(id = 1L, fileName = "$ROM_BASE [2026-08-18_11-29-56].autosave.state.auto"))
        localHas()

        val result = useCase(GAME_ID, EmulatorRegistry.BUILTIN_PACKAGE, channelName = null)

        assertEquals(PreLaunchStateSyncUseCase.Result.Downloaded(1), result)
        assertEquals("101", liveAutoFile().readText())
    }

    @Test
    fun `a launch on a named channel only caches the server's autosave state`() = runTest {
        serverHas(serverState(id = 1L, fileName = "$ROM_BASE [2026-08-18_11-29-56].autosave.state.auto"))
        localHas()

        val result = useCase(GAME_ID, EmulatorRegistry.BUILTIN_PACKAGE, channelName = "Speedrun")

        assertEquals(PreLaunchStateSyncUseCase.Result.Downloaded(1), result)
        assertFalse(liveAutoFile().exists())
    }

    @Test
    fun `an unsent local autosave with no channel is kept over the server's autosave state`() = runTest {
        serverHas(serverState(id = 1L, fileName = "$ROM_BASE [2026-08-18_11-29-56].autosave.state.auto"))
        localHas(cachedRow(id = 7L, slotNumber = -1, channelName = null, rommSaveId = null))

        val result = useCase(GAME_ID, EmulatorRegistry.BUILTIN_PACKAGE, channelName = null)

        assertEquals(PreLaunchStateSyncUseCase.Result.Ready, result)
        assertTrue(downloaded.isEmpty())
        assertFalse(liveAutoFile().exists())
    }

    @Test
    fun `the newest of a slot's autosave and unnamed states is the one placed`() = runTest {
        serverHas(
            serverState(id = 1L, fileName = "$ROM_BASE [2026-08-18_11-29-56].state.auto"),
            serverState(id = 2L, fileName = "$ROM_BASE [2026-09-20_08-00-00].autosave.state.auto")
        )
        localHas()

        useCase(GAME_ID, EmulatorRegistry.BUILTIN_PACKAGE, channelName = null)

        assertEquals(listOf(2L), downloaded)
        assertEquals("102", liveAutoFile().readText())
    }

    private fun serverHas(vararg states: RomMState) {
        coEvery { manager.checkServerStates(ROMM_ID, any()) } returns states.toList()
    }

    private fun localHas(vararg rows: StateCacheEntity) {
        rows.forEach { row -> row.rommSaveId?.let { cachedRows[it] = row } }
        coEvery { manager.getByGameAndEmulator(GAME_ID, EmulatorRegistry.BUILTIN_ID) } returns rows.toList()
    }

    private fun liveAutoFile(): File =
        File(statesDir, LibretroStateSlots.fileName(ROM_BASE, LibretroStateSlots.AUTO_SLOT))

    private fun serverState(id: Long, fileName: String) = RomMState(
        id = id,
        romId = ROMM_ID,
        userId = 1L,
        emulator = "mgba",
        fileName = fileName,
        updatedAt = "2026-09-22T00:00:00Z"
    )

    private fun cachedRow(
        id: Long,
        slotNumber: Int,
        channelName: String?,
        rommSaveId: Long?
    ) = StateCacheEntity(
        id = id,
        gameId = GAME_ID,
        platformSlug = "gba",
        emulatorId = EmulatorRegistry.BUILTIN_ID,
        slotNumber = slotNumber,
        channelName = channelName,
        cachedAt = Instant.ofEpochSecond(1_000),
        stateSize = 1,
        cachePath = "cached/$id",
        coreId = "mgba",
        rommSaveId = rommSaveId,
        syncStatus = if (rommSaveId == null) StateCacheEntity.STATUS_PENDING_UPLOAD else StateCacheEntity.STATUS_SYNCED
    )

    private companion object {
        const val GAME_ID = 407L
        const val ROMM_ID = 3267L
        const val ROM_BASE = "Mother 3 (English v1.3)"
    }
}
