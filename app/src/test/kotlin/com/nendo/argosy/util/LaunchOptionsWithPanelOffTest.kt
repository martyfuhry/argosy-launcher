package com.nendo.argosy.util

import android.app.ActivityOptions
import android.content.Context
import android.hardware.display.DisplayManager
import android.os.Bundle
import android.view.Display
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import io.mockk.verify
import org.junit.After
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

private const val TOP = 0
private const val BOTTOM = 4

/**
 * The Thor with its bottom panel still reading off, as it does for a moment after the lid opens.
 */
class LaunchOptionsWithPanelOffTest {

    private val options = mockk<ActivityOptions>()
    private val bundle = mockk<Bundle>()

    private fun panel(id: Int, state: Int): Display = mockk {
        every { displayId } returns id
        every { this@mockk.state } returns state
        every { flags } returns 0
    }

    private fun helper(bottomState: Int): DisplayAffinityHelper {
        val displayManager = mockk<DisplayManager> {
            every { displays } returns arrayOf(panel(TOP, Display.STATE_ON), panel(BOTTOM, bottomState))
        }
        val context = mockk<Context> {
            every { getSystemService(Context.DISPLAY_SERVICE) } returns displayManager
        }
        return DisplayAffinityHelper(context).apply { dualScreenEnabled = true }
    }

    @Before
    fun setUp() {
        mockkStatic(ActivityOptions::class)
        every { ActivityOptions.makeBasic() } returns options
        every { options.setLaunchDisplayId(any()) } returns options
        every { options.toBundle() } returns bundle
    }

    @After
    fun tearDown() {
        unmockkStatic(ActivityOptions::class)
    }

    @Test
    fun `a game launch names its display while one of two attached panels is off`() {
        val affinity = helper(bottomState = Display.STATE_OFF)

        assertNotNull(affinity.getActivityOptions(forEmulator = true, rolesSwapped = false))
        verify { options.setLaunchDisplayId(TOP) }
    }

    @Test
    fun `a dual-screen game is sent to the top panel while the bottom one is off`() {
        val affinity = helper(bottomState = Display.STATE_OFF)

        val displayId = affinity.gameDisplayId(drawsSecondScreen = true, explicitDisplayId = null, rolesSwapped = true)

        assertNotNull(affinity.getActivityOptions(forEmulator = true, rolesSwapped = true, overrideDisplayId = displayId))
        verify { options.setLaunchDisplayId(TOP) }
    }

    @Test
    fun `a swapped single-screen game still takes the bottom panel while it is off`() {
        val affinity = helper(bottomState = Display.STATE_OFF)

        val displayId = affinity.gameDisplayId(drawsSecondScreen = false, explicitDisplayId = null, rolesSwapped = true)

        assertNotNull(affinity.getActivityOptions(forEmulator = true, rolesSwapped = true, overrideDisplayId = displayId))
        verify { options.setLaunchDisplayId(BOTTOM) }
    }

    @Test
    fun `an app launch is left to the launching screen while the other panel is off`() {
        val affinity = helper(bottomState = Display.STATE_OFF)

        assertNull(affinity.getActivityOptions(forEmulator = false))
    }
}
