package com.nendo.argosy.ui.dualscreen

import com.nendo.argosy.DualScreenManagerHolder

/**
 * Whether select belongs to the role swap. A screen binding select to an action of its own
 * returns it unhandled while this is true, so the app-level handler performs the swap.
 */
fun selectSwapsRoles(): Boolean =
    DualScreenManagerHolder.instance
        ?.let { it.isDualScreenDevice.value && it.hasPresentationScreen.value } == true
