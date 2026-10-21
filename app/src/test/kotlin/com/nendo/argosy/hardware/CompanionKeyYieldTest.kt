package com.nendo.argosy.hardware

import android.view.Display
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CompanionKeyYieldTest {

    private val companionDisplay = 4

    private fun yields(
        gameActive: Boolean = true,
        showcaseRole: Boolean = false,
        emulatorDisplayId: Int? = Display.DEFAULT_DISPLAY,
        hostDisplayId: Int = companionDisplay
    ) = CompanionKeyYield.yieldsKeys(gameActive, showcaseRole, emulatorDisplayId, hostDisplayId)

    @Test
    fun `game on the other display takes the keys`() {
        assertTrue(yields())
    }

    @Test
    fun `game with no recorded display counts as the default display`() {
        assertTrue(yields(emulatorDisplayId = null))
    }

    @Test
    fun `no game keeps the window focusable`() {
        assertFalse(yields(gameActive = false))
    }

    @Test
    fun `no game with a leftover emulator display keeps the window focusable`() {
        assertFalse(yields(gameActive = false, emulatorDisplayId = Display.DEFAULT_DISPLAY))
    }

    @Test
    fun `showcase role keeps the window focusable`() {
        assertFalse(yields(showcaseRole = true))
    }

    @Test
    fun `game on the companion's own display keeps the window focusable`() {
        assertFalse(yields(emulatorDisplayId = companionDisplay))
    }
}
