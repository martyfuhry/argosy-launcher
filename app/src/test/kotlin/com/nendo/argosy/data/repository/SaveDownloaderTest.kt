package com.nendo.argosy.data.repository

import com.nendo.argosy.data.local.dao.GameDao
import com.nendo.argosy.data.local.dao.SaveSyncDao
import com.nendo.argosy.data.local.entity.GameEntity
import com.nendo.argosy.data.model.GameSource
import com.nendo.argosy.data.local.entity.SaveCacheEntity
import com.nendo.argosy.data.local.entity.SaveSyncEntity
import com.nendo.argosy.data.remote.romm.RomMApi
import com.nendo.argosy.data.remote.romm.RomMCapabilities
import com.nendo.argosy.data.remote.romm.RomMDeviceIdRequest
import com.nendo.argosy.data.remote.romm.RomMDeviceSync
import com.nendo.argosy.data.remote.romm.RomMSave
import com.nendo.argosy.data.storage.FileAccessLayer
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import okhttp3.ResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.Response
import java.time.Instant

// SaveDownloader is heavily coupled to Android (Context, FileAccessLayer, platform handlers) and
// real file I/O, making full unit coverage of downloadSave() impractical without a significant
// refactor or instrumented tests. The tests here cover the parts that can be isolated cleanly.
//
// Gaps requiring instrumented or integration tests:
//   - confirmDeviceSynced() is called after a successful network downloadSave() (the download loop fix)
//   - saveSyncDao.upsert() is called with lastUploadedHash == serverSave.contentHash post-download
//   - folder-based saves use the correct path extraction logic

class SaveDownloaderTest {

    private val api: RomMApi = mockk()
    private val mockApiClient: SaveSyncApiClient = mockk {
        every { getApi() } returns api
        every { getDeviceId() } returns "device-abc"
    }
    private val saveSyncDao: SaveSyncDao = mockk(relaxed = true)
    private val gameDao: GameDao = mockk(relaxed = true)
    private val activeSaveRepository: ActiveSaveRepository = mockk(relaxed = true)
    private val saveCacheManager: SaveCacheManager = mockk(relaxed = true)
    private val fal: FileAccessLayer = mockk(relaxed = true)
    private var pendingSaveId: Long? = null

    private val downloader = SaveDownloader(
        context = mockk(relaxed = true),
        saveSyncDao = saveSyncDao,
        saveCacheDao = mockk(relaxed = true),
        emulatorResolver = mockk(relaxed = true),
        gameDao = gameDao,
        activeSaveRepository = activeSaveRepository,
        titleDbRepository = mockk(relaxed = true),
        titleIdExtractor = mockk(relaxed = true),
        saveArchiver = mockk(relaxed = true),
        savePathResolver = mockk(relaxed = true),
        syncPreferencesRepository = mockk(relaxed = true),
        saveCacheManager = dagger.Lazy { saveCacheManager },
        fal = fal,
        switchSaveHandler = mockk(relaxed = true),
        gciSaveHandler = mockk(relaxed = true),
        apiClient = dagger.Lazy { mockApiClient },
        saveUploader = dagger.Lazy { mockk(relaxed = true) },
        emulatorSaveConfigRepository = mockk(relaxed = true),
        unitSaveHandler = mockk(relaxed = true),
        saveUnitResolver = mockk(relaxed = true)
    )

    private val gameId = 5L
    private val serverSaveId = 41L
    private val savePath = "/storage/saves/game.srm"

    private fun serverSave(deviceSyncs: List<RomMDeviceSync>) = RomMSave(
        id = serverSaveId, romId = 3268L, userId = 1L, emulator = "retroarch",
        fileName = "game.srm", updatedAt = "2026-09-20T12:00:00Z", slot = "autosave",
        contentHash = "server-hash", originDeviceId = "device-origin", deviceSyncs = deviceSyncs
    )

    private fun primeCacheHit(deviceSyncs: List<RomMDeviceSync>) {
        coEvery { gameDao.getById(gameId) } returns GameEntity(
            id = gameId, platformId = 1L, platformSlug = "gba", title = "Game", sortTitle = "game",
            localPath = "/roms/game.gba", rommId = 3268L, igdbId = null, source = GameSource.ROMM_SYNCED
        )
        coEvery { saveSyncDao.getByGameEmulatorAndChannel(gameId, "retroarch", "autosave", any()) } returns
            SaveSyncEntity(
                gameId = gameId, rommId = 3268L, emulatorId = "retroarch", channelName = "autosave",
                rommSaveId = serverSaveId, localSavePath = savePath, syncStatus = SaveSyncEntity.STATUS_SERVER_NEWER
            )
        every { fal.exists(savePath) } returns true
        coEvery { mockApiClient.resolveCoreForGame(any<GameEntity>(), any()) } returns "mgba"
        every { mockApiClient.getCapabilities() } returns mockk<RomMCapabilities>(relaxed = true)
        coEvery { api.getSaveWithDevice(serverSaveId, "device-abc") } returns Response.success(serverSave(deviceSyncs))
        coEvery { saveCacheManager.findCachedByHash(gameId, "server-hash") } returns SaveCacheEntity(
            id = 9L, gameId = gameId, emulatorId = "retroarch", cachedAt = Instant.EPOCH, saveSize = 1L,
            cachePath = "/cache/9", contentHash = "server-hash"
        )
        coEvery { saveCacheManager.restoreSave(9L, savePath) } returns true
        coEvery { activeSaveRepository.setPendingDeviceSyncSaveId(gameId, any()) } answers { pendingSaveId = secondArg() }
    }

