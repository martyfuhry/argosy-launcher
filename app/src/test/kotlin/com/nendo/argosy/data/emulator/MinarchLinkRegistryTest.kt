package com.nendo.argosy.data.emulator

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
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
    fun `it is offered for gba after every emulator gba already recommends`() {
        val gba = EmulatorRegistry.getRecommendedEmulators().getValue("gba")

        assertEquals(EmulatorRegistry.BUILTIN_ID, gba.first())
        assertEquals("minarch_link", gba.last())
    }

    @Test
    fun `saves resolve to the shared MinarchLink folder as sav files`() {
        val config = SavePathRegistry.getConfigForPlatformByPackage("farm.fuhry.minarch", "gba")

        requireNotNull(config)
        assertEquals("minarch_link", config.emulatorId)
        assertEquals(listOf("sav"), config.saveExtensions)
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
        assertEquals("$rom.state", pattern.targetFileName(key(rom), 0, null))
        assertEquals("$rom.state3", pattern.targetFileName(key(rom), 3, null))
        assertEquals("$rom.state.auto", pattern.targetFileName(key(rom), -1, null))
        assertEquals(0, pattern.parseSlotNumber("$rom.state", key(rom)))
        assertEquals(7, pattern.parseSlotNumber("$rom.state7", key(rom)))
        assertEquals(9, pattern.parseSlotNumber("$rom.state9", key(rom)))
        assertEquals(-1, pattern.parseSlotNumber("$rom.state.auto", key(rom)))
        assertNull(pattern.parseSlotNumber("$rom.sav", key(rom)))
        assertNull(pattern.parseSlotNumber("$rom.state.auto.tmp", key(rom)))
        assertNull(pattern.parseSlotNumber("${rom}_1.state", key(rom)))
    }

    @Test
    fun `a state belongs to a rom only when the whole name before the suffix is the rom's`() {
        val pattern = requireNotNull(StatePathRegistry.getConfig("minarch_link")).slotPattern

        assertNull(pattern.parseSlotNumber("Dr. Mario.state1", key("Mario")))
        assertNull(pattern.parseSlotNumber("Game 2.state", key("Game")))
        assertNull(pattern.parseSlotNumber("Game 2.state.auto", key("Game")))
        assertNull(pattern.parseSlotNumber("Pokemon - LeafGreen Version (USA, Europe) (Rev 1).state", key("Pokemon - LeafGreen Version (USA, Europe)")))
        assertNull(pattern.parseSlotNumber("Game.state1.auto", key("Game")))
        assertEquals(1, pattern.parseSlotNumber("Game.state1", key("Game")))
        assertEquals(-1, pattern.parseSlotNumber("Game.state.auto", key("Game")))
    }

    @Test
    fun `the gba bios goes to the folder minarch hands gpSP as its system directory`() {
        val config = BiosPathRegistry.getEmulatorBiosPaths("minarch_link")
        requireNotNull(config)

        assertEquals(1, config.defaultPaths.size)
        assertTrue(config.defaultPaths.single().endsWith("/MinarchLink/Bios/GBA"))
        assertTrue(config.actsUnprompted("gba"))
        assertFalse(config.supports("gb"))
        assertTrue(BiosPathRegistry.getUnpromptedEmulatorsForPlatform("gba").any { it.emulatorId == "minarch_link" })
    }

    private fun key(romBaseName: String) = StateNameKey(romBaseName, emptySet())
}
