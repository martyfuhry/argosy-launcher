package com.nendo.argosy.ui.components.launch

import com.nendo.argosy.domain.model.LaunchPhase
import com.nendo.argosy.domain.model.LaunchProgress
import com.nendo.argosy.domain.model.phase

/**
 * Whether a game is on its way in: the launch has published LAUNCHING and its window has been or
 * is about to be requested.
 */
fun veilFor(progress: LaunchProgress?): Boolean =
    progress?.step?.phase == LaunchPhase.LAUNCHING

/**
 * Whether the presentation surface on [displayId] goes black: only the display the launch placed
 * the game on, [gameDisplayId], and only while [veilFor] holds. A running session alone never
 * holds it.
 */
fun veilsDisplay(
    progress: LaunchProgress?,
    gameDisplayId: Int?,
    displayId: Int?
): Boolean = gameDisplayId != null && gameDisplayId == displayId && veilFor(progress)

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
