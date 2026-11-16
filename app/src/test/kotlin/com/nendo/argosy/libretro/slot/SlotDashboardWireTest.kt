package com.nendo.argosy.libretro.slot

import android.os.Bundle
import com.nendo.argosy.DualScreenManager
import com.nendo.argosy.ui.dualscreen.dashboard.DashboardQuickState
import com.nendo.argosy.ui.dualscreen.dashboard.SessionControls
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SlotDashboardWireTest {

    private fun mapBundle(): Bundle {
        val values = mutableMapOf<String, Any?>()
        val bundle = mockk<Bundle>()
        every { bundle.putInt(any(), any()) } answers { values[firstArg<String>()] = secondArg<Int>() }
        every { bundle.putLong(any(), any()) } answers { values[firstArg<String>()] = secondArg<Long>() }
        every { bundle.putBoolean(any(), any()) } answers { values[firstArg<String>()] = secondArg<Boolean>() }
        every { bundle.putIntArray(any(), any()) } answers { values[firstArg<String>()] = secondArg<IntArray?>() }
        every { bundle.putLongArray(any(), any()) } answers { values[firstArg<String>()] = secondArg<LongArray?>() }
        every { bundle.putStringArrayList(any(), any()) } answers {
            values[firstArg<String>()] = secondArg<ArrayList<String>?>()
        }
        every { bundle.getInt(any(), any()) } answers { values[firstArg<String>()] as Int? ?: secondArg<Int>() }
        every { bundle.getLong(any(), any()) } answers { values[firstArg<String>()] as Long? ?: secondArg<Long>() }
        every { bundle.getBoolean(any(), any()) } answers {
            values[firstArg<String>()] as Boolean? ?: secondArg<Boolean>()
        }
        every { bundle.getIntArray(any()) } answers { values[firstArg<String>()] as IntArray? }
        every { bundle.getLongArray(any()) } answers { values[firstArg<String>()] as LongArray? }
        @Suppress("UNCHECKED_CAST")
        every { bundle.getStringArrayList(any()) } answers {
            values[firstArg<String>()] as ArrayList<String>?
        }
        return bundle
    }

    @Test
    fun `controls survive the trip between processes`() {
        val controls = SessionControls(
            quickStates = listOf(
                DashboardQuickState(slotNumber = 1, savedAtMillis = 1_000L, screenshotPath = "/states/1.png"),
                DashboardQuickState(slotNumber = 2, savedAtMillis = 2_000L, screenshotPath = null)
            ),
            cheatsAvailable = true,
            settingsAvailable = true,
            screenshotAvailable = false,
            discSwapAvailable = true,
            statesAvailable = false,
            achievementsAvailable = false
        )
        val bundle = mapBundle()

        SlotDashboardWire.writeControls(controls, bundle)

        assertEquals(controls, SlotDashboardWire.readControls(bundle))
    }

    @Test
    fun `an empty bundle reads as the default controls`() {
        assertEquals(SessionControls(), SlotDashboardWire.readControls(mapBundle()))
    }

    @Test
    fun `every command reaches the matching action with its arguments`() {
        DashboardCommand.entries.forEach { command ->
            val actions = mockk<DualScreenManager.SessionQuickActions>(relaxed = true)
            val bundle = mapBundle()
            SlotDashboardWire.writeCommand(command, stateSlot = 5, savedAtMillis = 500L, into = bundle)

            assertTrue(SlotDashboardWire.runCommand(bundle, actions))

            when (command) {
                DashboardCommand.QUICK_SAVE -> verify(exactly = 1) { actions.quickSave() }
                DashboardCommand.LOAD_STATE -> verify(exactly = 1) { actions.loadState(5, 500L) }
                DashboardCommand.SCREENSHOT -> verify(exactly = 1) { actions.screenshot() }
                DashboardCommand.OPEN_CHEATS -> verify(exactly = 1) { actions.openCheats() }
                DashboardCommand.OPEN_SETTINGS -> verify(exactly = 1) { actions.openGameSettings() }
                DashboardCommand.QUIT -> verify(exactly = 1) { actions.quit() }
            }
        }
    }

    @Test
    fun `an unknown command runs nothing`() {
        val actions = mockk<DualScreenManager.SessionQuickActions>(relaxed = true)
        val bundle = mapBundle()
        bundle.putInt(SlotProtocol.KEY_COMMAND, DashboardCommand.entries.size)

        assertFalse(SlotDashboardWire.runCommand(bundle, actions))
        verify(exactly = 0) { actions.quit() }
        verify(exactly = 0) { actions.quickSave() }
    }
}
