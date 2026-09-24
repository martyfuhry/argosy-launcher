package com.nendo.argosy.data.repository

import android.content.Context
import com.nendo.argosy.data.local.dao.FirmwareDao
import com.nendo.argosy.data.local.entity.FirmwareEntity
import com.nendo.argosy.data.remote.romm.RomMFirmware
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import java.io.File
import java.time.Instant

class BiosFirmwareSyncTest {

    private val rows = mutableListOf<FirmwareEntity>()
    private lateinit var repository: BiosRepository

    @Before
    fun setUp() {
        rows.clear()
        val dao = mockk<FirmwareDao>(relaxed = true)
        coEvery { dao.getByRommId(any()) } answers { rows.firstOrNull { it.rommId == firstArg() } }
        coEvery { dao.upsertAll(any()) } answers {
            val incoming = firstArg<List<FirmwareEntity>>()
            rows.removeAll { row -> incoming.any { it.rommId == row.rommId } }
            rows.addAll(incoming)
        }
        coEvery { dao.deleteRemovedFirmware(any(), any()) } answers {
            val platformId = firstArg<Long>()
            val keep = secondArg<List<Long>>()
            rows.removeAll { it.platformId == platformId && it.rommId !in keep }
        }
        coEvery { dao.deleteByPlatform(any()) } answers {
            val platformId = firstArg<Long>()
            rows.removeAll { it.platformId == platformId }
        }

        val context = mockk<Context>(relaxed = true)
        every { context.filesDir } returns File("build/tmp/bios-sync")
        repository = BiosRepository(
            context = context,
            firmwareDao = dao,
            platformDao = mockk(relaxed = true),
            userPreferencesRepository = mockk(relaxed = true),
            switchKeyManager = mockk(relaxed = true),
            attributionRepository = mockk(relaxed = true),
            xboxDiskImageProvisioner = mockk(relaxed = true)
        )
    }

    private fun remote(id: Long, name: String, missing: Boolean = false) = RomMFirmware(
        id = id,
        fileName = name,
        filePath = "bios/3ds/$name",
        fullPath = "/romm/library/bios/3ds/$name",
        fileSizeBytes = 1024,
        md5Hash = null,
        sha1Hash = null,
        missingFromFs = missing
    )

    private fun row(rommId: Long, platformId: Long, name: String, localPath: String? = null) = FirmwareEntity(
        id = rommId,
        platformId = platformId,
        platformSlug = if (platformId == 3L) "3ds" else "ps1",
        rommId = rommId,
        fileName = name,
        filePath = "bios/$name",
        fileSizeBytes = 1024,
        md5Hash = null,
        sha1Hash = null,
        localPath = localPath,
        downloadedAt = localPath?.let { Instant.EPOCH },
        lastVerifiedAt = null
    )

    @Test
    fun `an empty server list clears only that platform's rows`() = runBlocking {
        rows += row(10, 3, "firmware.bin")
        rows += row(20, 1, "scph5501.bin")

        repository.syncPlatformFirmware(3, "3ds", emptyList())

        assertEquals(listOf(20L), rows.map { it.rommId })
    }

    @Test
    fun `firmware missing from the server filesystem is dropped and never offered`() = runBlocking {
        rows += row(10, 3, "firmware.bin")

        repository.syncPlatformFirmware(
            3,
            "3ds",
            listOf(remote(10, "firmware.bin", missing = true), remote(11, "aes_keys.txt"))
        )

        assertEquals(listOf("aes_keys.txt"), rows.map { it.fileName })
    }

    @Test
    fun `a platform whose only firmware is missing ends up with no rows`() = runBlocking {
        rows += row(10, 3, "firmware.bin")

        repository.syncPlatformFirmware(3, "3ds", listOf(remote(10, "firmware.bin", missing = true)))

        assertEquals(emptyList<FirmwareEntity>(), rows)
    }

    @Test
    fun `a normal list keeps download state and prunes removed entries`() = runBlocking {
        rows += row(10, 3, "firmware.bin")
        rows += row(11, 3, "aes_keys.txt", localPath = "/bios/3ds/aes_keys.txt")

        repository.syncPlatformFirmware(3, "3ds", listOf(remote(11, "aes_keys.txt"), remote(12, "seeddb.bin")))

        assertEquals(listOf(11L, 12L), rows.map { it.rommId }.sorted())
        assertEquals("/bios/3ds/aes_keys.txt", rows.first { it.rommId == 11L }.localPath)
        assertNull(rows.first { it.rommId == 12L }.localPath)
    }
}