    @Test
    fun `cache-hit adoption of a save this device is not current on confirms the device`() = runTest {
        primeCacheHit(listOf(RomMDeviceSync(deviceId = "device-origin", isCurrent = true)))
        coEvery { api.confirmSaveDownloaded(serverSaveId, RomMDeviceIdRequest("device-abc")) } returns
            Response.success(serverSave(emptyList()))

        val result = downloader.downloadSave(gameId, "retroarch", channelName = "autosave")

        assertTrue(result is SaveSyncResult.Success)
        coVerify(exactly = 1) { api.confirmSaveDownloaded(serverSaveId, RomMDeviceIdRequest("device-abc")) }
        coVerify(exactly = 0) { api.downloadSaveContentWithDevice(any(), any(), any()) }
        assertNull(pendingSaveId)
    }

    @Test
    fun `cache-hit confirm that fails offline stays pending for the pre-launch flush`() = runTest {
        primeCacheHit(listOf(RomMDeviceSync(deviceId = "device-abc", isCurrent = false)))
        coEvery { api.confirmSaveDownloaded(any(), any()) } throws java.io.IOException("offline")

        val result = downloader.downloadSave(gameId, "retroarch", channelName = "autosave")

        assertTrue(result is SaveSyncResult.Success)
        assertEquals(serverSaveId, pendingSaveId)
    }

    @Test
    fun `cache-hit adoption skips the confirm when the server already marks this device current`() = runTest {
        primeCacheHit(listOf(RomMDeviceSync(deviceId = "device-abc", isCurrent = true)))

        downloader.downloadSave(gameId, "retroarch", channelName = "autosave")

        coVerify(exactly = 0) { api.confirmSaveDownloaded(any(), any()) }
        assertNull(pendingSaveId)
    }

    @Test
    fun `flushPendingDeviceSync keeps the pending save when the confirm fails and clears it on success`() = runTest {
        pendingSaveId = serverSaveId
        coEvery { activeSaveRepository.getPendingDeviceSyncSaveId(gameId) } answers { pendingSaveId }
        coEvery { activeSaveRepository.setPendingDeviceSyncSaveId(gameId, any()) } answers { pendingSaveId = secondArg() }
        coEvery { api.confirmSaveDownloaded(any(), any()) } returns Response.error(503, ResponseBody.create(null, ""))

        downloader.flushPendingDeviceSync(gameId)
        assertEquals(serverSaveId, pendingSaveId)

        coEvery { api.confirmSaveDownloaded(any(), any()) } returns Response.success(serverSave(emptyList()))
        downloader.flushPendingDeviceSync(gameId)
        assertNull(pendingSaveId)
    }

    @Test
    fun `confirmDeviceSynced treats a client error as settled so a deleted save is not retried forever`() = runTest {
        coEvery { api.confirmSaveDownloaded(any(), any()) } returns Response.error(404, ResponseBody.create(null, ""))

        assertTrue(downloader.confirmDeviceSynced(3L))
    }

    @Test
    fun `confirmDeviceSynced calls confirmSaveDownloaded with correct saveId and deviceId`() = runTest {
        coEvery { api.confirmSaveDownloaded(42L, RomMDeviceIdRequest("device-abc")) } returns
            Response.success(RomMSave(id = 42L, romId = 1L, userId = 1L, emulator = null, fileName = "save.sav", updatedAt = ""))

        downloader.confirmDeviceSynced(42L)

        coVerify { api.confirmSaveDownloaded(42L, RomMDeviceIdRequest("device-abc")) }
    }

    @Test
    fun `confirmDeviceSynced does not throw on HTTP error`() = runTest {
        coEvery { api.confirmSaveDownloaded(any(), any()) } returns
            Response.error(500, ResponseBody.create(null, ""))

        assertFalse(downloader.confirmDeviceSynced(99L))

        coVerify { api.confirmSaveDownloaded(99L, any()) }
    }

    @Test
    fun `confirmDeviceSynced does not throw on network exception`() = runTest {
        coEvery { api.confirmSaveDownloaded(any(), any()) } throws RuntimeException("timeout")

        assertFalse(downloader.confirmDeviceSynced(7L))
    }

    @Test
    fun `confirmDeviceSynced skips api call when api is null`() = runTest {
        every { mockApiClient.getApi() } returns null

        downloader.confirmDeviceSynced(1L)

        coVerify(exactly = 0) { api.confirmSaveDownloaded(any(), any()) }
    }

    @Test
    fun `confirmDeviceSynced skips api call when deviceId is null`() = runTest {
        every { mockApiClient.getDeviceId() } returns null

        downloader.confirmDeviceSynced(1L)

        coVerify(exactly = 0) { api.confirmSaveDownloaded(any(), any()) }
    }
}
