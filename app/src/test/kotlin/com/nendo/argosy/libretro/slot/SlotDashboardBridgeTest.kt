package com.nendo.argosy.libretro.slot

import com.nendo.argosy.DualScreenManager
import com.nendo.argosy.ui.dualscreen.dashboard.DashboardQuickState
import com.nendo.argosy.ui.dualscreen.dashboard.SessionControls
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class SlotDashboardBridgeTest {

    private class FakeDashboard : SlotDashboardBridge.Dashboard {
        override var quickActions: DualScreenManager.SessionQuickActions? = null
        val published = mutableListOf<SessionControls>()
        override fun publish(controls: SessionControls) {
            published += controls
        }
    }

    private data class Sent(val slot: Int, val command: DashboardCommand, val stateSlot: Int, val savedAt: Long)

    private val dashboard = FakeDashboard()
    private val sent = mutableListOf<Sent>()
    private var owner: Int? = null
    private val bridge = SlotDashboardBridge(
        dashboard = dashboard,
        ownerSlot = { owner },
        send = { slot, command, stateSlot, savedAt -> sent += Sent(slot, command, stateSlot, savedAt) }
    )

    private fun controls(slotNumber: Int) = SessionControls(
        quickStates = listOf(DashboardQuickState(slotNumber, 100L * slotNumber, "/states/$slotNumber.png")),
        cheatsAvailable = true,
        settingsAvailable = true
    )

    @Test
    fun `the session owner's offer installs forwarding with its controls`() {
        owner = 1
        bridge.offer(1, available = true)
        bridge.publish(1, controls(3))

        assertSame(bridge.forwarding, dashboard.quickActions)
        assertEquals(controls(3), dashboard.published.last())
    }

    @Test
    fun `an offer from a slot that does not own the session leaves the dashboard alone`() {
        owner = 1
        bridge.offer(2, available = true)
        bridge.publish(2, controls(2))

        assertNull(dashboard.quickActions)
        assertTrue(dashboard.published.isEmpty())
    }

    @Test
    fun `actions go to the slot holding the session`() {
        bridge.offer(1, available = true)
        bridge.offer(2, available = true)
        owner = 2
        bridge.refresh()

        bridge.forwarding.loadState(4, 400L)
        bridge.forwarding.quit()

        assertEquals(
            listOf(Sent(2, DashboardCommand.LOAD_STATE, 4, 400L), Sent(2, DashboardCommand.QUIT, 0, 0L)),
            sent
        )
    }

    @Test
    fun `switching the session to another slot republishes that slot's controls`() {
        owner = 1
        bridge.offer(1, available = true)
        bridge.publish(1, controls(1))
        bridge.offer(2, available = true)
        bridge.publish(2, controls(2))

        owner = 2
        bridge.refresh()

        assertSame(bridge.forwarding, dashboard.quickActions)
        assertEquals(controls(2), dashboard.published.last())
    }

    @Test
    fun `a session leaving the slots clears forwarding but not an in-process game's actions`() {
        owner = 1
        bridge.offer(1, available = true)
        owner = null
        bridge.refresh()
        assertNull(dashboard.quickActions)

        val inProcess = mockk<DualScreenManager.SessionQuickActions>(relaxed = true)
        dashboard.quickActions = inProcess
        bridge.refresh()
        bridge.offer(2, available = true)
        assertSame(inProcess, dashboard.quickActions)
    }

    @Test
    fun `withdrawing or forgetting the owner's offer clears forwarding and stops sending`() {
        owner = 1
        bridge.offer(1, available = true)
        bridge.offer(1, available = false)
        assertNull(dashboard.quickActions)

        bridge.offer(1, available = true)
        bridge.forget(1)
        assertNull(dashboard.quickActions)

        bridge.forwarding.quickSave()
        assertTrue(sent.isEmpty())
    }

    @Test
    fun `controls from a slot that never offered are ignored`() {
        owner = 1
        bridge.publish(1, controls(1))
        assertNull(dashboard.quickActions)
        assertTrue(dashboard.published.isEmpty())
    }

    @Test
    fun `detach releases forwarding`() {
        owner = 1
        bridge.offer(1, available = true)
        bridge.detach()
        assertNull(dashboard.quickActions)
    }
}
