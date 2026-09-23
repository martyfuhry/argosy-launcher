package com.nendo.argosy.ui.input

/**
 * Edge detector for one two-axis directional input. [update] returns the direction entered on
 * this sample, or null when the sample changes nothing or returns to neutral. A direction is
 * entered at [enterThreshold] and left only below [exitThreshold].
 */
class AxisDirectionTracker(
    private val enterThreshold: Float = DEFAULT_ENTER_THRESHOLD,
    private val exitThreshold: Float = DEFAULT_EXIT_THRESHOLD
) {
    var direction: GamepadEvent? = null
        private set

    fun update(x: Float, y: Float): GamepadEvent? {
        val previous = direction
        if (previous != null && stillHeld(previous, x, y)) return null

        val next = when {
            y <= -enterThreshold -> GamepadEvent.Up
            y >= enterThreshold -> GamepadEvent.Down
            x <= -enterThreshold -> GamepadEvent.Left
            x >= enterThreshold -> GamepadEvent.Right
            else -> null
        }
        direction = next
        return next
    }

    fun reset() {
        direction = null
    }

    private fun stillHeld(held: GamepadEvent, x: Float, y: Float): Boolean = when (held) {
        GamepadEvent.Up -> y <= -exitThreshold
        GamepadEvent.Down -> y >= exitThreshold
        GamepadEvent.Left -> x <= -exitThreshold
        GamepadEvent.Right -> x >= exitThreshold
        else -> false
    }

    companion object {
        const val DEFAULT_ENTER_THRESHOLD = 0.5f
        const val DEFAULT_EXIT_THRESHOLD = 0.3f
    }
}
