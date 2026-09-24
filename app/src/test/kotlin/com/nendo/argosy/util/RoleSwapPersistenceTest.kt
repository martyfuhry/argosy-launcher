package com.nendo.argosy.util

import com.nendo.argosy.domain.model.ScreenLayout
import com.nendo.argosy.util.DisplayAffinityHelper.Companion.isSwappedArrangement
import com.nendo.argosy.util.DisplayAffinityHelper.Companion.layoutWithPrimaryOn
import com.nendo.argosy.util.DisplayAffinityHelper.Companion.resolveGameDisplayId
import com.nendo.argosy.util.DisplayAffinityHelper.Companion.resolveRoleDisplayIds
import com.nendo.argosy.util.DisplayAffinityHelper.Companion.resolveSecondaryDisplayId
import org.junit.Assert.assertEquals
import org.junit.Test

private const val TOP = 0
private const val BOTTOM = 4
private const val TOP_KEY = "display:0:1080x1920"
private const val BOTTOM_KEY = "display:4:1080x1240"

/**
 * The Thor's two stored layouts carried through Select swaps and process restarts, using the same
 * steps DualScreenManager takes: apply the layout, flip the live arrangement, store the screen now
 * hosting, and apply the stored layout again on the next start.
 */
class RoleSwapPersistenceTest {

    private val attached = setOf(TOP, BOTTOM)
    private val keysByDisplayId = mapOf(TOP to TOP_KEY, BOTTOM to BOTTOM_KEY)
    private val displayIdsByKey = keysByDisplayId.entries.associate { (id, key) -> key to id }

    private val primaryTop = ScreenLayout.fromJson("""{"$TOP_KEY":"PRIMARY","$BOTTOM_KEY":"PRESENTATION"}""")
    private val primaryBottom = ScreenLayout.fromJson("""{"$TOP_KEY":"PRESENTATION","$BOTTOM_KEY":"PRIMARY"}""")

    private data class Live(val layoutPair: Pair<Int, Int>, val rolesSwapped: Boolean, val stored: ScreenLayout)

    private fun start(stored: ScreenLayout): Live {
        val primary = displayIdsByKey.getValue(stored.primaryKey!!)
        val presentation = displayIdsByKey.getValue(stored.presentationKey!!)
        return Live(primary to presentation, isSwappedArrangement(primary), stored)
    }

    private fun Live.roles(): Pair<Int, Int> {
        val secondary = resolveSecondaryDisplayId(layoutPair, attached, BOTTOM)
        return resolveRoleDisplayIds(layoutPair, attached, secondary, rolesSwapped)!!
    }

    private fun Live.swap(): Live {
        val next = copy(rolesSwapped = !rolesSwapped)
        val shownPrimary = next.roles().first
        return next.copy(stored = layoutWithPrimaryOn(stored, keysByDisplayId, shownPrimary)!!)
    }

    private fun Live.launchDisplay(drawsSecondScreen: Boolean): Int? = resolveGameDisplayId(
        drawsSecondScreen = drawsSecondScreen,
        explicitDisplayId = null,
        attachedIds = attached,
        dualScreenActive = true,
        secondaryDisplayId = resolveSecondaryDisplayId(layoutPair, attached, BOTTOM),
        rolesSwapped = rolesSwapped
    )

    private fun Live.hostingDisplay(): Int = if (rolesSwapped) TOP else BOTTOM

    @Test
    fun `the launcher starts on the panel the stored layout names`() {
        assertEquals(TOP, start(primaryTop).hostingDisplay())
        assertEquals(TOP, start(primaryTop).roles().first)
        assertEquals(BOTTOM, start(primaryBottom).hostingDisplay())
        assertEquals(BOTTOM, start(primaryBottom).roles().first)
    }

    @Test
    fun `one swap stores exactly the arrangement on screen`() {
        for (layout in listOf(primaryTop, primaryBottom)) {
            val swapped = start(layout).swap()

            val roles = swapped.roles()
            assertEquals(swapped.hostingDisplay(), roles.first)
            assertEquals(keysByDisplayId[roles.first], swapped.stored.primaryKey)
            assertEquals(keysByDisplayId[roles.second], swapped.stored.presentationKey)
        }
    }

    @Test
    fun `swapping and swapping back leaves the stored layout as it was`() {
        for (layout in listOf(primaryTop, primaryBottom)) {
            assertEquals(layout, start(layout).swap().swap().stored)
        }
    }

    @Test
    fun `a restart after any number of swaps reproduces the arrangement and launch displays`() {
        for (layout in listOf(primaryTop, primaryBottom)) {
            var live = start(layout)
            repeat(5) {
                live = live.swap()
                val restarted = start(live.stored)

                assertEquals(live.rolesSwapped, restarted.rolesSwapped)
                assertEquals(live.roles(), restarted.roles())
                assertEquals(live.hostingDisplay(), restarted.hostingDisplay())
                assertEquals(live.launchDisplay(false), restarted.launchDisplay(false))
                assertEquals(live.launchDisplay(true), restarted.launchDisplay(true))
            }
        }
    }

    @Test
    fun `a swap from a layout with PRIMARY on top stores PRIMARY on the bottom panel`() {
        val swapped = start(primaryTop).swap()

        assertEquals(BOTTOM_KEY, swapped.stored.primaryKey)
        assertEquals(BOTTOM, swapped.hostingDisplay())
    }
}
