package com.nendo.argosy.ui.dualscreen

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GameInputRoutingTest {

    @Test
    fun `a game running on the other display takes the input`() {
        assertTrue(isInputForGameOnOtherDisplay(4, 0, emptySet()))
    }

    @Test
    fun `a companion resumed over the game's display takes the input back`() {
        assertFalse(isInputForGameOnOtherDisplay(4, 0, setOf(4)))
    }

    @Test
    fun `a companion resumed on an unrelated display leaves the game its input`() {
        assertTrue(isInputForGameOnOtherDisplay(4, 0, setOf(7)))
    }

    @Test
    fun `a game on this activity's own display never takes the input`() {
        assertFalse(isInputForGameOnOtherDisplay(0, 0, emptySet()))
    }

    @Test
    fun `no emulator display or no own display never takes the input`() {
        assertFalse(isInputForGameOnOtherDisplay(null, 0, emptySet()))
        assertFalse(isInputForGameOnOtherDisplay(4, null, emptySet()))
    }
}
