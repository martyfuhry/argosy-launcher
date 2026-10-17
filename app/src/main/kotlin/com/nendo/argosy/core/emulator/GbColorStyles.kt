package com.nendo.argosy.core.emulator

/**
 * Stored tokens for the Game Boy platform's color style. They are written to
 * `platform_libretro_settings` and compared at launch.
 */
object GbColorStyles {
    const val PLATFORM_SLUG = "gb"

    const val ORIGINAL = "original"
    const val GBC = "gbc"
    const val SGB = "sgb"
    const val SGB_NOFRAME = "sgb_noframe"
    const val CUSTOM = "custom"

    val ALL: List<String> = listOf(ORIGINAL, GBC, SGB, SGB_NOFRAME, CUSTOM)

    /**
     * ARGB shades, lightest first, of the four-tone ramp a style renders an
     * original Game Boy game through. Palette-per-game styles return one
     * representative palette: the hardware default for a title without a preset.
     */
    fun previewShades(style: String): List<Long>? = when (style) {
        ORIGINAL -> listOf(0xFF9BBC0FL, 0xFF8BAC0FL, 0xFF306230L, 0xFF0F380FL)
        GBC -> listOf(0xFFFFFFFFL, 0xFF7BFF31L, 0xFF0063C5L, 0xFF000000L)
        SGB, SGB_NOFRAME -> listOf(0xFFF8E8C8L, 0xFFD89048L, 0xFFA82820L, 0xFF301850L)
        else -> null
    }
}
