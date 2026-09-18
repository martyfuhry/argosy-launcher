package com.nendo.argosy.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private const val BUILT_IN = "local:builtin"
private const val SECOND = "local:second"
private const val THIRD = "local:third"

class ScreenLayoutTest {

    private fun layoutOf(vararg pairs: Pair<String, ScreenRole>) = ScreenLayout(pairs.toMap())

    @Test
    fun `built-in screen takes primary by default`() {
        val layout = ScreenLayout.defaultFor(listOf(BUILT_IN, SECOND), listOf(BUILT_IN))
        assertEquals(BUILT_IN, layout.primaryKey)
        assertEquals(SECOND, layout.presentationKey)
    }

    @Test
    fun `promoting the presentation screen demotes the old primary to presentation`() {
        val layout = layoutOf(BUILT_IN to ScreenRole.PRIMARY, SECOND to ScreenRole.PRESENTATION)
        val next = layout.withRole(SECOND, ScreenRole.PRIMARY)
        assertEquals(SECOND, next.primaryKey)
        assertEquals(BUILT_IN, next.presentationKey)
    }

    @Test
    fun `demoting the primary to presentation promotes the old presentation`() {
        val layout = layoutOf(BUILT_IN to ScreenRole.PRIMARY, SECOND to ScreenRole.PRESENTATION)
        val next = layout.withRole(BUILT_IN, ScreenRole.PRESENTATION)
        assertEquals(SECOND, next.primaryKey)
        assertEquals(BUILT_IN, next.presentationKey)
    }

    @Test
    fun `swapping back restores the arrangement it started from`() {
        val layout = layoutOf(BUILT_IN to ScreenRole.PRIMARY, SECOND to ScreenRole.PRESENTATION)
        val round = layout.withRole(SECOND, ScreenRole.PRIMARY).withRole(BUILT_IN, ScreenRole.PRIMARY)
        assertEquals(layout.roles, round.roles)
    }

    /**
     * The swap is only ever between primary and presentation. An app-target screen keeps its role
     * while the other two exchange theirs, which is what makes a media screen a fixed destination
     * rather than something a swap can move out from under a running app.
     */
    @Test
    fun `swapping primary and presentation leaves an app-target screen alone`() {
        val layout = layoutOf(
            BUILT_IN to ScreenRole.PRIMARY,
            SECOND to ScreenRole.PRESENTATION,
            THIRD to ScreenRole.APP_TARGET
        )
        val next = layout.withRole(SECOND, ScreenRole.PRIMARY)
        assertEquals(ScreenRole.APP_TARGET, next.roleFor(THIRD))
        assertEquals(SECOND, next.primaryKey)
        assertEquals(BUILT_IN, next.presentationKey)
    }

    /**
     * A promotion trades roles rather than inventing one: the displaced primary takes the role the
     * promoted screen gave up, so the arrangement keeps one screen per role instead of ending up
     * with two presentation screens and no app target.
     */
    @Test
    fun `promoting an app-target screen hands the old primary the app-target role`() {
        val layout = layoutOf(
            BUILT_IN to ScreenRole.PRIMARY,
            SECOND to ScreenRole.PRESENTATION,
            THIRD to ScreenRole.APP_TARGET
        )
        val next = layout.withRole(THIRD, ScreenRole.PRIMARY)
        assertEquals(THIRD, next.primaryKey)
        assertEquals(SECOND, next.presentationKey)
        assertEquals(BUILT_IN, next.appTargetKey)
    }

    @Test
    fun `promoting a disabled screen hands the old primary the off role`() {
        val layout = layoutOf(
            BUILT_IN to ScreenRole.PRIMARY,
            SECOND to ScreenRole.PRESENTATION,
            THIRD to ScreenRole.OFF
        )
        val next = layout.withRole(THIRD, ScreenRole.PRIMARY)
        assertEquals(THIRD, next.primaryKey)
        assertEquals(SECOND, next.presentationKey)
        assertEquals(ScreenRole.OFF, next.roleFor(BUILT_IN))
    }

    @Test
    fun `three screens keep one role each through a promotion`() {
        val layout = layoutOf(
            BUILT_IN to ScreenRole.PRIMARY,
            SECOND to ScreenRole.PRESENTATION,
            THIRD to ScreenRole.APP_TARGET
        )
        val next = layout.withRole(THIRD, ScreenRole.PRIMARY)
        assertEquals(3, next.roles.size)
        assertEquals(layout.roles.values.toSet(), next.roles.values.toSet())
        assertEquals(3, next.roles.values.toSet().size)
    }

