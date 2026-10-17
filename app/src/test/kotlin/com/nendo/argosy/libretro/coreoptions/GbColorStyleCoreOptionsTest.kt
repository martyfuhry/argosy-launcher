package com.nendo.argosy.libretro.coreoptions

import com.nendo.argosy.core.emulator.GbColorStyles
import com.nendo.argosy.libretro.coreoptions.manifests.GambatteManifest
import com.nendo.argosy.libretro.coreoptions.manifests.MgbaManifest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GbColorStyleCoreOptionsTest {

    @Test
    fun `mgba original forces the monochrome model without borders or presets`() {
        assertEquals(
            listOf(
                "mgba_gb_model" to "Game Boy",
                "mgba_sgb_borders" to "OFF",
                "mgba_gb_colors_preset" to "0"
            ),
            GbColorStyleCoreOptions.forCore(GbColorStyles.ORIGINAL, "mgba")
        )
    }

    @Test
    fun `mgba gbc forces the color model and its per-game presets`() {
        assertEquals(
            listOf(
                "mgba_gb_model" to "Game Boy Color",
                "mgba_sgb_borders" to "OFF",
                "mgba_gb_colors_preset" to "1"
            ),
            GbColorStyleCoreOptions.forCore(GbColorStyles.GBC, "mgba")
        )
    }

    @Test
    fun `mgba sgb styles differ only in the border`() {
        assertEquals(
            listOf(
                "mgba_gb_model" to "Super Game Boy",
                "mgba_sgb_borders" to "ON",
                "mgba_gb_colors_preset" to "2"
            ),
            GbColorStyleCoreOptions.forCore(GbColorStyles.SGB, "mgba")
        )
        assertEquals(
            listOf(
                "mgba_gb_model" to "Super Game Boy",
                "mgba_sgb_borders" to "OFF",
                "mgba_gb_colors_preset" to "2"
            ),
            GbColorStyleCoreOptions.forCore(GbColorStyles.SGB_NOFRAME, "mgba")
        )
    }

    @Test
    fun `gambatte original selects the green built-in palette`() {
        assertEquals(
            listOf(
                "gambatte_gb_colorization" to "internal",
                "gambatte_gb_internal_palette" to "GB - DMG"
            ),
            GbColorStyleCoreOptions.forCore(GbColorStyles.ORIGINAL, "gambatte")
        )
    }

    @Test
    fun `gambatte gbc and sgb map to the colorization modes`() {
        assertEquals(
            listOf("gambatte_gb_colorization" to "GBC"),
            GbColorStyleCoreOptions.forCore(GbColorStyles.GBC, "gambatte")
        )
        assertEquals(
            listOf("gambatte_gb_colorization" to "SGB"),
            GbColorStyleCoreOptions.forCore(GbColorStyles.SGB, "gambatte")
        )
        assertEquals(
            GbColorStyleCoreOptions.forCore(GbColorStyles.SGB, "gambatte"),
            GbColorStyleCoreOptions.forCore(GbColorStyles.SGB_NOFRAME, "gambatte")
        )
    }

    @Test
    fun `custom pins nothing on either core`() {
        assertTrue(GbColorStyleCoreOptions.forCore(GbColorStyles.CUSTOM, "mgba").isEmpty())
        assertTrue(GbColorStyleCoreOptions.forCore(GbColorStyles.CUSTOM, "gambatte").isEmpty())
    }

    @Test
    fun `cores without a mapping receive nothing`() {
        GbColorStyles.ALL.forEach { style ->
            assertTrue(GbColorStyleCoreOptions.forCore(style, "vbam").isEmpty())
        }
    }

    @Test
    fun `every pinned value exists in the core manifest`() {
        val manifests = mapOf("mgba" to MgbaManifest, "gambatte" to GambatteManifest)
        manifests.forEach { (coreId, manifest) ->
            val defs = manifest.options.associateBy { it.key }
            GbColorStyles.ALL.forEach { style ->
                GbColorStyleCoreOptions.forCore(style, coreId).forEach { (key, value) ->
                    val def = defs[key]
                    assertTrue("$coreId lacks option $key", def != null)
                    assertTrue("$coreId $key lacks value $value", value in def!!.values)
                }
            }
        }
    }
}
