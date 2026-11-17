package com.nendo.argosy.data.emulator

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class MinarchLinkStatesTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val rom = "Pokemon - LeafGreen Version (USA, Europe) (Rev 1).gba"
    private val stem = "Pokemon - LeafGreen Version (USA, Europe) (Rev 1)"

    @Test
    fun `only the Minarch Link package is recognised`() {
        assertTrue(MinarchLinkStates.isMinarchLink("farm.fuhry.minarch"))
        assertFalse(MinarchLinkStates.isMinarchLink("com.retroarch"))
        assertFalse(MinarchLinkStates.isMinarchLink(EmulatorRegistry.BUILTIN_PACKAGE))
        assertFalse(MinarchLinkStates.isMinarchLink(null))
    }

    @Test
    fun `the states folder is the one the state registry names`() {
        val root = File("/storage/emulated/0")
        val registered = requireNotNull(StatePathRegistry.getConfig(MinarchLinkStates.EMULATOR_ID)).defaultPaths.single()

        assertEquals(registered.replace("{extStorage}", root.path), MinarchLinkStates.statesDir(root).path)
        assertEquals(
            "/storage/emulated/0/MinarchLink/.userdata/shared/.minui/GBA",
            MinarchLinkStates.previewDir(root).path
        )
    }

    @Test
    fun `the state stem drops only the last extension and previews keep it`() {
        assertEquals(stem, MinarchLinkStates.stateStem(rom))
        assertEquals("Dr. Mario", MinarchLinkStates.stateStem("Dr. Mario.gba"))
        assertEquals("NoExtension", MinarchLinkStates.stateStem("NoExtension"))
        assertEquals("$rom.3.bmp", MinarchLinkStates.previewFileName(rom, 3))
    }

    @Test
    fun `menu slots are labelled from one`() {
        assertEquals(1, MinarchLinkStates.menuSlotLabel(0))
        assertEquals(8, MinarchLinkStates.menuSlotLabel(7))
    }

    @Test
    fun `lists the rom's menu slots newest first with their previews`() {
        val root = tmp.root
        writeState("$stem.state", 1_000L)
        writeState("$stem.state3", 3_000L)
        writeState("$stem.state7", 2_000L)
        writePreview("$rom.3.bmp")

        val states = MinarchLinkStates.list(root, rom)

        assertEquals(listOf(3, 7, 0), states.map { it.slotNumber })
        assertEquals(listOf(3_000L, 2_000L, 1_000L), states.map { it.savedAtMillis })
        assertEquals(File(MinarchLinkStates.previewDir(root), "$rom.3.bmp"), states[0].previewFile)
        assertNull(states[1].previewFile)
        assertNull(states[2].previewFile)
    }

    @Test
    fun `leaves out the auto-resume state, slots past the menu, other roms and stray files`() {
        writeState("$stem.state.auto", 5_000L)
        writeState("$stem.state9", 5_000L)
        writeState("$stem.state8", 5_000L)
        writeState("$stem (Hack).state1", 5_000L)
        writeState("$stem.state1.tmp", 5_000L)
        writeState("$stem.sav", 5_000L)
        writeState("$stem.state2", 4_000L)

        val states = MinarchLinkStates.list(tmp.root, rom)

        assertEquals(listOf(2), states.map { it.slotNumber })
    }

    @Test
    fun `a missing states folder lists nothing`() {
        assertTrue(MinarchLinkStates.list(tmp.root, rom).isEmpty())
    }

    private fun writeState(name: String, modifiedAt: Long) {
        val dir = MinarchLinkStates.statesDir(tmp.root).apply { mkdirs() }
        File(dir, name).apply {
            writeBytes(byteArrayOf(1))
            setLastModified(modifiedAt)
        }
    }

    private fun writePreview(name: String) {
        val dir = MinarchLinkStates.previewDir(tmp.root).apply { mkdirs() }
        File(dir, name).writeBytes(byteArrayOf(1))
    }
}
