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
    fun `it is recommended for gba only after every dedicated upstream emulator`() {
        val gba = EmulatorRegistry.getRecommendedEmulators().getValue("gba")
        val minarch = gba.indexOf("minarch_link")
        val dedicated = gba.filterNot { it == "minarch_link" || it.startsWith("retroarch") }

        assertEquals(EmulatorRegistry.BUILTIN_ID, gba.first())
        assertTrue(minarch > gba.indexOf("linkboy"))
        assertTrue(dedicated.all { gba.indexOf(it) < minarch })
    }

    @Test
    fun `saves resolve to the shared MinarchLink folder as sav files`() {
        val config = SavePathRegistry.getConfigForPlatformByPackage("farm.fuhry.minarch", "gba")

        assertEquals("minarch_link", config?.emulatorId)
        assertEquals(listOf("sav"), config!!.saveExtensions)
        assertEquals(listOf("{extStorage}/MinarchLink/Saves/GBA"), config.defaultPaths)
    }

    @Test
    fun `states resolve to the shared MinarchLink folder as name and slot files`() {
        val config = StatePathRegistry.getConfig("minarch_link")
        requireNotNull(config)
        val pattern = config.slotPattern
        val rom = "Pokemon - LeafGreen Version (USA, Europe) (Rev 1)"

        assertEquals(listOf("{extStorage}/MinarchLink/States/GBA"), config.defaultPaths)
        assertEquals(10, config.maxSlots)
        assertEquals("${rom}_3.state", pattern.buildFileName(rom, 3))
        assertEquals(0, pattern.parseSlotNumber("${rom}_0.state", rom))
        assertEquals(9, pattern.parseSlotNumber("${rom}_9.state", rom))
        assertEquals(null, pattern.parseSlotNumber("$rom.sav", rom))
    }

    @Test
    fun `a forked build resolves through the family to the same save config`() {
        val family = EmulatorRegistry.findFamilyForPackage("farm.fuhry.minarch.thor")
        requireNotNull(family)
        val variant = EmulatorRegistry.createDefFromFamily(family, "farm.fuhry.minarch.thor")

        assertEquals("minarch_link", family.baseId)
        assertEquals(setOf("gba"), variant.supportedPlatforms)
        assertEquals("minarch_link", SavePathRegistry.canonicalConfigId(variant.id, variant.packageName))
        assertEquals("minarch_link", SavePathRegistry.getConfigForPlatform(variant.id, "gba")?.emulatorId)
        assertEquals("minarch_link", StatePathRegistry.getConfig(variant.id)?.emulatorId)
    }
}
