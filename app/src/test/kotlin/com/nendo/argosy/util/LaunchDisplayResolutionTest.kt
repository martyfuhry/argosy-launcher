package com.nendo.argosy.util

import com.nendo.argosy.data.preferences.EmulatorDisplayTarget
import com.nendo.argosy.util.DisplayAffinityHelper.Companion.resolveDisplayTargetId
import com.nendo.argosy.util.DisplayAffinityHelper.Companion.resolveLaunchDisplayId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

private const val BUILT_IN = 0
private const val LOWER = 4
private const val MONITOR = 7

/**
 * The Thor with a monitor attached, where the monitor holds the app-target role.
 */
class LaunchDisplayResolutionTest {

    @Test
    fun `a game ignores the app-target screen`() {
        val resolved = resolveLaunchDisplayId(
            forEmulator = true,
            overrideDisplayId = null,
            appTarget = MONITOR,
            secondaryDisplayId = LOWER,
            rolesSwapped = false
        )

        assertEquals(BUILT_IN, resolved)
    }

    @Test
    fun `a game with no app-target screen lands where it always did`() {
        val resolved = resolveLaunchDisplayId(
            forEmulator = true,
            overrideDisplayId = null,
            appTarget = null,
            secondaryDisplayId = LOWER,
            rolesSwapped = false
        )

        assertEquals(BUILT_IN, resolved)
    }

    @Test
    fun `a swapped game takes the companion screen over the app-target screen`() {
        val resolved = resolveLaunchDisplayId(
            forEmulator = true,
            overrideDisplayId = null,
            appTarget = MONITOR,
            secondaryDisplayId = LOWER,
            rolesSwapped = true
        )

        assertEquals(LOWER, resolved)
    }

    @Test
    fun `a swapped game on a single screen has nowhere named to go`() {
        val resolved = resolveLaunchDisplayId(
            forEmulator = true,
            overrideDisplayId = null,
            appTarget = MONITOR,
            secondaryDisplayId = null,
            rolesSwapped = true
        )

        assertNull(resolved)
    }

    @Test
    fun `an app takes the app-target screen`() {
        val resolved = resolveLaunchDisplayId(
            forEmulator = false,
            overrideDisplayId = null,
            appTarget = MONITOR,
            secondaryDisplayId = LOWER,
            rolesSwapped = false
        )

        assertEquals(MONITOR, resolved)
    }

    @Test
    fun `an app falls back to the companion screen when no app target is set`() {
        val resolved = resolveLaunchDisplayId(
            forEmulator = false,
            overrideDisplayId = null,
            appTarget = null,
            secondaryDisplayId = LOWER,
            rolesSwapped = false
        )

        assertEquals(LOWER, resolved)
    }

    @Test
    fun `an explicit choice beats the app-target screen`() {
        val resolved = resolveLaunchDisplayId(
            forEmulator = false,
            overrideDisplayId = LOWER,
            appTarget = MONITOR,
            secondaryDisplayId = LOWER,
            rolesSwapped = false
        )

        assertEquals(LOWER, resolved)
    }

    @Test
    fun `an explicit choice sends a game to the app-target screen`() {
        val resolved = resolveLaunchDisplayId(
            forEmulator = true,
            overrideDisplayId = MONITOR,
            appTarget = MONITOR,
            secondaryDisplayId = LOWER,
            rolesSwapped = false
        )

        assertEquals(MONITOR, resolved)
    }

    @Test
    fun `the default target names no screen of its own`() {
        val resolved = resolveDisplayTargetId(
            target = EmulatorDisplayTarget.DEFAULT,
            roleDisplayIds = LOWER to BUILT_IN,
            appScreenDisplayId = MONITOR
        )

        assertNull(resolved)
    }

    @Test
    fun `the presentation target names the presentation screen`() {
        val resolved = resolveDisplayTargetId(
            target = EmulatorDisplayTarget.PRESENTATION,
            roleDisplayIds = LOWER to BUILT_IN,
            appScreenDisplayId = MONITOR
        )

        assertEquals(BUILT_IN, resolved)
    }

    @Test
    fun `the primary target names the primary screen`() {
        val resolved = resolveDisplayTargetId(
            target = EmulatorDisplayTarget.PRIMARY,
            roleDisplayIds = LOWER to BUILT_IN,
            appScreenDisplayId = MONITOR
        )

        assertEquals(LOWER, resolved)
    }

    @Test
    fun `the app-screen target names the app screen`() {
        val resolved = resolveDisplayTargetId(
            target = EmulatorDisplayTarget.APP_SCREEN,
            roleDisplayIds = LOWER to BUILT_IN,
            appScreenDisplayId = MONITOR
        )

        assertEquals(MONITOR, resolved)
    }

    @Test
    fun `the app-screen target falls back to the presentation screen when none is attached`() {
        val resolved = resolveDisplayTargetId(
            target = EmulatorDisplayTarget.APP_SCREEN,
            roleDisplayIds = LOWER to BUILT_IN,
            appScreenDisplayId = null
        )

        assertEquals(BUILT_IN, resolved)
    }

    @Test
    fun `a named target has no screen to take on a single-screen device`() {
        val resolved = resolveDisplayTargetId(
            target = EmulatorDisplayTarget.PRESENTATION,
            roleDisplayIds = null,
            appScreenDisplayId = null
        )

        assertNull(resolved)
    }
}
