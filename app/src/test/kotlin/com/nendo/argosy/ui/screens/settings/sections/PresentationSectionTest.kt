package com.nendo.argosy.ui.screens.settings.sections

import com.nendo.argosy.domain.model.PRESENTATION_SCRIM_MAX
import com.nendo.argosy.domain.model.PRESENTATION_SCRIM_MIN
import com.nendo.argosy.domain.model.PresentationArt
import com.nendo.argosy.domain.model.PresentationScrim
import com.nendo.argosy.domain.model.PresentationStat
import com.nendo.argosy.domain.model.PresentationStyle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PresentationSectionTest {

    @Test
    fun `the shade and art rows wrap in both directions`() {
        val style = PresentationStyle(scrim = PresentationScrim.GRADIENT, art = PresentationArt.COVER)

        assertEquals(
            PresentationScrim.NONE,
            adjustPresentationItem(style, PresentationItem.Scrim, -1)?.scrim
        )
        assertEquals(
            PresentationArt.TITLE,
            adjustPresentationItem(style, PresentationItem.Art, -1)?.art
        )
        assertEquals(
            PresentationArt.BOX_3D,
            adjustPresentationItem(style, PresentationItem.Art, 1)?.art
        )
    }

    @Test
    fun `strength steps and stops at both ends`() {
        val atTop = PresentationStyle(scrimStrength = PRESENTATION_SCRIM_MAX)
        val atBottom = PresentationStyle(scrimStrength = PRESENTATION_SCRIM_MIN)

        assertEquals(
            PRESENTATION_SCRIM_MAX,
            adjustPresentationItem(atTop, PresentationItem.ScrimStrength, 1)?.scrimStrength
        )
        assertEquals(
            PRESENTATION_SCRIM_MIN,
            adjustPresentationItem(atBottom, PresentationItem.ScrimStrength, -1)?.scrimStrength
        )
        assertEquals(
            PRESENTATION_SCRIM_MAX - 10,
            adjustPresentationItem(atTop, PresentationItem.ScrimStrength, -1)?.scrimStrength
        )
    }

    @Test
    fun `left turns a stat off and right turns it on`() {
        val item = PresentationItem.Stat(PresentationStat.PLAY_TIME)
        val off = PresentationStyle(stats = emptySet())

        val on = adjustPresentationItem(off, item, 1)
        assertTrue(on!!.shows(PresentationStat.PLAY_TIME))
        assertFalse(adjustPresentationItem(on, item, -1)!!.shows(PresentationStat.PLAY_TIME))
        assertTrue(adjustPresentationItem(on, item, 1)!!.shows(PresentationStat.PLAY_TIME))
    }

    @Test
    fun `headers take no adjustment`() {
        val header = PresentationItem.ALL.first { it is PresentationItem.Header }

        assertNull(adjustPresentationItem(PresentationStyle(), header, 1))
    }

    @Test
    fun `strength is hidden while there is no shade`() {
        val none = PresentationStyle(scrim = PresentationScrim.NONE)
        val solid = PresentationStyle(scrim = PresentationScrim.SOLID)

        assertFalse(PresentationItem.ScrimStrength.visibleWhen(none))
        assertTrue(PresentationItem.ScrimStrength.visibleWhen(solid))
        assertEquals(presentationMaxFocusIndex(solid) - 1, presentationMaxFocusIndex(none))
    }
}
