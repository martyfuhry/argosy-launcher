package com.nendo.argosy.ui.dualscreen

/**
 * Whether input reaching [ownDisplay] belongs to the running game: its emulator is on another
 * display, and no companion of ours is resumed in front of that display.
 */
fun isInputForGameOnOtherDisplay(
    emulatorDisplay: Int?,
    ownDisplay: Int?,
    companionFrontedDisplays: Set<Int>
): Boolean {
    if (emulatorDisplay == null || ownDisplay == null) return false
    return emulatorDisplay != ownDisplay && emulatorDisplay !in companionFrontedDisplays
}
