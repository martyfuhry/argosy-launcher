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
    fun `it is recommended for gba after the built-in core`() {
        val gba = EmulatorRegistry.getRecommendedEmulators().getValue("gba")

        assertEquals(EmulatorRegistry.BUILTIN_ID, gba.first())
        assertEquals(1, gba.indexOf("minarch_link"))
    }

    @Test
    fun `saves resolve to the shared MinarchLink folder as sav files`() {
        val config = SavePathRegistry.getConfigForPlatformByPackage("farm.fuhry.minarch", "gba")

        assertEquals("minarch_link", config?.emulatorId)
        assertEquals(listOf("sav"), config!!.saveExtensions)
        assertEquals(listOf("{anyStorage}/MinarchLink/Saves/GBA"), config.defaultPaths)
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
    }
}
