package com.nendo.argosy.ui.screens.gamedetail

import com.nendo.argosy.ui.screens.gamedetail.delegates.LaunchScreenOption
import com.nendo.argosy.ui.screens.gamedetail.delegates.PickerModalDelegate
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private const val TOP = 0
private const val BOTTOM = 4

class LaunchScreenPickerTest {

    private val delegate = PickerModalDelegate(
        context = mockk(relaxed = true),
        emulatorDetector = mockk(relaxed = true),
        soundManager = mockk(relaxed = true)
    )

    private val screens = listOf(LaunchScreenOption(TOP, 1), LaunchScreenOption(BOTTOM, 2))

    @Test
    fun `the chooser opens on the screen the row was showing`() {
        delegate.showLaunchScreenPicker(screens, focusIndex = 1)

        assertTrue(delegate.state.value.showLaunchScreenPicker)
        assertEquals(BOTTOM, delegate.state.value.focusedLaunchScreen?.displayId)
    }

    @Test
    fun `confirming maps the focused row to its display`() {
        delegate.showLaunchScreenPicker(screens, focusIndex = 0)
        delegate.moveLaunchScreenFocus(1)

        assertEquals(BOTTOM, delegate.state.value.focusedLaunchScreen?.displayId)
    }

    @Test
    fun `focus stays inside the list`() {
        delegate.showLaunchScreenPicker(screens, focusIndex = 5)

        assertEquals(1, delegate.state.value.launchScreenFocusIndex)
    }

    @Test
    fun `the chooser counts as an open picker while it is shown`() {
        delegate.showLaunchScreenPicker(screens, focusIndex = 0)
        assertTrue(delegate.state.value.hasAnyPickerOpen)

        delegate.dismissLaunchScreenPicker()
        assertFalse(delegate.state.value.hasAnyPickerOpen)
        assertNull(delegate.state.value.focusedLaunchScreen)
    }

    @Test
    fun `no screens means no chooser`() {
        delegate.showLaunchScreenPicker(emptyList(), focusIndex = 0)

        assertFalse(delegate.state.value.showLaunchScreenPicker)
    }
}
