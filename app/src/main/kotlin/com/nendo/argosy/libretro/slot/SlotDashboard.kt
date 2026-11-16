package com.nendo.argosy.libretro.slot

import android.os.Bundle
import com.nendo.argosy.DualScreenManager
import com.nendo.argosy.ui.dualscreen.dashboard.DashboardQuickState
import com.nendo.argosy.ui.dualscreen.dashboard.SessionControls

internal enum class DashboardCommand { QUICK_SAVE, LOAD_STATE, SCREENSHOT, OPEN_CHEATS, OPEN_SETTINGS, QUIT }

internal fun DashboardCommand.runOn(
    actions: DualScreenManager.SessionQuickActions,
    stateSlot: Int,
    savedAtMillis: Long
) = when (this) {
    DashboardCommand.QUICK_SAVE -> actions.quickSave()
    DashboardCommand.LOAD_STATE -> actions.loadState(stateSlot, savedAtMillis)
    DashboardCommand.SCREENSHOT -> actions.screenshot()
    DashboardCommand.OPEN_CHEATS -> actions.openCheats()
    DashboardCommand.OPEN_SETTINGS -> actions.openGameSettings()
    DashboardCommand.QUIT -> actions.quit()
}

internal object SlotDashboardWire {

    fun writeCommand(command: DashboardCommand, stateSlot: Int, savedAtMillis: Long, into: Bundle) {
        into.putInt(SlotProtocol.KEY_COMMAND, command.ordinal)
        into.putInt(SlotProtocol.KEY_STATE_SLOT, stateSlot)
        into.putLong(SlotProtocol.KEY_SAVED_AT, savedAtMillis)
    }

    /**
     * Runs the command [data] carries on [actions]. False when [data] names no known command.
     */
    fun runCommand(data: Bundle, actions: DualScreenManager.SessionQuickActions): Boolean {
        val command = DashboardCommand.entries.getOrNull(data.getInt(SlotProtocol.KEY_COMMAND, -1)) ?: return false
        command.runOn(actions, data.getInt(SlotProtocol.KEY_STATE_SLOT, 0), data.getLong(SlotProtocol.KEY_SAVED_AT, 0L))
        return true
    }

    fun writeControls(controls: SessionControls, into: Bundle) {
        into.putIntArray(SlotProtocol.KEY_STATE_SLOTS, controls.quickStates.map { it.slotNumber }.toIntArray())
        into.putLongArray(SlotProtocol.KEY_STATE_TIMES, controls.quickStates.map { it.savedAtMillis }.toLongArray())
        into.putStringArrayList(
            SlotProtocol.KEY_STATE_SHOTS,
            ArrayList(controls.quickStates.map { it.screenshotPath.orEmpty() })
        )
        into.putBoolean(SlotProtocol.KEY_CHEATS, controls.cheatsAvailable)
        into.putBoolean(SlotProtocol.KEY_SETTINGS, controls.settingsAvailable)
        into.putBoolean(SlotProtocol.KEY_SCREENSHOT, controls.screenshotAvailable)
        into.putBoolean(SlotProtocol.KEY_DISC_SWAP, controls.discSwapAvailable)
        into.putBoolean(SlotProtocol.KEY_STATES, controls.statesAvailable)
        into.putBoolean(SlotProtocol.KEY_ACHIEVEMENTS, controls.achievementsAvailable)
    }

