package com.nendo.argosy.ui.components.launch

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class LaunchOverlayStateTest {

    @Test
    fun `the mapper and the calm bar shapes exist for the overlay to build on`() {
        assertNotNull(LaunchOverlayState)
        assertEquals(listOf(CalmLaunchBar.NONE, CalmLaunchBar.INDETERMINATE, CalmLaunchBar.DETERMINATE), CalmLaunchBar.entries)
    }
}
