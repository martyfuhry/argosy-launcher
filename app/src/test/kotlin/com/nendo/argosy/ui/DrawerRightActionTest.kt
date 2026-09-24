package com.nendo.argosy.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class DrawerRightActionTest {

    @Test
    fun `Right on navigation closes the drawer when there is no Friends tab`() {
        assertEquals(
            DrawerRightAction.CLOSE_DRAWER,
            drawerRightAction(DrawerTab.NAVIGATION, socialConnected = false)
        )
    }

    @Test
    fun `Right on navigation switches to Friends when social is connected`() {
        assertEquals(
            DrawerRightAction.SWITCH_TO_FRIENDS,
            drawerRightAction(DrawerTab.NAVIGATION, socialConnected = true)
        )
    }

    @Test
    fun `Right on the Friends tab is left to the tab`() {
        assertEquals(DrawerRightAction.NONE, drawerRightAction(DrawerTab.FRIENDS, socialConnected = true))
        assertEquals(DrawerRightAction.NONE, drawerRightAction(DrawerTab.FRIENDS, socialConnected = false))
    }
}
