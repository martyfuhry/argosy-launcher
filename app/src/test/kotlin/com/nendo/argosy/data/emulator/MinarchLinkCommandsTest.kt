package com.nendo.argosy.data.emulator

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MinarchLinkCommandsTest {

    @Test
    fun `the contract matches Minarch Link's StateCommands and the registry package`() {
        assertEquals("farm.fuhry.minarch.action.SAVE_STATE", MinarchStateOp.SAVE.action)
        assertEquals("farm.fuhry.minarch.action.LOAD_STATE", MinarchStateOp.LOAD.action)
        assertEquals("farm.fuhry.minarch.action.STATE_RESULT", MinarchLinkCommands.ACTION_STATE_RESULT)
        assertEquals("save", MinarchStateOp.SAVE.token)
        assertEquals("load", MinarchStateOp.LOAD.token)
        assertEquals(MinarchLinkStates.EMULATOR_ID, EmulatorRegistry.getByPackage(MinarchLinkCommands.PACKAGE)?.id)
    }

    @Test
    fun `menu slots are 0 to 7`() {
        assertTrue(MinarchLinkCommands.isMenuSlot(0))
        assertTrue(MinarchLinkCommands.isMenuSlot(7))
        assertFalse(MinarchLinkCommands.isMenuSlot(-1))
        assertFalse(MinarchLinkCommands.isMenuSlot(8))
        assertFalse(MinarchLinkCommands.isMenuSlot(9))
    }

    @Test
    fun `a save takes the first empty slot`() {
        assertEquals(0, MinarchLinkCommands.nextSaveSlot(emptyMap()))
        assertEquals(1, MinarchLinkCommands.nextSaveSlot(mapOf(0 to 5L, 2 to 9L)))
        assertEquals(7, MinarchLinkCommands.nextSaveSlot((0..6).associateWith { 10L }))
    }

    @Test
    fun `a save with every slot used overwrites the one saved longest ago`() {
        val full = (0..7).associateWith { 100L + it }.toMutableMap().apply { put(5, 1L) }

        assertEquals(5, MinarchLinkCommands.nextSaveSlot(full))
    }

    @Test
    fun `a result parses into the command it answers`() {
        assertEquals(
            MinarchStateResult(MinarchStateCommand(MinarchStateOp.SAVE, 3), ok = true, reason = null),
            MinarchLinkCommands.parseResult("save", 3, ok = true, reason = null)
        )
        assertEquals(
            MinarchStateResult(MinarchStateCommand(MinarchStateOp.LOAD, 0), ok = false, reason = "linked"),
            MinarchLinkCommands.parseResult("load", 0, ok = false, reason = "linked")
        )
    }

    @Test
    fun `a successful result drops any reason, and malformed results are ignored`() {
        assertNull(MinarchLinkCommands.parseResult("save", 1, ok = true, reason = "write")?.reason)
        assertNull(MinarchLinkCommands.parseResult("quit", 1, ok = true, reason = null))
        assertNull(MinarchLinkCommands.parseResult(null, 1, ok = true, reason = null))
        assertNull(MinarchLinkCommands.parseResult("load", -1, ok = true, reason = null))
        assertNull(MinarchLinkCommands.parseResult("load", 8, ok = true, reason = null))
    }
}
