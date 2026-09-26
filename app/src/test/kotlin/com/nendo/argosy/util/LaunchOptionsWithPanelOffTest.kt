package com.nendo.argosy.util

import android.app.ActivityOptions
import android.content.Context
import android.hardware.display.DisplayManager
import android.os.Bundle
import android.provider.Settings
import android.view.Display
import com.nendo.argosy.data.preferences.EmulatorDisplayTarget
import com.nendo.argosy.util.DisplayAffinityHelper.Companion.isWithinPanelWakeWindow
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import io.mockk.verify
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

private const val TOP = 0
private const val BOTTOM = 4
private const val LAUNCH_AT = 100_000L

/**
 * The Thor with its bottom panel reading off: for a moment while it wakes, just after a display
 * changed state, and for as long as the player has switched it off.
 */
class LaunchOptionsWithPanelOffTest {

    private val options = mockk<ActivityOptions>()
    private val bundle = mockk<Bundle>()
    private var now = LAUNCH_AT
    private var bottomState = Display.STATE_OFF

    private val top = panel(TOP) { Display.STATE_ON }
    private val bottom = panel(BOTTOM) { bottomState }

    private fun panel(id: Int, state: () -> Int): Display = mockk {
        every { displayId } returns id
        every { this@mockk.state } answers { state() }
        every { flags } returns 0
    }

    private fun helper(stateChangedAt: Long? = null): DisplayAffinityHelper {
        val displayManager = mockk<DisplayManager> {
            every { displays } returns arrayOf(top, bottom)
            every { getDisplay(TOP) } returns top
            every { getDisplay(BOTTOM) } returns bottom
        }
        val context = mockk<Context> {
            every { getSystemService(Context.DISPLAY_SERVICE) } returns displayManager
            every { getSystemService(DisplayManager::class.java) } returns displayManager
            every { contentResolver } returns mockk(relaxed = true)
        }
        return DisplayAffinityHelper(context, mockk(relaxed = true)).apply {
            dualScreenEnabled = true
            clock = { now }
            stateChangedAt?.let {
                now = it
                notePanelStateChange(TOP)
                now = LAUNCH_AT
            }
        }
    }

    private fun DisplayAffinityHelper.launch(
        drawsSecondScreen: Boolean,
        rolesSwapped: Boolean,
        target: EmulatorDisplayTarget = EmulatorDisplayTarget.DEFAULT
    ): Int? = gameDisplayId(drawsSecondScreen, target, overrideDisplayId = null, rolesSwapped = rolesSwapped)

    @Before
    fun setUp() {
        mockkStatic(ActivityOptions::class)
        every { ActivityOptions.makeBasic() } returns options
        every { options.setLaunchDisplayId(any()) } returns options
        every { options.toBundle() } returns bundle
        mockkStatic(Settings.System::class)
        every { Settings.System.getInt(any(), any(), any()) } returns 0
    }

    @After
    fun tearDown() {
        unmockkStatic(ActivityOptions::class)
        unmockkStatic(Settings.System::class)
    }

    @Test
    fun `an unswapped single-screen game takes the top panel while the bottom one wakes`() {
        val affinity = helper(stateChangedAt = LAUNCH_AT - 1_000)

        val displayId = affinity.launch(drawsSecondScreen = false, rolesSwapped = false)

        assertNotNull(affinity.getActivityOptions(forEmulator = true, rolesSwapped = false, overrideDisplayId = displayId))
        verify { options.setLaunchDisplayId(TOP) }
    }

    @Test
    fun `a swapped single-screen game takes the bottom panel while it wakes`() {
        val affinity = helper(stateChangedAt = LAUNCH_AT - 1_000)

        val displayId = affinity.launch(drawsSecondScreen = false, rolesSwapped = true)

        assertNotNull(affinity.getActivityOptions(forEmulator = true, rolesSwapped = true, overrideDisplayId = displayId))
        verify { options.setLaunchDisplayId(BOTTOM) }
    }

    @Test
    fun `a swapped single-screen game is left where it started once the wake window has passed`() {
        val affinity = helper(stateChangedAt = LAUNCH_AT - 5_000)

        val displayId = affinity.launch(drawsSecondScreen = false, rolesSwapped = true)

        assertNull(displayId)
        assertNull(affinity.getActivityOptions(forEmulator = true, rolesSwapped = true, overrideDisplayId = displayId))
    }

    @Test
    fun `a single-screen game is left where it started while the bottom panel is switched off`() {
        val affinity = helper()

        for (rolesSwapped in listOf(false, true)) {
            assertNull("swapped=$rolesSwapped", affinity.launch(drawsSecondScreen = false, rolesSwapped = rolesSwapped))
        }
    }

    @Test
    fun `a dual-screen game takes the top panel whether the bottom one wakes or stays off`() {
        for (stateChangedAt in listOf(LAUNCH_AT - 1_000, null)) {
            val affinity = helper(stateChangedAt)

            for (rolesSwapped in listOf(false, true)) {
                assertEquals(
                    "changedAt=$stateChangedAt swapped=$rolesSwapped",
                    TOP,
                    affinity.launch(drawsSecondScreen = true, rolesSwapped = rolesSwapped)
                )
            }
        }
    }

    @Test
    fun `a pin to the bottom panel holds while it wakes and is passed over while it stays off`() {
        val waking = helper(stateChangedAt = LAUNCH_AT - 1_000)
        val off = helper()

        val target = EmulatorDisplayTarget.PRESENTATION
        assertEquals(BOTTOM, waking.launch(drawsSecondScreen = false, rolesSwapped = true, target = target))
        assertNull(off.launch(drawsSecondScreen = false, rolesSwapped = true, target = target))
    }

    @Test
    fun `a display reporting the state it already had opens no new window`() {
        val affinity = helper(stateChangedAt = LAUNCH_AT - 10_000)
        now = LAUNCH_AT - 1_000
        affinity.notePanelStateChange(TOP)
        now = LAUNCH_AT

        assertNull(affinity.launch(drawsSecondScreen = false, rolesSwapped = true))
    }

    @Test
    fun `the bottom panel switching off opens a window of its own`() {
        val affinity = helper()
        bottomState = Display.STATE_ON
        now = LAUNCH_AT - 20_000
        affinity.notePanelStateChange(BOTTOM)
        bottomState = Display.STATE_OFF
        now = LAUNCH_AT - 1_000
        affinity.notePanelStateChange(BOTTOM)
        now = LAUNCH_AT

        assertEquals(BOTTOM, affinity.launch(drawsSecondScreen = false, rolesSwapped = true))
    }

    @Test
    fun `the wake window is open for five seconds after a change and never before one`() {
        assertFalse(isWithinPanelWakeWindow(lastChangeAt = null, now = LAUNCH_AT))
        assertTrue(isWithinPanelWakeWindow(lastChangeAt = LAUNCH_AT, now = LAUNCH_AT))
        assertTrue(isWithinPanelWakeWindow(lastChangeAt = LAUNCH_AT, now = LAUNCH_AT + 4_999))
        assertFalse(isWithinPanelWakeWindow(lastChangeAt = LAUNCH_AT, now = LAUNCH_AT + 5_000))
        assertFalse(isWithinPanelWakeWindow(lastChangeAt = LAUNCH_AT, now = LAUNCH_AT - 1))
    }

    @Test
    fun `an app launch is left to the launching screen while the other panel is off`() {
        val affinity = helper()

        assertNull(affinity.getActivityOptions(forEmulator = false))
    }
}
