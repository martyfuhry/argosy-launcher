package com.nendo.argosy.data.emulator

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MinarchLinkRegistryTest {

    @Test
    fun `the package is recognised as Minarch Link for gba only`() {
        val def = EmulatorRegistry.getByPackage("farm.fuhry.minarch")

        assertEquals("minarch_link", def?.id)
        assertEquals(setOf("gba"), def?.supportedPlatforms)
        assertTrue(EmulatorRegistry.getForPlatform("gba").any { it.id == "minarch_link" })
        assertFalse(EmulatorRegistry.getForPlatform("gb").any { it.id == "minarch_link" })
        assertFalse(EmulatorRegistry.getForPlatform("gbc").any { it.id == "minarch_link" })
    }

    @Test
    fun `it is offered for gba after the existing emulators`() {
        val gba = EmulatorRegistry.getRecommendedEmulators().getValue("gba")

        assertEquals(EmulatorRegistry.BUILTIN_ID, gba.first())
        assertTrue("minarch_link" in gba)
    }

    @Test
    fun `saves resolve to the shared MinarchLink folder as sav files`() {
        val config = SavePathRegistry.getConfigForPlatformByPackage("farm.fuhry.minarch", "gba")

        assertEquals("minarch_link", config?.emulatorId)
        assertEquals(listOf("sav"), config!!.saveExtensions)
        assertEquals(listOf("{extStorage}/MinarchLink/Saves/GBA"), config.defaultPaths)
    }

    @Test
    fun `states resolve to the shared MinarchLink folder with an auto-resume slot`() {
        val config = StatePathRegistry.getConfig("minarch_link")
        requireNotNull(config)
        val pattern = config.slotPattern
        val rom = "Pokemon - LeafGreen Version (USA, Europe) (Rev 1)"

        assertEquals(listOf("{extStorage}/MinarchLink/States/GBA"), config.defaultPaths)
        assertEquals(10, config.maxSlots)
        assertEquals("$rom.state", pattern.buildFileName(rom, 0))
        assertEquals("$rom.state3", pattern.buildFileName(rom, 3))
        assertEquals("$rom.state.auto", pattern.buildFileName(rom, -1))
        assertEquals(0, pattern.parseSlotNumber("$rom.state", rom))
        assertEquals(7, pattern.parseSlotNumber("$rom.state7", rom))
        assertEquals(-1, pattern.parseSlotNumber("$rom.state.auto", rom))
        assertEquals(null, pattern.parseSlotNumber("$rom.sav", rom))
        assertEquals(null, pattern.parseSlotNumber("$rom.state.auto.tmp", rom))
        assertEquals(null, pattern.parseSlotNumber("Pokemon - LeafGreen Version (USA, Europe).state", "Pokemon - LeafGreen Version (USA, Europe) (Rev 1)"))
        assertEquals(null, pattern.parseSlotNumber("$rom (Rev 2).state", rom))
    }

    @Test
    fun `only the Minarch Link package itself is recognised`() {
        assertEquals(null, EmulatorRegistry.findFamilyForPackage("farm.fuhry.minarch.thor"))
        assertEquals("minarch_link", SavePathRegistry.canonicalConfigId("minarch_link", "farm.fuhry.minarch"))
    }
}
