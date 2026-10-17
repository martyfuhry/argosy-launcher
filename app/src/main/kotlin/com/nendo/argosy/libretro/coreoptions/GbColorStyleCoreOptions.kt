package com.nendo.argosy.libretro.coreoptions

import com.nendo.argosy.core.emulator.GbColorStyles

/**
 * Core option values a Game Boy color style pins for the built-in core that
 * runs the game. Keys and values are upstream option tokens and match the
 * core's manifest byte for byte. [GbColorStyles.CUSTOM] and unknown cores pin
 * nothing.
 */
object GbColorStyleCoreOptions {
    private const val MGBA = "mgba"
    private const val GAMBATTE = "gambatte"

    fun forCore(style: String, coreId: String): List<Pair<String, String>> = when (coreId) {
        MGBA -> when (style) {
            GbColorStyles.ORIGINAL -> listOf(
                "mgba_gb_model" to "Game Boy",
                "mgba_sgb_borders" to "OFF",
                "mgba_gb_colors_preset" to "0"
            )
            GbColorStyles.GBC -> listOf(
                "mgba_gb_model" to "Game Boy Color",
                "mgba_sgb_borders" to "OFF",
                "mgba_gb_colors_preset" to "1"
            )
            GbColorStyles.SGB -> listOf(
                "mgba_gb_model" to "Super Game Boy",
                "mgba_sgb_borders" to "ON",
                "mgba_gb_colors_preset" to "2"
            )
            GbColorStyles.SGB_NOFRAME -> listOf(
                "mgba_gb_model" to "Super Game Boy",
                "mgba_sgb_borders" to "OFF",
                "mgba_gb_colors_preset" to "2"
            )
            else -> emptyList()
        }
        GAMBATTE -> when (style) {
            GbColorStyles.ORIGINAL -> listOf(
                "gambatte_gb_colorization" to "internal",
                "gambatte_gb_internal_palette" to "GB - DMG"
            )
            GbColorStyles.GBC -> listOf("gambatte_gb_colorization" to "GBC")
            GbColorStyles.SGB, GbColorStyles.SGB_NOFRAME -> listOf("gambatte_gb_colorization" to "SGB")
            else -> emptyList()
        }
        else -> emptyList()
    }
}
