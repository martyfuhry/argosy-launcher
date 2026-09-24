package com.nendo.argosy.ui.dualscreen

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HostHandoffTest {

    @Test
    fun `the host composed under the captured arrangement claims the state once`() {
        val handoff = HostHandoff<String>()
        handoff.attach { "settings" }

        handoff.capture(forSwapped = true)

        assertEquals("settings", handoff.claim(currentSwapped = true))
        assertNull(handoff.claim(currentSwapped = true))
    }

    @Test
    fun `a host composed under another arrangement gets nothing and the capture is dropped`() {
        val handoff = HostHandoff<String>()
        handoff.attach { "settings" }

        handoff.capture(forSwapped = true)

        assertNull(handoff.claim(currentSwapped = false))
        assertNull(handoff.claim(currentSwapped = true))
    }

    @Test
    fun `the outgoing host detaching after the incoming one attached keeps the incoming source`() {
        val handoff = HostHandoff<String>()
        val outgoing: () -> String? = { "outgoing" }
        val incoming: () -> String? = { "incoming" }
        handoff.attach(outgoing)
        handoff.attach(incoming)

        handoff.detach(outgoing)
        handoff.capture(forSwapped = false)

        assertEquals("incoming", handoff.claim(currentSwapped = false))
    }

    @Test
    fun `a capture with no hosting launcher clears an unclaimed earlier one`() {
        val handoff = HostHandoff<String>()
        val source: () -> String? = { "library" }
        handoff.attach(source)
        handoff.capture(forSwapped = true)
        handoff.detach(source)

        handoff.capture(forSwapped = true)

        assertNull(handoff.claim(currentSwapped = true))
    }

    @Test
    fun `the state is read at capture time, not at claim time`() {
        val handoff = HostHandoff<String>()
        var route = "settings"
        handoff.attach { route }

        handoff.capture(forSwapped = false)
        route = "home"

        assertEquals("settings", handoff.claim(currentSwapped = false))
    }
}