    fun readControls(from: Bundle): SessionControls {
        val defaults = SessionControls()
        val slots = from.getIntArray(SlotProtocol.KEY_STATE_SLOTS) ?: IntArray(0)
        val times = from.getLongArray(SlotProtocol.KEY_STATE_TIMES) ?: LongArray(0)
        val shots = from.getStringArrayList(SlotProtocol.KEY_STATE_SHOTS)
        return SessionControls(
            quickStates = slots.indices.filter { it < times.size }.map { index ->
                DashboardQuickState(
                    slotNumber = slots[index],
                    savedAtMillis = times[index],
                    screenshotPath = shots?.getOrNull(index)?.takeIf { it.isNotEmpty() }
                )
            },
            cheatsAvailable = from.getBoolean(SlotProtocol.KEY_CHEATS, defaults.cheatsAvailable),
            settingsAvailable = from.getBoolean(SlotProtocol.KEY_SETTINGS, defaults.settingsAvailable),
            screenshotAvailable = from.getBoolean(SlotProtocol.KEY_SCREENSHOT, defaults.screenshotAvailable),
            discSwapAvailable = from.getBoolean(SlotProtocol.KEY_DISC_SWAP, defaults.discSwapAvailable),
            statesAvailable = from.getBoolean(SlotProtocol.KEY_STATES, defaults.statesAvailable),
            achievementsAvailable = from.getBoolean(SlotProtocol.KEY_ACHIEVEMENTS, defaults.achievementsAvailable)
        )
    }
}

/**
 * The second-screen dashboard's door into games running in slot processes. Each slot offers its
 * quick actions and publishes its controls here; while the play session belongs to a slot that
 * offers them, the dashboard holds [forwarding], which sends every action to that slot.
 */
internal class SlotDashboardBridge(
    private val dashboard: Dashboard,
    private val ownerSlot: () -> Int?,
    private val send: (slotIndex: Int, command: DashboardCommand, stateSlot: Int, savedAtMillis: Long) -> Unit
) {

    interface Dashboard {
        var quickActions: DualScreenManager.SessionQuickActions?
        fun publish(controls: SessionControls)
    }

    private val offers = mutableMapOf<Int, SessionControls>()

    val forwarding: DualScreenManager.SessionQuickActions = object : DualScreenManager.SessionQuickActions {
        override fun quickSave() = forward(DashboardCommand.QUICK_SAVE)
        override fun loadState(slotNumber: Int, savedAtMillis: Long) =
            forward(DashboardCommand.LOAD_STATE, slotNumber, savedAtMillis)
        override fun screenshot() = forward(DashboardCommand.SCREENSHOT)
        override fun openCheats() = forward(DashboardCommand.OPEN_CHEATS)
        override fun openGameSettings() = forward(DashboardCommand.OPEN_SETTINGS)
        override fun quit() = forward(DashboardCommand.QUIT)
    }

    fun offer(slotIndex: Int, available: Boolean) {
        if (available) offers.getOrPut(slotIndex) { SessionControls() } else offers.remove(slotIndex)
        refresh(slotIndex)
    }

    fun publish(slotIndex: Int, controls: SessionControls) {
        if (slotIndex !in offers) return
        offers[slotIndex] = controls
        refresh(slotIndex)
    }

    fun forget(slotIndex: Int) {
        if (offers.remove(slotIndex) != null) refresh(slotIndex)
    }

    /**
     * Points the dashboard at the session owner's slot. A call naming [changedSlot] does nothing
     * unless that slot owns the session or the dashboard holds [forwarding].
     */
    fun refresh(changedSlot: Int? = null) {
        val owner = activeSlot()
        val holdsForwarding = dashboard.quickActions === forwarding
        if (changedSlot != null && changedSlot != ownerSlot() && !holdsForwarding) return
        if (owner == null) {
            if (holdsForwarding) dashboard.quickActions = null
            return
        }
        if (!holdsForwarding) dashboard.quickActions = forwarding
        dashboard.publish(offers.getValue(owner))
    }

    fun detach() {
        offers.clear()
        if (dashboard.quickActions === forwarding) dashboard.quickActions = null
    }

    private fun activeSlot(): Int? = ownerSlot()?.takeIf { it in offers }

    private fun forward(command: DashboardCommand, stateSlot: Int = 0, savedAtMillis: Long = 0L) {
        val owner = activeSlot() ?: return
        send(owner, command, stateSlot, savedAtMillis)
    }
}
