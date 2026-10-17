package com.nendo.argosy.ui.input

import android.view.KeyEvent
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LeftEdgeDrawerGateTest {

    private val start = KeyEvent.KEYCODE_BUTTON_START
    private val select = KeyEvent.KEYCODE_BUTTON_SELECT

    @Test
    fun `a pad with Start and Select disables the opener`() {
        assertFalse(isLeftEdgeDrawerEnabled(setOf(start, select), swapStartSelect = false))
        assertFalse(isLeftEdgeDrawerEnabled(setOf(start, select), swapStartSelect = true))
    }

    @Test
    fun `no pad with a system button keeps the opener`() {
        assertTrue(isLeftEdgeDrawerEnabled(emptySet(), swapStartSelect = false))
        assertTrue(isLeftEdgeDrawerEnabled(emptySet(), swapStartSelect = true))
    }

    @Test
    fun `only the button that maps to Menu counts`() {
        assertFalse(isLeftEdgeDrawerEnabled(setOf(start), swapStartSelect = false))
        assertTrue(isLeftEdgeDrawerEnabled(setOf(start), swapStartSelect = true))
        assertTrue(isLeftEdgeDrawerEnabled(setOf(select), swapStartSelect = false))
        assertFalse(isLeftEdgeDrawerEnabled(setOf(select), swapStartSelect = true))
    }

    @Test
    fun `hot-plugging a pad with Start toggles the opener`() = runTest(UnconfinedTestDispatcher()) {
        val buttons = MutableStateFlow(emptySet<Int>())
        val swap = MutableStateFlow(false)
        val seen = mutableListOf<Boolean>()
        val job = launch { leftEdgeDrawerEnabled(buttons, swap).toList(seen) }

        buttons.value = setOf(start, select)
        buttons.value = setOf(start)
        swap.value = true
        buttons.value = emptySet()
        job.cancel()

        assertEquals(listOf(true, false, true), seen)
    }
}
