package com.nendo.argosy.util

import com.nendo.argosy.data.emulator.LaunchDisplayPlanner
import com.nendo.argosy.data.preferences.EmulatorDisplayTarget
import com.nendo.argosy.libretro.LibretroActivity
import com.nendo.argosy.util.DisplayAffinityHelper.Companion.resolveDisplayTargetId
import com.nendo.argosy.util.DisplayAffinityHelper.Companion.resolveGameDisplayId
import com.nendo.argosy.util.DisplayAffinityHelper.Companion.resolveRoleDisplayIds
import com.nendo.argosy.util.DisplayAffinityHelper.Companion.resolveSecondaryDisplayId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

private const val TOP = 0
private const val BOTTOM = 4
private const val DETACHED = 7
private const val TV = 2

private enum class TargetKind(val className: String?, val packageName: String?, val coreId: String?) {
    DUAL_SCREEN_EMULATOR(null, "org.azahar_emu.azahar.thor", null),
    SINGLE_SCREEN_EMULATOR(null, "com.github.stenzek.duckstation", null),
    LIBRETRO_DS_CORE(LibretroActivity::class.java.name, null, "melonds"),
    LIBRETRO_SINGLE_SCREEN_CORE(LibretroActivity::class.java.name, null, "snes9x"),
    ANDROID_APP(null, "com.example.banjo", null)
}

private enum class Pin(val displayId: Int?) { NONE(null), TOP_SCREEN(TOP), BOTTOM_SCREEN(BOTTOM), DETACHED_SCREEN(DETACHED) }

/**
 * Every Thor arrangement against every kind of launch: the stored layout's PRIMARY panel, whether
 * the roles are swapped away from it, what is being launched and what the player pinned.
 */
class GameLaunchDisplayTableTest {

    private val attached = setOf(TOP, BOTTOM)

    private fun expected(kind: TargetKind, pin: Pin, launcherOnTop: Boolean): Int = when {
        pin == Pin.TOP_SCREEN -> TOP
        pin == Pin.BOTTOM_SCREEN -> BOTTOM
        kind == TargetKind.DUAL_SCREEN_EMULATOR || kind == TargetKind.LIBRETRO_DS_CORE -> TOP
        launcherOnTop -> BOTTOM
        else -> TOP
    }

    @Test
    fun `every arrangement sends each launch to the expected screen`() {
        for (layoutPrimary in listOf(TOP, BOTTOM)) {
            val layoutPair = layoutPrimary to (if (layoutPrimary == TOP) BOTTOM else TOP)
            for (swappedAway in listOf(false, true)) {
                val livePrimary = if (swappedAway) layoutPair.second else layoutPair.first
                val rolesSwapped = livePrimary == TOP
                val secondary = resolveSecondaryDisplayId(layoutPair, attached, BOTTOM)
                val presentation = resolveRoleDisplayIds(layoutPair, attached, secondary, rolesSwapped)!!.second
                for (kind in TargetKind.entries) {
                    for (pin in Pin.entries) {
                        val drawsSecondScreen = LaunchDisplayPlanner.drawsSecondScreen(
                            kind.className,
                            kind.packageName,
                            kind.coreId
                        )
                        val resolved = resolveGameDisplayId(
                            drawsSecondScreen = drawsSecondScreen,
                            explicitDisplayId = pin.displayId,
                            attachedIds = attached,
                            builtInPanelPair = true,
                            dualScreenActive = true,
                            presentationDisplayId = presentation
                        )
                        assertEquals(
                            "layout PRIMARY=$layoutPrimary swappedAway=$swappedAway $kind pin=$pin",
                            expected(kind, pin, launcherOnTop = livePrimary == TOP),
                            resolved
                        )
                    }
                }
            }
        }
    }

    @Test
    fun `a role pin names the screen holding that role for every kind of launch`() {
        val layoutPair = TOP to BOTTOM
        for (rolesSwapped in listOf(false, true)) {
            val secondary = resolveSecondaryDisplayId(layoutPair, attached, BOTTOM)
            val roles = resolveRoleDisplayIds(layoutPair, attached, secondary, rolesSwapped)!!
            for (kind in TargetKind.entries) {
                val drawsSecondScreen = LaunchDisplayPlanner.drawsSecondScreen(kind.className, kind.packageName, kind.coreId)
                for (target in listOf(EmulatorDisplayTarget.PRIMARY, EmulatorDisplayTarget.PRESENTATION)) {
                    val pinned = resolveDisplayTargetId(target, roles, appScreenDisplayId = null)
                    val resolved = resolveGameDisplayId(
                        drawsSecondScreen = drawsSecondScreen,
                        explicitDisplayId = pinned,
                        attachedIds = attached,
                        builtInPanelPair = true,
                        dualScreenActive = true,
                        presentationDisplayId = roles.second
                    )
                    val holder = if (target == EmulatorDisplayTarget.PRIMARY) roles.first else roles.second
                    assertEquals("$kind $target swapped=$rolesSwapped", holder, resolved)
                }
            }
        }
    }

    @Test
    fun `a dual-screen game takes the top screen with dual screen switched off`() {
        val resolved = resolveGameDisplayId(
            drawsSecondScreen = true,
            explicitDisplayId = null,
            attachedIds = attached,
            builtInPanelPair = true,
            dualScreenActive = false,
            presentationDisplayId = BOTTOM
        )

        assertEquals(TOP, resolved)
    }

    @Test
    fun `a single-screen game stays where it was started with dual screen switched off`() {
        val resolved = resolveGameDisplayId(
            drawsSecondScreen = false,
            explicitDisplayId = null,
            attachedIds = attached,
            builtInPanelPair = true,
            dualScreenActive = false,
            presentationDisplayId = BOTTOM
        )

        assertNull(resolved)
    }

    @Test
    fun `a handheld with a TV attached sends a dual-screen game to the presentation screen`() {
        for (presentation in listOf(TOP, TV)) {
            val resolved = resolveGameDisplayId(
                drawsSecondScreen = true,
                explicitDisplayId = null,
                attachedIds = setOf(TOP, TV),
                builtInPanelPair = false,
                dualScreenActive = true,
                presentationDisplayId = presentation
            )

            assertEquals("presentation=$presentation", presentation, resolved)
        }
    }

    @Test
    fun `a handheld with a TV and dual screen switched off leaves a dual-screen game where it started`() {
        val resolved = resolveGameDisplayId(
            drawsSecondScreen = true,
            explicitDisplayId = null,
            attachedIds = setOf(TOP, TV),
            builtInPanelPair = false,
            dualScreenActive = false,
            presentationDisplayId = TV
        )

        assertNull(resolved)
    }

    @Test
    fun `a single panel names no screen for any game`() {
        for (drawsSecondScreen in listOf(false, true)) {
            val resolved = resolveGameDisplayId(
                drawsSecondScreen = drawsSecondScreen,
                explicitDisplayId = null,
                attachedIds = setOf(TOP),
                builtInPanelPair = false,
                dualScreenActive = true,
                presentationDisplayId = null
            )

            assertNull(resolved)
        }
    }
}
