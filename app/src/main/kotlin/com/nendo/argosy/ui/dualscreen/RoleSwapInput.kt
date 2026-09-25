package com.nendo.argosy.ui.dualscreen

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.nendo.argosy.DualScreenManagerHolder

/**
 * Whether select belongs to the role swap. A screen binding select to an action of its own
 * returns it unhandled while this is true, so the app-level handler performs the swap. False
 * while a game runs, when no swap can happen and select keeps the screen's own action.
 */
fun selectSwapsRoles(): Boolean =
    DualScreenManagerHolder.instance
        ?.let {
            it.isDualScreenDevice.value && it.hasPresentationScreen.value && !it.swappedIsGameActive.value
        } == true

/**
 * [selectSwapsRoles] for a footer, recomposing when any of its inputs changes.
 */
@Composable
fun selectSwapsRolesState(): Boolean {
    val manager = DualScreenManagerHolder.instance ?: return false
    val dualScreen by manager.isDualScreenDevice.collectAsState()
    val hasPresentation by manager.hasPresentationScreen.collectAsState()
    val gameActive by manager.swappedIsGameActive.collectAsState()
    return dualScreen && hasPresentation && !gameActive
}
