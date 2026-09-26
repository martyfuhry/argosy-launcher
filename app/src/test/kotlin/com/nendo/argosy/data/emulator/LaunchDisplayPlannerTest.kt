package com.nendo.argosy.data.emulator

import android.content.Context
import android.content.SharedPreferences
import android.hardware.display.DisplayManager
import android.provider.Settings
import android.view.Display
import com.nendo.argosy.data.preferences.EmulatorDisplayTarget
import com.nendo.argosy.data.repository.EmulatorConfigRepository
import com.nendo.argosy.util.DisplayAffinityHelper
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

private const val TOP = 0
private const val BOTTOM = 4
private const val TV = 2
private const val GAME_ID = 9L

/**
 * The planner as a launch reaches it: the live role swap read from the session store, the stored
 * screen pin read from the emulator config and the panels read from the display manager.
 */
class LaunchDisplayPlannerTest {

    private var rolesSwapped = false
    private var storedTarget: String? = null

    private fun panel(id: Int, flags: Int = 0, state: Int = Display.STATE_ON): Display = mockk {
        every { displayId } returns id
        every { this@mockk.state } returns state
        every { this@mockk.flags } returns flags
    }

    private fun planner(vararg panels: Display, layout: Pair<Int, Int>?): LaunchDisplayPlanner {
        val displayManager = mockk<DisplayManager> {
            every { displays } returns arrayOf(*panels)
        }
        val prefs = mockk<SharedPreferences> {
            every { getBoolean(any(), any()) } answers { rolesSwapped }
        }
        val context = mockk<Context> {
            every { getSystemService(Context.DISPLAY_SERVICE) } returns displayManager
            every { getSystemService(DisplayManager::class.java) } returns displayManager
            every { contentResolver } returns mockk(relaxed = true)
            every { getSharedPreferences(any(), any()) } returns prefs
        }
        val affinity = DisplayAffinityHelper(context, mockk(relaxed = true)).apply {
            dualScreenEnabled = true
            roleDisplayIds = layout
        }
        val configRepository = mockk<EmulatorConfigRepository> {
            coEvery { getEffectiveDisplayTarget(GAME_ID) } answers { storedTarget }
        }
        return LaunchDisplayPlanner(context, affinity, configRepository)
    }

    @Before
    fun setUp() {
        mockkStatic(Settings.System::class)
        every { Settings.System.getInt(any(), any(), any()) } returns 0
    }

    @After
    fun tearDown() {
        unmockkStatic(Settings.System::class)
    }

    private fun thor() = planner(panel(TOP), panel(BOTTOM), layout = TOP to BOTTOM)

    private fun expectedOnThor(
        drawsSecondScreen: Boolean,
        pin: EmulatorDisplayTarget,
        overrideDisplayId: Int?,
        swapped: Boolean
    ): Int {
        val primary = if (swapped) TOP else BOTTOM
        val presentation = if (swapped) BOTTOM else TOP
        return when {
            overrideDisplayId != null -> overrideDisplayId
            pin == EmulatorDisplayTarget.PRIMARY -> primary
            pin == EmulatorDisplayTarget.PRESENTATION -> presentation
            drawsSecondScreen -> TOP
            else -> presentation
        }
    }

    @Test
    fun `every launch on the thor lands on the expected panel`() = runTest {
        val planner = thor()
        val pins = listOf(EmulatorDisplayTarget.DEFAULT, EmulatorDisplayTarget.PRIMARY, EmulatorDisplayTarget.PRESENTATION)
        for (swapped in listOf(false, true)) {
            rolesSwapped = swapped
            for (drawsSecondScreen in listOf(true, false)) {
                for (pin in pins) {
                    storedTarget = pin.name
                    for (overrideDisplayId in listOf(null, TOP, BOTTOM)) {
                        assertEquals(
                            "swapped=$swapped dual=$drawsSecondScreen pin=$pin override=$overrideDisplayId",
                            expectedOnThor(drawsSecondScreen, pin, overrideDisplayId, swapped),
                            planner.displayFor(GAME_ID, drawsSecondScreen, overrideDisplayId)
                        )
                    }
                }
            }
        }
    }

    @Test
    fun `a handheld with a TV sends a dual-screen game to the presentation screen as upstream does`() = runTest {
        val planner = planner(panel(TOP), panel(TV, flags = Display.FLAG_PRESENTATION), layout = null)

        rolesSwapped = false
        assertEquals(TOP, planner.displayFor(GAME_ID, drawsSecondScreen = true))
        rolesSwapped = true
        assertEquals(TV, planner.displayFor(GAME_ID, drawsSecondScreen = true))
        assertEquals(TV, planner.displayFor(GAME_ID, drawsSecondScreen = false))
    }

    @Test
    fun `a dock blanking the thor's panels sends every launch to the television`() = runTest {
        val planner = planner(
            panel(TOP, state = Display.STATE_OFF),
            panel(BOTTOM, state = Display.STATE_OFF),
            panel(TV, flags = Display.FLAG_PRESENTATION),
            layout = TOP to BOTTOM
        )
        for (swapped in listOf(false, true)) {
            rolesSwapped = swapped
            for (drawsSecondScreen in listOf(true, false)) {
                storedTarget = EmulatorDisplayTarget.PRIMARY.name
                assertEquals(TV, planner.displayFor(GAME_ID, drawsSecondScreen, overrideDisplayId = BOTTOM))
                storedTarget = null
                assertEquals(TV, planner.displayFor(GAME_ID, drawsSecondScreen))
            }
        }
    }
}
