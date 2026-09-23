package com.nendo.argosy.ui.components

import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.nendo.argosy.domain.model.CustomGridConfig
import com.nendo.argosy.domain.model.HomeLayoutSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CustomGridMatchedColumnsTest {

    private val wide = IntSize(1800, 800)

    @Test
    fun `a cap narrows the columns and centres the block`() {
        val free = customGridMetrics(wide, laneCount = 3, gapPx = 8f)
        val capped = customGridMetrics(wide, laneCount = 3, gapPx = 8f, maxColumns = 4)

        assertTrue(free.columns > 4)
        assertEquals(4, capped.columns)
        assertEquals(free.cellPx, capped.cellPx, 0.001f)
        assertTrue(capped.offsetXPx > free.offsetXPx)
    }

    @Test
    fun `a cap above what fits changes nothing`() {
        val free = customGridMetrics(wide, laneCount = 3, gapPx = 8f)

        assertEquals(free, customGridMetrics(wide, laneCount = 3, gapPx = 8f, maxColumns = free.columns + 5))
    }

    @Test
    fun `a narrower peer screen yields fewer columns than the wide one draws`() {
        val ownScreen = DpSize(960.dp, 540.dp)
        val ownGrid = DpSize(920.dp, 440.dp)
        val peer = DpSize(620.dp, 540.dp)

        val peerColumns = matchedGridColumns(ownScreen, ownGrid, peer, laneCount = 3, gap = 8.dp)
        val ownColumns = customGridMetrics(IntSize(920, 440), 3, 8f).columns

        assertTrue(peerColumns!! < ownColumns)
    }

    @Test
    fun `an equal peer screen yields the same columns`() {
        val screen = DpSize(960.dp, 540.dp)
        val grid = DpSize(920.dp, 440.dp)

        assertEquals(
            customGridMetrics(IntSize(920, 440), 3, 8f).columns,
            matchedGridColumns(screen, grid, screen, laneCount = 3, gap = 8.dp)
        )
    }

    @Test
    fun `a portrait grid on either side takes no cap`() {
        val landscape = DpSize(960.dp, 540.dp)
        val portrait = DpSize(540.dp, 960.dp)

        assertNull(matchedGridColumns(portrait, DpSize(500.dp, 860.dp), landscape, 3, 8.dp))
        assertNull(matchedGridColumns(landscape, DpSize(920.dp, 440.dp), portrait, 3, 8.dp))
    }

    @Test
    fun `matching screens is on by default and survives a round trip when off`() {
        assertTrue(HomeLayoutSettings.fromJson(null).customGrid.matchOtherScreen)
        val off = HomeLayoutSettings(customGrid = CustomGridConfig(matchOtherScreen = false))

        assertFalse(HomeLayoutSettings.fromJson(off.toJson()).customGrid.matchOtherScreen)
    }
}
