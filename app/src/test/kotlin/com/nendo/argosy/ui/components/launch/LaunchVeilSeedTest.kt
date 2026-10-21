package com.nendo.argosy.ui.components.launch

import org.junit.Assert.assertEquals
import org.junit.Test

class LaunchVeilSeedTest {

    private val veilMs = 700
    private val tolerance = 0.0001f

    @Test
    fun veilFirstDrawnAsLaunchingBeginsStartsClear() {
        assertEquals(0f, veilStartAlpha(true, 0L, veilMs, reducedMotion = false), tolerance)
    }

    @Test
    fun veilRecreatedHalfwayCarriesOnFromHalfway() {
        assertEquals(0.5f, veilStartAlpha(true, veilMs / 2L, veilMs, reducedMotion = false), tolerance)
    }

    @Test
    fun veilRecreatedAfterTheFadeIsBlack() {
        assertEquals(1f, veilStartAlpha(true, veilMs.toLong(), veilMs, reducedMotion = false), tolerance)
        assertEquals(1f, veilStartAlpha(true, veilMs * 10L, veilMs, reducedMotion = false), tolerance)
    }

    @Test
    fun clockAheadOfTheStepStartsClear() {
        assertEquals(0f, veilStartAlpha(true, -50L, veilMs, reducedMotion = false), tolerance)
    }

    @Test
    fun reducedMotionStartsAtTheTarget() {
        assertEquals(1f, veilStartAlpha(true, 0L, veilMs, reducedMotion = true), tolerance)
        assertEquals(0f, veilStartAlpha(false, veilMs.toLong(), veilMs, reducedMotion = true), tolerance)
    }

    @Test
    fun clearVeilStartsClear() {
        assertEquals(0f, veilStartAlpha(false, veilMs / 2L, veilMs, reducedMotion = false), tolerance)
    }

    @Test
    fun unknownStartKeepsTheTarget() {
        assertEquals(1f, veilStartAlpha(true, null, veilMs, reducedMotion = false), tolerance)
        assertEquals(0f, veilStartAlpha(false, null, veilMs, reducedMotion = false), tolerance)
    }

    @Test
    fun remainderRunsAtTheFullFadesRate() {
        assertEquals(veilMs, veilRemainingMs(0f, 1f, veilMs, reducedMotion = false))
        assertEquals(veilMs / 2, veilRemainingMs(0.5f, 1f, veilMs, reducedMotion = false))
        assertEquals(veilMs / 2, veilRemainingMs(0.5f, 0f, veilMs, reducedMotion = false))
        assertEquals(0, veilRemainingMs(1f, 1f, veilMs, reducedMotion = false))
    }

    @Test
    fun reducedMotionHasNoRemainder() {
        assertEquals(0, veilRemainingMs(0f, 1f, veilMs, reducedMotion = true))
    }
}
