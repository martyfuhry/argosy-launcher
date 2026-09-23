package com.nendo.argosy.util

import com.nendo.argosy.data.preferences.EmulatorDisplayTarget
import com.nendo.argosy.util.DisplayAffinityHelper.Companion.resolveDisplayTarget
import com.nendo.argosy.util.DisplayAffinityHelper.Companion.resolveDisplayTargetId
import com.nendo.argosy.util.DisplayAffinityHelper.Companion.resolveRoleDisplayIds
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

private const val TOP = 0
private const val BOTTOM = 4
private const val MONITOR = 7

/**
 * The Thor whose layout gives PRIMARY to the top panel and PRESENTATION to the bottom one, with a
 * monitor attached as the app-target screen.
 */
class LaunchScreenTargetMappingTest {

    private val layout = TOP to BOTTOM
    private val attached = setOf(TOP, BOTTOM, MONITOR)

    private fun roles(rolesSwapped: Boolean) = resolveRoleDisplayIds(
        roleDisplayIds = layout,
        attachedIds = attached,
        secondaryDisplayId = BOTTOM,
        rolesSwapped = rolesSwapped
    )

    @Test
    fun `each screen maps to the role the layout gives it`() {
        assertEquals(EmulatorDisplayTarget.PRIMARY, resolveDisplayTarget(TOP, roles(false), MONITOR))
        assertEquals(EmulatorDisplayTarget.PRESENTATION, resolveDisplayTarget(BOTTOM, roles(false), MONITOR))
        assertEquals(EmulatorDisplayTarget.APP_SCREEN, resolveDisplayTarget(MONITOR, roles(false), MONITOR))
    }

    @Test
    fun `a screen that holds no role has no target`() {
        assertNull(resolveDisplayTarget(MONITOR, roles(false), appScreenDisplayId = null))
        assertNull(resolveDisplayTarget(99, roles(false), MONITOR))
    }

    @Test
    fun `a single screen has no target to map to`() {
        assertNull(resolveDisplayTarget(TOP, roleDisplayIds = null, appScreenDisplayId = null))
    }

    @Test
    fun `a target stored against the layout comes back as the same screen`() {
        for (displayId in attached) {
            val target = resolveDisplayTarget(displayId, roles(false), MONITOR)!!
            assertEquals(displayId, resolveDisplayTargetId(target, roles(false), MONITOR))
        }
    }

    @Test
    fun `a swap moves a target that follows the roles`() {
        val stored = resolveDisplayTarget(TOP, roles(false), MONITOR)!!

        assertEquals(BOTTOM, resolveDisplayTargetId(stored, roles(true), MONITOR))
    }

    @Test
    fun `a swapped screen is stored by the role the layout gives it`() {
        assertEquals(EmulatorDisplayTarget.PRESENTATION, resolveDisplayTarget(TOP, roles(true), MONITOR))
        assertEquals(EmulatorDisplayTarget.PRIMARY, resolveDisplayTarget(TOP, roles(false), MONITOR))
    }
}
