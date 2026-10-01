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
import com.nendo.argosy.data.repository.SaveSyncApiClient
import com.nendo.argosy.data.repository.SaveSyncRepository
import com.nendo.argosy.data.repository.StateCacheManager
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.spyk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * A rom's server states come from every emulator that played it, and a state is a snapshot only the
 * emulator and core that wrote it can load. A game moved from the built-in core to a standalone
 * emulator must not get the built-in core's mGBA autosave dropped into that emulator's folder.
 */
class PreLaunchStateSyncOriginTest {

    @Rule
    @JvmField
    val temp = TemporaryFolder()

    private lateinit var statesDir: File
    private lateinit var manager: StateCacheManager
    private lateinit var useCase: PreLaunchStateSyncUseCase
    private lateinit var api: RomMApi
    private lateinit var gameDao: GameDao
    private lateinit var saveSyncApiClient: SaveSyncApiClient

    private val downloaded = mutableListOf<Long>()
    private val cachedRows = mutableMapOf<Long, StateCacheEntity>()
    private var launchEmulatorId = STANDALONE_ID

    @Before
    fun setUp() {
        statesDir = temp.newFolder("states")
        val game = mockk<GameEntity> {
            every { id } returns GAME_ID
            every { title } returns "Pokemon LeafGreen"
            every { rommId } returns ROMM_ID
            every { platformId } returns 5L
            every { platformSlug } returns "gba"
            every { localPath } returns "/roms/gba/$ROM_BASE.gba"
        }
        gameDao = mockk { coEvery { getById(GAME_ID) } returns game }
        saveSyncApiClient = mockk {
            coEvery { resolveCoreForGame(game, EmulatorRegistry.BUILTIN_ID) } returns "mgba"
            coEvery { resolveCoreForGame(game, STANDALONE_ID) } returns null
        }
        manager = spyk(newManager(gameDao, saveSyncApiClient))
        coEvery { manager.getByGameAndEmulator(any(), any()) } returns emptyList()
        coEvery {
            manager.downloadStateFromRomM(any(), any(), any(), any(), any(), any(), any(), any())
        } answers {
            val rommStateId = firstArg<Long>()
            val parsed = manager.parseStateFileName(secondArg())
            downloaded += rommStateId
            cachedRows[rommStateId] = StateCacheEntity(
                id = 100L + rommStateId,
                gameId = GAME_ID,
                platformSlug = "gba",
                emulatorId = arg(5),
                slotNumber = parsed.slotNumber,
                channelName = parsed.channelName,
                cachedAt = java.time.Instant.EPOCH,
                stateSize = 1,
                cachePath = "cached/$rommStateId",
                coreId = arg(6),
                rommSaveId = rommStateId,
                syncStatus = StateCacheEntity.STATUS_SYNCED
            )
            StateCacheManager.StateCloudResult.Success
        }
        coEvery { manager.getByRommSaveId(any()) } answers { cachedRows[firstArg()] }
        coEvery { manager.getStateById(any()) } answers { cachedRows.values.firstOrNull { it.id == firstArg<Long>() } }
        coEvery { manager.validateCoreVersion(any(), any(), any()) } returns VersionValidationResult.Compatible
        coEvery {
            manager.buildStateTargetPath(any(), any(), any(), any(), any(), any(), any(), any())
        } answers { File(statesDir, "${arg<String>(2)}_${arg<Int>(3)}.state").absolutePath }
        coEvery { manager.deleteState(any()) } answers { Unit }
        coEvery { manager.clearServerLink(any()) } returns Unit
        coEvery { manager.restoreState(any(), any()) } answers {
            File(secondArg<String>()).writeText(firstArg<Long>().toString())
            true
        }

        val emulatorConfigDao = mockk<EmulatorConfigDao> {
            coEvery { getByGameId(any()) } returns null
            coEvery { getDefaultForPlatform(any()) } returns null
        }
        val emulatorResolver = mockk<EmulatorResolver> {
            every { resolveEmulatorId(any()) } answers { launchEmulatorId }
        }
        val coreVersionExtractor = mockk<CoreVersionExtractor> {
            every { getCoreIdForEmulator(any(), any()) } answers { firstArg() }
        }
        val preferencesRepository = mockk<UserPreferencesRepository> {
            every { userPreferences } returns MutableStateFlow(UserPreferences(saveSyncEnabled = true))
        }
        api = mockk(relaxed = true)
        val saveSyncRepository = mockk<SaveSyncRepository>(relaxed = true) {
            every { getApi() } returns api
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
    fun `the built-in core's autosave is not placed for a standalone emulator`() = runTest {
        serverHas(serverState(id = 1L, emulator = "mgba", fileName = "$ROM_BASE [2026-09-30_16-52-23].autosave.state.auto"))

        val result = useCase(GAME_ID, STANDALONE_PACKAGE, channelName = null)

        assertEquals(PreLaunchStateSyncUseCase.Result.Ready, result)
        assertTrue(downloaded.isEmpty())
        assertTrue(statesDir.listFiles().isNullOrEmpty())
    }

    @Test
    fun `a newer state from another emulator does not hide the launch emulator's own`() = runTest {
        serverHas(
            serverState(id = 1L, emulator = STANDALONE_ID, fileName = "$ROM_BASE [2026-09-20_08-00-00].state.auto"),
            serverState(id = 2L, emulator = "mgba", fileName = "$ROM_BASE [2026-09-30_16-52-23].state.auto")
        )

        useCase(GAME_ID, STANDALONE_PACKAGE, channelName = null)

        assertEquals(listOf(1L), downloaded)
        assertEquals("101", File(statesDir, "${ROM_BASE}_-1.state").readText())
    }

    @Test
    fun `the built-in core takes a state any host of its core wrote`() = runTest {
        launchEmulatorId = EmulatorRegistry.BUILTIN_ID
        serverHas(serverState(id = 1L, emulator = "mgba", fileName = "$ROM_BASE [2026-09-30_16-52-23].autosave.state.auto"))

        useCase(GAME_ID, EmulatorRegistry.BUILTIN_PACKAGE, channelName = null)

        assertEquals(listOf(1L), downloaded)
    }

    @Test
    fun `a state with no emulator tag is taken as before`() = runTest {
        serverHas(serverState(id = 1L, emulator = null, fileName = "$ROM_BASE [2026-09-30_16-52-23].autosave.state.auto"))

        useCase(GAME_ID, STANDALONE_PACKAGE, channelName = null)

        assertEquals(listOf(1L), downloaded)
    }

    @Test
    fun `a synced copy of another emulator's state is dropped and the slot takes the emulator's own`() = runTest {
        serverHas(
            serverState(id = 1L, emulator = STANDALONE_ID, fileName = "$ROM_BASE [2026-09-20_08-00-00].state.auto"),
            serverState(id = 2L, emulator = "mgba", fileName = "$ROM_BASE [2026-09-30_16-52-23].state.auto")
        )
        localHas(linkedRow(rommSaveId = 2L, syncStatus = StateCacheEntity.STATUS_SYNCED))

        useCase(GAME_ID, STANDALONE_PACKAGE, channelName = null)

        coVerify(exactly = 1) { manager.deleteState(9L) }
        coVerify(exactly = 0) { manager.clearServerLink(any()) }
        assertEquals(listOf(1L), downloaded)
    }

    @Test
    fun `an unsent row linked to another emulator's state is unlinked instead of overwriting it`() = runTest {
        serverHas(serverState(id = 2L, emulator = "mgba", fileName = "$ROM_BASE [2026-09-30_16-52-23].state.auto"))
        localHas(linkedRow(rommSaveId = 2L, syncStatus = StateCacheEntity.STATUS_LOCAL_NEWER))

        useCase(GAME_ID, STANDALONE_PACKAGE, channelName = null)

        coVerify(exactly = 1) { manager.clearServerLink(9L) }
        coVerify(exactly = 0) { manager.deleteState(any()) }
        assertTrue(downloaded.isEmpty())
    }

    @Test
    fun `a built-in row linked to another core's state is left to the core check`() = runTest {
        launchEmulatorId = EmulatorRegistry.BUILTIN_ID
        serverHas(serverState(id = 2L, emulator = "gpsp", fileName = "$ROM_BASE [2026-09-30_16-52-23].state.auto"))
        localHas(linkedRow(rommSaveId = 2L, syncStatus = StateCacheEntity.STATUS_SYNCED, emulatorId = EmulatorRegistry.BUILTIN_ID))

        useCase(GAME_ID, EmulatorRegistry.BUILTIN_PACKAGE, channelName = null)

        coVerify(exactly = 0) { manager.deleteState(any()) }
        coVerify(exactly = 0) { manager.clearServerLink(any()) }
    }

    @Test
    fun `a download of another emulator's state is refused before anything is cached`() = runTest {
        val plain = newManager(gameDao, saveSyncApiClient)
        val foreign = serverState(id = 1L, emulator = "mgba", fileName = "$ROM_BASE [2026-09-30_16-52-23].autosave.state.auto")

        val result = plain.downloadStateFromRomM(
            rommStateId = foreign.id,
            fileName = foreign.fileName,
            api = api,
            gameId = GAME_ID,
            platformSlug = "gba",
            emulatorId = STANDALONE_ID,
            coreId = STANDALONE_ID,
            serverState = foreign
        )

        assertEquals(StateCacheManager.StateCloudResult.NoStateFound, result)
        coVerify(exactly = 0) { api.downloadRaw(any()) }
    }

    private fun newManager(gameDao: GameDao, saveSyncApiClient: SaveSyncApiClient): StateCacheManager {
        val context = mockk<Context>(relaxed = true).also { every { it.filesDir } returns temp.root }
        return StateCacheManager(
            context = context,
            gameDao = gameDao,
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
            saveSyncApiClient = saveSyncApiClient,
            payloadCodec = mockk(relaxed = true),
            attributionRepository = mockk(relaxed = true),
            stateOwnershipTracker = mockk(relaxed = true)
        )
    }

    private fun localHas(vararg rows: StateCacheEntity) {
        coEvery { manager.getByGameAndEmulator(GAME_ID, any()) } returns rows.toList()
    }

    private fun linkedRow(rommSaveId: Long, syncStatus: String, emulatorId: String = STANDALONE_ID) = StateCacheEntity(
        id = 9L,
        gameId = GAME_ID,
        platformSlug = "gba",
        emulatorId = emulatorId,
        slotNumber = -1,
        cachedAt = java.time.Instant.EPOCH,
        stateSize = 1,
        cachePath = "cached/9",
        coreId = emulatorId,
        rommSaveId = rommSaveId,
        syncStatus = syncStatus,
        serverUpdatedAt = java.time.Instant.parse("2026-09-30T00:00:00Z")
    )

    private fun serverHas(vararg states: RomMState) {
        coEvery { manager.checkServerStates(ROMM_ID, any()) } returns states.toList()
    }

    private fun serverState(id: Long, emulator: String?, fileName: String) = RomMState(
        id = id,
        romId = ROMM_ID,
        userId = 1L,
        emulator = emulator,
        fileName = fileName,
        downloadPath = "/states/$id",
        updatedAt = "2026-09-30T00:00:00Z"
    )

    private companion object {
        const val GAME_ID = 352L
        const val ROMM_ID = 4100L
        const val ROM_BASE = "Pokemon - LeafGreen Version (USA, Europe) (Rev 1)"
        const val STANDALONE_ID = "pizza_boy_gba"
        const val STANDALONE_PACKAGE = "it.dbtecno.pizzaboygba"
    }
}
