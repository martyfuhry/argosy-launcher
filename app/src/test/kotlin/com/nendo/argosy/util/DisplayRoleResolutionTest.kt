package com.nendo.argosy.util

import com.nendo.argosy.util.DisplayAffinityHelper.Companion.resolveRoleDisplayIds
import com.nendo.argosy.util.DisplayAffinityHelper.Companion.resolveSecondaryDisplayId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

private const val BUILT_IN = 0
private const val LOWER = 4
private const val MONITOR = 7

/**
 * The Thor with a monitor attached: the layout gives PRIMARY to the lower panel and PRESENTATION
 * to the built-in, and the monitor holds neither.
 */
class DisplayRoleResolutionTest {

    private val attached = setOf(BUILT_IN, LOWER, MONITOR)

    @Test
    fun `the companion display comes from the layout rather than list position`() {
        val resolved = resolveSecondaryDisplayId(
            roleDisplayIds = LOWER to BUILT_IN,
            attachedIds = attached,
            positionalFallback = MONITOR
        )

        assertEquals(LOWER, resolved)
    }

    @Test
    fun `list position is used only while no layout has been applied`() {
        val resolved = resolveSecondaryDisplayId(
            roleDisplayIds = null,
            attachedIds = attached,
            positionalFallback = LOWER
        )

        assertEquals(LOWER, resolved)
    }

    @Test
    fun `a role holder that has been unplugged falls back to position`() {
        val resolved = resolveSecondaryDisplayId(
            roleDisplayIds = MONITOR to BUILT_IN,
            attachedIds = setOf(BUILT_IN, LOWER),
            positionalFallback = LOWER
        )

        assertEquals(LOWER, resolved)
    }

    @Test
    fun `a layout holding both roles on the default display has no companion display`() {
        val resolved = resolveSecondaryDisplayId(
            roleDisplayIds = BUILT_IN to BUILT_IN,
            attachedIds = setOf(BUILT_IN),
            positionalFallback = null
        )

        assertNull(resolved)
    }

    @Test
    fun `roles answer the layout, input first`() {
        val resolved = resolveRoleDisplayIds(
            roleDisplayIds = LOWER to BUILT_IN,
            attachedIds = attached,
            secondaryDisplayId = LOWER,
            rolesSwapped = false
        )

        assertEquals(LOWER to BUILT_IN, resolved)
    }

    @Test
    fun `a swap exchanges the two role displays`() {
        val resolved = resolveRoleDisplayIds(
            roleDisplayIds = LOWER to BUILT_IN,
            attachedIds = attached,
            secondaryDisplayId = LOWER,
            rolesSwapped = true
        )

        assertEquals(BUILT_IN to LOWER, resolved)
    }

    @Test
    fun `a layout giving the default display PRIMARY answers itself while that is live`() {
        val resolved = resolveRoleDisplayIds(
            roleDisplayIds = BUILT_IN to LOWER,
            attachedIds = attached,
            secondaryDisplayId = LOWER,
            rolesSwapped = true
        )

        assertEquals(BUILT_IN to LOWER, resolved)
    }

    @Test
    fun `a layout giving the default display PRIMARY is exchanged once the lower panel hosts`() {
        val resolved = resolveRoleDisplayIds(
            roleDisplayIds = BUILT_IN to LOWER,
            attachedIds = attached,
            secondaryDisplayId = LOWER,
            rolesSwapped = false
        )

        assertEquals(LOWER to BUILT_IN, resolved)
    }

    @Test
    fun `the monitor never takes a role it was not given`() {
        val resolved = resolveRoleDisplayIds(
            roleDisplayIds = LOWER to BUILT_IN,
            attachedIds = attached,
            secondaryDisplayId = LOWER,
            rolesSwapped = false
        )

        assertEquals(setOf(LOWER, BUILT_IN), setOf(resolved!!.first, resolved.second))
    }

    @Test
    fun `without a layout the roles pair the default display with the positional secondary`() {
        val resolved = resolveRoleDisplayIds(
            roleDisplayIds = null,
            attachedIds = attached,
            secondaryDisplayId = LOWER,
            rolesSwapped = false
        )

        assertEquals(LOWER to BUILT_IN, resolved)
    }

    @Test
    fun `a single display has no roles to hold`() {
        val resolved = resolveRoleDisplayIds(
            roleDisplayIds = null,
            attachedIds = setOf(BUILT_IN),
            secondaryDisplayId = null,
            rolesSwapped = false
        )

        assertNull(resolved)
    }
}
