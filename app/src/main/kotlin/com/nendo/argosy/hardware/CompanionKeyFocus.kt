package com.nendo.argosy.hardware

import android.view.Display

enum class CompanionKeyFocus {
    OWN,
    YIELDED,
    HANDED_HERE
}

object CompanionKeyFocusDecision {

    /**
     * Yields key focus while a game runs on another display, unless the system has put focus here
     * ([focusHandedHere]), since a focused display with no focusable window ANRs.
     */
    fun decide(
        isGameActive: Boolean,
        isShowcaseRole: Boolean,
        emulatorDisplayId: Int?,
        hostDisplayId: Int,
        focusHandedHere: Boolean
    ): CompanionKeyFocus = when {
        !isGameActive || isShowcaseRole -> CompanionKeyFocus.OWN
        (emulatorDisplayId ?: Display.DEFAULT_DISPLAY) == hostDisplayId -> CompanionKeyFocus.OWN
        focusHandedHere -> CompanionKeyFocus.HANDED_HERE
        else -> CompanionKeyFocus.YIELDED
    }
}
