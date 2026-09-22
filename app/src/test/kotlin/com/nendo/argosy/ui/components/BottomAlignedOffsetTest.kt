package com.nendo.argosy.ui.components

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * A row scrolled into view from below lands against the safe bottom edge, so the rows above it
 * stay where the eye left them; a row too tall for the band falls back to the top edge.
 */
class BottomAlignedOffsetTest {

    @Test
    fun `a row that fits lands with its bottom on the safe edge`() {
        assertEquals(-(1000 - 0 - 200), bottomAlignedOffset(viewportEnd = 1000, bottomInset = 0, itemExtent = 200))
    }

    @Test
    fun `a footer inset lifts the row clear of the chrome`() {
        assertEquals(-(1000 - 120 - 200), bottomAlignedOffset(viewportEnd = 1000, bottomInset = 120, itemExtent = 200))
    }

    @Test
    fun `a row taller than the band aligns to the top instead`() {
        assertEquals(0, bottomAlignedOffset(viewportEnd = 1000, bottomInset = 120, itemExtent = 1200))
    }

    @Test
    fun `a row exactly the height of the band sits flush`() {
        assertEquals(0, bottomAlignedOffset(viewportEnd = 1000, bottomInset = 120, itemExtent = 880))
    }
}
