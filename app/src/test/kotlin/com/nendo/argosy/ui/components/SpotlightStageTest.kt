package com.nendo.argosy.ui.components

import com.nendo.argosy.domain.model.HomeLayoutKind
import com.nendo.argosy.domain.model.HomeLayoutSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SpotlightStageTest {

    @Test
    fun `games inside the reach rest at their own distance`() {
        assertEquals(-2f, spotlightRestingPosition(-2, reach = 2))
        assertEquals(-1f, spotlightRestingPosition(-1, reach = 2))
        assertEquals(0f, spotlightRestingPosition(0, reach = 2))
        assertEquals(1f, spotlightRestingPosition(1, reach = 2))
        assertEquals(2f, spotlightRestingPosition(2, reach = 2))
    }

    @Test
    fun `a game left behind by a long jump slides one step toward its side`() {
        assertEquals(-1f, spotlightRestingPosition(-7, reach = 2))
        assertEquals(1f, spotlightRestingPosition(5, reach = 1))
    }

    @Test
    fun `only the focused position is fully visible and a step away is hidden`() {
        assertEquals(1f, spotlightAlphaAt(0f))
        assertEquals(0f, spotlightAlphaAt(1f))
        assertEquals(0f, spotlightAlphaAt(-1f))
        assertEquals(0f, spotlightAlphaAt(2f))
    }

    @Test
    fun `the outgoing game is gone before it finishes its step`() {
        assertEquals(0f, spotlightAlphaAt(SPOTLIGHT_FADE_REACH))
        assertTrue(spotlightAlphaAt(SPOTLIGHT_FADE_REACH / 2) in 0.49f..0.51f)
    }

    @Test
    fun `spotlight lists only its own fields`() {
        val fields = homeLayoutFieldsFor(HomeLayoutKind.SPOTLIGHT)

        assertEquals(
            listOf(
                HomeLayoutSettingField.SPOTLIGHT_BOX_ART,
                HomeLayoutSettingField.SPOTLIGHT_PLATFORM_BADGE
            ),
            fields
        )
        fields.forEach { field ->
            assertTrue(homeLayoutFieldsFor(HomeLayoutKind.CAROUSEL).none { it == field })
        }
    }

    @Test
    fun `spotlight fields change the spotlight and leave the carousel alone`() {
        val settings = HomeLayoutSettings()

        val boxArtOn = adjustHomeLayoutField(settings, HomeLayoutSettingField.SPOTLIGHT_BOX_ART, 1)
        val badgeOff = toggleHomeLayoutField(settings, HomeLayoutSettingField.SPOTLIGHT_PLATFORM_BADGE)

        assertTrue(boxArtOn.spotlight.useBoxArt)
        assertEquals(settings.carousel, boxArtOn.carousel)
        assertFalse(badgeOff.spotlight.showPlatformBadge)
        assertEquals(settings.carousel, badgeOff.carousel)
    }
}
