package com.nendo.argosy.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

class PresentationStyleTest {

    @Test
    fun `a style survives a round trip`() {
        val style = PresentationStyle(
            scrim = PresentationScrim.BLUR,
            scrimStrength = 40,
            art = PresentationArt.TITLE,
            stats = setOf(PresentationStat.PLAY_TIME, PresentationStat.ACHIEVEMENTS)
        )

        assertEquals(style, PresentationStyle.fromJson(style.toJson()))
    }

    @Test
    fun `nothing stored reads back as the shipped view`() {
        val style = PresentationStyle.fromJson(null)

        assertEquals(PresentationScrim.GRADIENT, style.scrim)
        assertEquals(PresentationArt.COVER, style.art)
        assertEquals(
            setOf(PresentationStat.DEVELOPER, PresentationStat.RELEASE_YEAR, PresentationStat.GENRE),
            style.stats
        )
    }

    @Test
    fun `an unknown stat is dropped and the rest are kept`() {
        val stored = """{"stats":["GENRE","SOMETHING_NEWER"],"art":"BOX_3D"}"""

        val style = PresentationStyle.fromJson(stored)

        assertEquals(setOf(PresentationStat.GENRE), style.stats)
        assertEquals(PresentationArt.BOX_3D, style.art)
        assertEquals(PresentationScrim.GRADIENT, style.scrim)
    }

    @Test
    fun `an empty stat list stays empty instead of reverting to the defaults`() {
        val style = PresentationStyle.fromJson(PresentationStyle(stats = emptySet()).toJson())

        assertEquals(emptySet<PresentationStat>(), style.stats)
    }

    @Test
    fun `a strength outside the range is clamped`() {
        assertEquals(PRESENTATION_SCRIM_MAX, PresentationStyle.fromJson("""{"scrimStrength":250}""").scrimStrength)
        assertEquals(PRESENTATION_SCRIM_MIN, PresentationStyle.fromJson("""{"scrimStrength":-5}""").scrimStrength)
    }
}
