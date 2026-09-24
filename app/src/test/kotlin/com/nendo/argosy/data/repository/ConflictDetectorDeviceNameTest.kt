package com.nendo.argosy.data.repository

import com.nendo.argosy.data.remote.romm.RomMDeviceSync
import com.nendo.argosy.data.remote.romm.RomMSave
import org.junit.Assert.assertEquals
import org.junit.Test

class ConflictDetectorDeviceNameTest {

    private val detector = ConflictDetector()

    private fun save(originDeviceId: String?, syncs: List<RomMDeviceSync>) = RomMSave(
        id = 41L, romId = 1L, userId = 1L, emulator = "retroarch", fileName = "game.srm",
        updatedAt = "2026-09-20T12:00:00Z", originDeviceId = originDeviceId, deviceSyncs = syncs
    )

    private val syncs = listOf(
        RomMDeviceSync(deviceId = "phone", deviceName = "Phone", lastSyncedAt = "2026-09-20T12:00:00Z"),
        RomMDeviceSync(deviceId = "tablet", deviceName = "Tablet", lastSyncedAt = "2026-09-22T09:00:00Z"),
        RomMDeviceSync(deviceId = "thor", deviceName = "Thor", lastSyncedAt = "2026-09-23T09:00:00Z")
    )

    @Test
    fun `names the device that created the server save over the device that synced it last`() {
        assertEquals("Phone", detector.extractUploaderDeviceName(save("phone", syncs), currentDeviceId = "thor"))
    }

    @Test
    fun `falls back to the latest other device when this device created the save`() {
        assertEquals("Tablet", detector.extractUploaderDeviceName(save("thor", syncs), currentDeviceId = "thor"))
    }

    @Test
    fun `falls back to the latest other device when the origin is unknown`() {
        assertEquals("Tablet", detector.extractUploaderDeviceName(save(null, syncs), currentDeviceId = "thor"))
    }
}
