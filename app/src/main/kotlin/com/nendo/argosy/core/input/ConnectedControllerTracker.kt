package com.nendo.argosy.core.input

import android.content.Context
import android.hardware.input.InputManager
import android.view.InputDevice
import android.view.KeyEvent
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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

    private val _connectedControllerIds = MutableStateFlow(readConnectedControllerIds())
    val connectedControllerIds: StateFlow<Set<String>> = _connectedControllerIds.asStateFlow()

    private val _connectedSystemButtons = MutableStateFlow(readConnectedSystemButtons())

    /**
     * Start and Select keycodes that at least one connected physical gamepad reports having.
     * Empty when no pad is connected or none exposes either button.
     */
    val connectedSystemButtons: StateFlow<Set<Int>> = _connectedSystemButtons.asStateFlow()

    private val listener = object : InputManager.InputDeviceListener {
        override fun onInputDeviceAdded(deviceId: Int) = refresh()
        override fun onInputDeviceChanged(deviceId: Int) = refresh()
        override fun onInputDeviceRemoved(deviceId: Int) = refresh()
    }

    init {
        inputManager.registerInputDeviceListener(listener, null)
    }

    private fun refresh() {
        _connectedControllerIds.value = readConnectedControllerIds()
        _connectedSystemButtons.value = readConnectedSystemButtons()
    }

    private fun physicalGamepads(): List<InputDevice> =
        InputDevice.getDeviceIds()
            .toList()
            .mapNotNull { InputDevice.getDevice(it) }
            .filter { it.isPhysicalGamepad() }

    private fun readConnectedControllerIds(): Set<String> =
        physicalGamepads().mapTo(mutableSetOf()) { controllerIdOf(it) }

    private fun readConnectedSystemButtons(): Set<Int> =
        physicalGamepads().flatMapTo(mutableSetOf()) { device ->
            val present = device.hasKeys(*SYSTEM_BUTTON_KEYCODES)
            SYSTEM_BUTTON_KEYCODES.filterIndexed { index, _ -> present[index] }
        }
}
