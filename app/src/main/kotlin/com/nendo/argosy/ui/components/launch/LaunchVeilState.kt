package com.nendo.argosy.ui.components.launch

import com.nendo.argosy.domain.model.LaunchPhase
import com.nendo.argosy.domain.model.LaunchProgress
import com.nendo.argosy.domain.model.phase

/**
 * Whether a game is on its way in or running: the launch has fired its intent, or a session is
 * live.
 */
fun veilFor(progress: LaunchProgress?, gameActive: Boolean): Boolean =
    progress?.step?.phase == LaunchPhase.LAUNCHING || gameActive

/**
 * Whether the presentation surface on [displayId] goes black: only the display the game opens on
 * or runs on, [gameDisplayId], and only while [veilFor] holds. A dashboard on any other display
 * stays clear.
 */
fun veilsDisplay(
    progress: LaunchProgress?,
    gameActive: Boolean,
    gameDisplayId: Int?,
    displayId: Int?
): Boolean = gameDisplayId != null && gameDisplayId == displayId && veilFor(progress, gameActive)

/**
 * Whether the control surface's launch overlay fades to black as the game opens: only once the
 * launch is LAUNCHING, and only with no presentation surface to take the cut instead.
 */
fun darkensForLaunch(progress: LaunchProgress?, hasPresentationSurface: Boolean): Boolean =
    !hasPresentationSurface && progress?.step?.phase == LaunchPhase.LAUNCHING
