package com.nendo.argosy.ui.input

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * Whether Left at a screen's leftmost boundary opens the navigation drawer: only when no
 * connected gamepad has a physical button that maps to [GamepadEvent.Menu] under the current
 * Start/Select swap.
 */
fun isLeftEdgeDrawerEnabled(connectedSystemButtons: Set<Int>, swapStartSelect: Boolean): Boolean =
    connectedSystemButtons.none { keyCode ->
        mapKeycodeToGamepadEvent(keyCode, swapStartSelect = swapStartSelect) == GamepadEvent.Menu
    }

fun leftEdgeDrawerEnabled(
    connectedSystemButtons: Flow<Set<Int>>,
    swapStartSelect: Flow<Boolean>
): Flow<Boolean> =
    combine(connectedSystemButtons, swapStartSelect, ::isLeftEdgeDrawerEnabled)
        .distinctUntilChanged()
