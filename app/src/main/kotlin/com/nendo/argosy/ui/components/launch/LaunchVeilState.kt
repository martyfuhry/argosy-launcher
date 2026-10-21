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
 * Whether the game opens on the control surface on [surfaceDisplayId]: always with no
 * presentation surface beside it, and otherwise only when [gameDisplayId], the display the launch
 * placed the game on, is this one.
 */
fun opensOnSurface(hasPresentationSurface: Boolean, gameDisplayId: Int?, surfaceDisplayId: Int?): Boolean =
    !hasPresentationSurface || (gameDisplayId != null && gameDisplayId == surfaceDisplayId)

/**
 * Whether a launch overlay fades to black as the game opens: only once the launch is LAUNCHING,
 * and only on the surface the game opens on, [gameOpensHere].
 */
fun darkensForLaunch(progress: LaunchProgress?, gameOpensHere: Boolean): Boolean =
    gameOpensHere && progress?.step?.phase == LaunchPhase.LAUNCHING