    @Test
    fun `promoting an app-target screen is undone by promoting the screen it displaced`() {
        val layout = layoutOf(
            BUILT_IN to ScreenRole.PRIMARY,
            SECOND to ScreenRole.PRESENTATION,
            THIRD to ScreenRole.APP_TARGET
        )
        val round = layout.withRole(THIRD, ScreenRole.PRIMARY).withRole(BUILT_IN, ScreenRole.PRIMARY)
        assertEquals(layout.roles, round.roles)
    }

    @Test
    fun `claiming app target does not disturb the primary`() {
        val layout = layoutOf(
            BUILT_IN to ScreenRole.PRIMARY,
            SECOND to ScreenRole.PRESENTATION,
            THIRD to ScreenRole.APP_TARGET
        )
        val next = layout.withRole(SECOND, ScreenRole.APP_TARGET)
        assertEquals(BUILT_IN, next.primaryKey)
        assertEquals(SECOND, next.appTargetKey)
    }

    /**
     * Two screens where the second is the app target is a single-display arrangement: nothing holds
     * the presentation role, so the primary screen must host the launcher on its own.
     */
    @Test
    fun `a primary plus an app target is a single-display arrangement`() {
        val layout = layoutOf(BUILT_IN to ScreenRole.PRIMARY, SECOND to ScreenRole.APP_TARGET)
        assertTrue(layout.isSingleDisplay)
        assertNull(layout.presentationKey)
    }

    @Test
    fun `a primary and a presentation is not a single-display arrangement`() {
        val layout = layoutOf(BUILT_IN to ScreenRole.PRIMARY, SECOND to ScreenRole.PRESENTATION)
        assertTrue(!layout.isSingleDisplay)
    }

    @Test
    fun `roles survive a json round trip`() {
        val layout = layoutOf(
            BUILT_IN to ScreenRole.PRIMARY,
            SECOND to ScreenRole.PRESENTATION,
            THIRD to ScreenRole.OFF
        )
        assertEquals(layout.roles, ScreenLayout.fromJson(layout.toJson()).roles)
    }

    @Test
    fun `an unreadable payload falls back to an empty layout`() {
        assertEquals(emptyMap<String, ScreenRole>(), ScreenLayout.fromJson("not json").roles)
        assertEquals(emptyMap<String, ScreenRole>(), ScreenLayout.fromJson(null).roles)
    }
}

class ScreenLayoutsTest {

    /**
     * The set key identifies which screens are attached, not the order the system happened to
     * enumerate them in, so unplugging and replugging the same panel returns to the same profile.
     */
    @Test
    fun `the set key ignores enumeration order`() {
        assertEquals(
            ScreenLayouts.setKeyOf(listOf(BUILT_IN, SECOND)),
            ScreenLayouts.setKeyOf(listOf(SECOND, BUILT_IN))
        )
    }

    @Test
    fun `a different set of screens is a different profile`() {
        val pair = ScreenLayouts.setKeyOf(listOf(BUILT_IN, SECOND))
        val trio = ScreenLayouts.setKeyOf(listOf(BUILT_IN, SECOND, THIRD))
        val single = ScreenLayouts.setKeyOf(listOf(BUILT_IN))
        assertEquals(3, setOf(pair, trio, single).size)
    }

    @Test
    fun `storing a layout leaves the other sets untouched`() {
        val single = ScreenLayouts.setKeyOf(listOf(BUILT_IN))
        val pair = ScreenLayouts.setKeyOf(listOf(BUILT_IN, SECOND))
        val singleLayout = ScreenLayout(mapOf(BUILT_IN to ScreenRole.PRIMARY))
        val pairLayout = ScreenLayout(
            mapOf(BUILT_IN to ScreenRole.PRESENTATION, SECOND to ScreenRole.PRIMARY)
        )

        val stored = ScreenLayouts().with(single, singleLayout).with(pair, pairLayout)

        assertEquals(singleLayout.roles, stored.layoutFor(single)?.roles)
        assertEquals(pairLayout.roles, stored.layoutFor(pair)?.roles)
    }

    @Test
    fun `an unseen set of screens has no stored layout`() {
        val stored = ScreenLayouts().with(
            ScreenLayouts.setKeyOf(listOf(BUILT_IN)),
            ScreenLayout(mapOf(BUILT_IN to ScreenRole.PRIMARY))
        )
        assertNull(stored.layoutFor(ScreenLayouts.setKeyOf(listOf(BUILT_IN, SECOND))))
    }

    @Test
    fun `profiles survive a json round trip`() {
        val key = ScreenLayouts.setKeyOf(listOf(BUILT_IN, SECOND))
        val stored = ScreenLayouts().with(
            key,
            ScreenLayout(mapOf(BUILT_IN to ScreenRole.PRIMARY, SECOND to ScreenRole.PRESENTATION))
        )
        val restored = ScreenLayouts.fromJson(stored.toJson())
        assertEquals(stored.layoutFor(key)?.roles, restored.layoutFor(key)?.roles)
    }
}
