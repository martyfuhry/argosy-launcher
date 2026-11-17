package com.nendo.argosy.ui.dualscreen.dashboard

import com.nendo.argosy.data.emulator.MinarchLinkState
import com.nendo.argosy.hardware.CompanionInGameState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class MinarchLinkSessionControlsTest {

    private val preview = File("/storage/emulated/0/MinarchLink/.userdata/shared/.minui/GBA/Game.gba.2.bmp")

    @Test
    fun `minarch states become read-only dashboard states labelled by menu slot`() {
        val controls = minarchLinkSessionControlsOf(
            listOf(
                MinarchLinkState(slotNumber = 2, savedAtMillis = 2_000L, previewFile = preview),
                MinarchLinkState(slotNumber = 0, savedAtMillis = 1_000L, previewFile = null)
            )
        )

        assertTrue(controls.externalStates)
        assertFalse(controls.screenshotAvailable)
        assertEquals(
            listOf(
                DashboardQuickState(2, 2_000L, preview.absolutePath, menuSlot = 3),
                DashboardQuickState(0, 1_000L, null, menuSlot = 1)
            ),
            controls.quickStates
        )
    }

    @Test
    fun `external states are offered without quick actions but never in hardcore`() {
        val external = minarchLinkSessionControlsOf(emptyList())
        val noActions = CompanionInGameState(quickActionsAvailable = false)

        assertTrue(external.offersStates(noActions))
        assertFalse(external.offersStates(noActions.copy(isHardcore = true)))
        assertFalse(SessionControls().offersStates(noActions))
        assertTrue(SessionControls().offersStates(noActions.copy(quickActionsAvailable = true)))
    }
}
