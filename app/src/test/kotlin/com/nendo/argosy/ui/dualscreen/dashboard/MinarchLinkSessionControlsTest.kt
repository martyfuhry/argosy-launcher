package com.nendo.argosy.ui.dualscreen.dashboard

import com.nendo.argosy.data.emulator.MinarchLinkState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import java.io.File

class MinarchLinkSessionControlsTest {

    private val preview = File("/storage/emulated/0/MinarchLink/.userdata/shared/.minui/GBA/Game.gba.2.bmp")

    @Test
    fun `minarch states become dashboard states labelled by menu slot, with no screenshot action`() {
        val controls = minarchLinkSessionControlsOf(
            listOf(
                MinarchLinkState(slotNumber = 2, savedAtMillis = 2_000L, previewFile = preview),
                MinarchLinkState(slotNumber = 0, savedAtMillis = 1_000L, previewFile = null)
            )
        )

        assertFalse(controls.screenshotAvailable)
        assertFalse(controls.cheatsAvailable)
        assertFalse(controls.settingsAvailable)
        assertEquals(
            listOf(
                DashboardQuickState(2, 2_000L, preview.absolutePath, menuSlot = 3),
                DashboardQuickState(0, 1_000L, null, menuSlot = 1)
            ),
            controls.quickStates
        )
    }
}
