package com.nendo.argosy.ui.dualscreen

/**
 * The slot the companion renders, or null to hold black. While the companion shows the presentation
 * side because a game is active, any slot but [PresentationSlot.InGame] is the launcher's leftover
 * from before the launch, so it is held back until the in-game state has loaded.
 */
fun companionSlotToRender(showsDashboard: Boolean, slot: PresentationSlot): PresentationSlot? =
    if (showsDashboard && slot !is PresentationSlot.InGame) null else slot

/**
 * How long the in-game dashboard fades in over the companion's black hold: [arrivalMs] while the
 * companion arrives from the hold, zero under reduced motion or when no hold applies.
 */
fun dashboardArrivalMs(showsDashboard: Boolean, reduced: Boolean, arrivalMs: Int): Int =
    if (showsDashboard && !reduced) arrivalMs else 0
