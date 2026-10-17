package com.nendo.argosy.core.input

import android.content.Context
import android.hardware.input.InputManager
import android.view.InputDevice
import android.view.InputEvent
import android.view.KeyEvent
import com.nendo.argosy.util.SafeCoroutineScope
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject
import javax.inject.Singleton

fun controllerIdOf(device: InputDevice): String =
    "${device.vendorId}:${device.productId}:${device.descriptor}"

private val SYSTEM_BUTTON_KEYCODES = intArrayOf(
    KeyEvent.KEYCODE_BUTTON_START,
    KeyEvent.KEYCODE_BUTTON_SELECT
)

@Singleton
class ConnectedControllerTracker @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val inputManager = context.getSystemService(Context.INPUT_SERVICE) as InputManager
    private val scope = SafeCoroutineScope(Dispatchers.IO, "ConnectedControllerTracker")
    private val activeState = ActiveControllerState()
    private val deviceGeneration = MutableStateFlow(0)

    private val _connectedControllerIds = MutableStateFlow(readConnectedControllerIds())
    val connectedControllerIds: StateFlow<Set<String>> = _connectedControllerIds.asStateFlow()

    private var systemButtonsCountVirtualPad = false
    private val _connectedSystemButtons = MutableStateFlow(readConnectedSystemButtons())

    /**
     * Start and Select keycodes that at least one connected pad has. A physical pad answers through
     * hasKeys; the firmware virtual pad counts as having both once it has sent pad input, since
     * Android's virtual device has no key bitmask to answer from. Empty when no pad is connected or
     * none exposes either button.
     */
    val connectedSystemButtons: StateFlow<Set<Int>> = _connectedSystemButtons.asStateFlow()

    val activeController: StateFlow<ActiveController?> = activeState.active

    /**
     * Layout of [activeController], or of the first connected pad until one has sent input.
     * Re-detected whenever the active pad changes or any input device is added, changed or removed.
     */
    val activeLayout: StateFlow<DetectedLayout?> =
        combine(activeController, deviceGeneration) { active, _ -> detectActive(active).layout }
            .flowOn(Dispatchers.IO)
            .stateIn(scope, SharingStarted.Eagerly, detectActive(null).layout)

    private val listener = object : InputManager.InputDeviceListener {
        override fun onInputDeviceAdded(deviceId: Int) = refresh()
        override fun onInputDeviceChanged(deviceId: Int) = refresh()
        override fun onInputDeviceRemoved(deviceId: Int) = refresh()
    }

    init {
        inputManager.registerInputDeviceListener(listener, null)
    }

    fun noteInput(event: InputEvent) {
        activeState.noteInput(event)
        if (VirtualPad.seen && !systemButtonsCountVirtualPad) refresh()
    }

    /**
     * Freezes [activeController] against input while a mapping capture waits for its press.
     */
    fun holdActiveController(held: Boolean) = activeState.setHeld(held)

    fun activeDevice(): InputDevice? = deviceOf(activeController.value)

    fun detectActive(): DetectionResult = detectActive(activeController.value)

    fun refresh() {
        _connectedControllerIds.value = readConnectedControllerIds()
        _connectedSystemButtons.value = readConnectedSystemButtons()
        activeState.onPadsChanged(connectedPads().map { it.toActiveController() })
        deviceGeneration.value++
    }

    private fun deviceOf(active: ActiveController?): InputDevice? =
        active?.let { InputDevice.getDevice(it.deviceId) }?.takeIf { it.actsAsGamepad() }

    private fun detectActive(active: ActiveController?): DetectionResult =
        ControllerDetector.detectFor(deviceOf(active))

    private fun connectedPads(): List<InputDevice> =
        InputDevice.getDeviceIds()
            .toList()
            .mapNotNull { InputDevice.getDevice(it) }
            .filter { it.actsAsGamepad() }

    private fun readConnectedControllerIds(): Set<String> =
        InputDevice.getDeviceIds()
            .toList()
            .mapNotNull { InputDevice.getDevice(it) }
            .filter { it.isPhysicalGamepad() }
            .mapTo(mutableSetOf()) { controllerIdOf(it) }

    private fun readConnectedSystemButtons(): Set<Int> {
        systemButtonsCountVirtualPad = VirtualPad.seen
        return connectedPads().flatMapTo(mutableSetOf()) { device -> device.systemButtons() }
    }

    private fun InputDevice.systemButtons(): List<Int> {
        if (isVirtual) return SYSTEM_BUTTON_KEYCODES.toList()
        val present = hasKeys(*SYSTEM_BUTTON_KEYCODES)
        return SYSTEM_BUTTON_KEYCODES.filterIndexed { index, _ -> present[index] }
    }
}
