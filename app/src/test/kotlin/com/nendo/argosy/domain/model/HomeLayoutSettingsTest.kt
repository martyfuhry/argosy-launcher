package com.nendo.argosy.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeLayoutSettingsTest {

    @Test
    fun `spotlight selection and its settings survive a round trip`() {
        val settings = HomeLayoutSettings(
            selected = HomeLayoutKind.SPOTLIGHT,
            spotlight = SpotlightConfig(showPlatformBadge = false, useBoxArt = true)
        )

        val restored = HomeLayoutSettings.fromJson(settings.toJson())

        assertEquals(HomeLayoutKind.SPOTLIGHT, restored.selected)
        assertEquals(SpotlightConfig(showPlatformBadge = false, useBoxArt = true), restored.spotlight)
        assertEquals(restored.spotlight, restored.active)
    }

    @Test
    fun `spotlight settings stay apart from the carousel's`() {
        val settings = HomeLayoutSettings(
            carousel = CarouselConfig(showPlatformBadge = true, useBoxArt = false),
            spotlight = SpotlightConfig(showPlatformBadge = false, useBoxArt = true)
        )

        val restored = HomeLayoutSettings.fromJson(settings.toJson())

        assertTrue(restored.carousel.showPlatformBadge)
        assertFalse(restored.carousel.useBoxArt)
        assertFalse(restored.spotlight.showPlatformBadge)
        assertTrue(restored.spotlight.useBoxArt)
    }

    @Test
    fun `settings written before spotlight existed read back with its defaults`() {
        val stored = """{"selected":"CAROUSEL","carousel":{"showPlatformBadge":false}}"""

        val restored = HomeLayoutSettings.fromJson(stored)

        assertEquals(HomeLayoutKind.CAROUSEL, restored.selected)
        assertFalse(restored.carousel.showPlatformBadge)
        assertEquals(SpotlightConfig(), restored.spotlight)
    }

    @Test
    fun `only the row layouts browse rows`() {
        assertTrue(HomeLayoutKind.CAROUSEL.browsesRows)
        assertTrue(HomeLayoutKind.SPOTLIGHT.browsesRows)
        assertFalse(HomeLayoutKind.AUTO_GRID.browsesRows)
        assertFalse(HomeLayoutKind.CUSTOM_GRID.browsesRows)
    }
}
