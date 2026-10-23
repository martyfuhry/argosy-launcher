package com.nendo.argosy.ui.dualscreen

/**
 * Whether the launcher's first resume hands key focus to the companion. A game that is launching
 * or running keeps it: raising the companion then moves the device's focused display off the game,
 * and the companion yields focus during a session, so the controller reaches no window at all.
 */
fun companionTakesFocusOnFirstResume(sessionActive: Boolean, gameActive: Boolean, launchLive: Boolean): Boolean =
    !sessionActive && !gameActive && !launchLive
