package com.nendo.argosy.hardware

import android.view.Display

object CompanionKeyYield {

    /**
     * Whether the companion window gives up key focus: only while a game runs on another display
     * and this companion is the launcher rather than the showcase, so the controller reaches the
     * game's window instead of the panel the person last touched. A session with no recorded
     * display is on the default display.
     */
    fun yieldsKeys(
        gameActive: Boolean,
        showcaseRole: Boolean,
        emulatorDisplayId: Int?,
        hostDisplayId: Int
    ): Boolean = gameActive && !showcaseRole &&
        (emulatorDisplayId ?: Display.DEFAULT_DISPLAY) != hostDisplayId
}
