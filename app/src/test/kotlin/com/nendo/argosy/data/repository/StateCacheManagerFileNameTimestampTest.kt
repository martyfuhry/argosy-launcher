package com.nendo.argosy.data.repository

import android.content.Context
import com.nendo.argosy.data.local.entity.StateCacheEntity
import io.mockk.mockk
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Instant
import java.util.TimeZone

/**
 * The bracketed timestamp in an uploaded state's file name is written in UTC and must read back
 * as the same instant on every device, or a device west of Greenwich ranks its own older state
 * above a newer one from the server and tombstones the wrong one.
 */
class StateCacheManagerFileNameTimestampTest {

    private val defaultZone = TimeZone.getDefault()
    private lateinit var manager: StateCacheManager

    @Before
    fun setUp() {
        manager = StateCacheManager(
            context = mockk<Context>(relaxed = true),
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
    }

    @After
    fun tearDown() {
        TimeZone.setDefault(defaultZone)
    }

    @Test
    fun `file name timestamp round-trips west of utc`() {
        TimeZone.setDefault(TimeZone.getTimeZone("America/New_York"))

        assertEquals(CACHED_AT, manager.parseStateFileTimestamp(manager.buildUploadFileName(state(), ROM)))
    }

    @Test
    fun `file name timestamp round-trips east of utc`() {
        TimeZone.setDefault(TimeZone.getTimeZone("Asia/Tokyo"))

        assertEquals(CACHED_AT, manager.parseStateFileTimestamp(manager.buildUploadFileName(state(), ROM)))
    }

    @Test
    fun `file name timestamp is read as utc whatever the device zone`() {
        TimeZone.setDefault(TimeZone.getTimeZone("America/New_York"))

        assertEquals(CACHED_AT, manager.parseStateFileTimestamp("$ROM [2024-06-01_12-00-00].state1"))
    }

    @Test
    fun `file name timestamp is written in utc whatever the device zone`() {
        TimeZone.setDefault(TimeZone.getTimeZone("Asia/Tokyo"))

        assertEquals("$ROM [2024-06-01_12-00-00].state1", manager.buildUploadFileName(state(), ROM))
    }

    @Test
    fun `server state newer by the clock wins on a device west of utc`() {
        TimeZone.setDefault(TimeZone.getTimeZone("America/New_York"))
        val local = manager.parseStateFileTimestamp(manager.buildUploadFileName(state(), ROM))!!
        val server = manager.parseStateFileTimestamp("$ROM [2024-06-01_13-00-00].state1")!!

        assertTrue(server.isAfter(local))
    }

    @Test
    fun `file name without a bracketed timestamp has no timestamp`() {
        assertNull(manager.parseStateFileTimestamp("$ROM.state1"))
    }

    private fun state() = StateCacheEntity(
        gameId = 7L,
        platformSlug = "snes",
        emulatorId = "retroarch",
        slotNumber = 1,
        cachedAt = CACHED_AT,
        stateSize = 0,
        cachePath = "unused"
    )

    private companion object {
        const val ROM = "Super Metroid (USA)"
        val CACHED_AT: Instant = Instant.parse("2024-06-01T12:00:00Z")
    }
}
