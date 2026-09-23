package com.nendo.argosy.data.remote.romm

import org.junit.Assert.assertEquals
import org.junit.Test

class RomMPlatformNamesTest {

    private fun platform(slug: String, fsSlug: String?, name: String, displayName: String? = null) =
        RomMPlatform(id = 1, slug = slug, name = name, fsSlug = fsSlug, romCount = 0, displayName = displayName)

    private fun names(slug: String, fsSlug: String?, name: String, displayName: String? = null) =
        platform(slug, fsSlug, name, displayName).resolvePlatformNames(slug)

    @Test
    fun `known slug keeps its short name when the folder starts with an alias`() {
        assertEquals("PS2", names("ps2", "Playstation 2", "PlayStation 2").second)
        assertEquals("PS3", names("ps3", "Playstation 3", "PlayStation 3").second)
        assertEquals("GBA", names("gba", "Gameboy Advance", "Game Boy Advance").second)
        assertEquals("GBC", names("gbc", "Gameboy Color", "Game Boy Color").second)
    }

    @Test
    fun `known slug is never renamed from its folder`() {
        assertEquals("PlayStation 2", names("ps2", "Playstation 2", "PlayStation 2").first)
        assertEquals("Game Boy Advance", names("gba", "Gameboy Advance", "Game Boy Advance").first)
        assertEquals("Sony PS2", names("ps2", "Playstation 2", "PlayStation 2", "Sony PS2").first)
    }

    @Test
    fun `known slug with a plain folder name keeps its short name`() {
        assertEquals("PS1", names("psx", "Playstation", "PlayStation").second)
        assertEquals("3DS", names("3ds", "Nintendo 3DS", "Nintendo 3DS").second)
        assertEquals("TG16", names("tg16", "TurboGrafx-16", "TurboGrafx-16").second)
    }

    @Test
    fun `unknown slug still derives a sub-platform name`() {
        assertEquals("PS1 Hacks" to "PS1 Hacks", names("psx-hacks", "psx-hacks", "psx-hacks"))
    }

    @Test
    fun `unknown slug still derives a sub-platform name from its folder`() {
        assertEquals("PS1 Hacks" to "PS1 Hacks", names("custom", "psx-hacks", "custom"))
    }
}
