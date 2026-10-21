package com.nendo.argosy.ui.components.launch

import com.nendo.argosy.domain.model.LaunchPromptOption
import com.nendo.argosy.domain.model.LaunchProgress
import com.nendo.argosy.domain.model.LaunchStep
import com.nendo.argosy.domain.model.SyncProgress
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LaunchVeilStateTest {

    private val gameDisplay = 1
    private val otherDisplay = 0

    private fun progress(step: LaunchStep) = LaunchProgress(
        gameTitle = "Game",
        step = step,
        gameId = 1L,
        stepStartedAtMs = 10_000L
    )

    private val quietSteps = listOf(
        LaunchStep.FinishingPrevious,
        LaunchStep.RecapLoading,
        LaunchStep.Save(SyncProgress.PreLaunch.CheckingSave(null, found = null)),
        LaunchStep.DownloadingCore(fraction = 0.5f, totalBytes = 1_000_000L),
        LaunchStep.Starting,
        LaunchStep.PreparingSystemFiles,
        LaunchStep.PreparingUi,
        LaunchStep.ChooseLauncher(listOf(LaunchPromptOption.LAUNCH_WITH_GAMENATIVE))
    )

    @Test
    fun launchingStepVeils() {
        assertTrue(veilFor(progress(LaunchStep.Launching)))
    }

    @Test
    fun quietStepLeavesTheShowcaseUp() {
        quietSteps.forEach { step -> assertFalse("$step", veilFor(progress(step))) }
    }

    @Test
    fun noLaunchLeavesTheShowcaseUp() {
        assertFalse(veilFor(null))
    }

    @Test
    fun displayTheGameOpensOnIsVeiledWhileLaunching() {
        assertTrue(veilsDisplay(progress(LaunchStep.Launching), gameDisplay, gameDisplay))
    }

    @Test
    fun displayTheGameWasSentToClearsOnceLaunchingEnds() {
        assertFalse(veilsDisplay(null, gameDisplay, gameDisplay))
    }

    @Test
    fun displayTheGameWasSentToClearsOverAQuietStep() {
        quietSteps.forEach { step -> assertFalse("$step", veilsDisplay(progress(step), gameDisplay, gameDisplay)) }
    }

    @Test
    fun dashboardOnAnotherDisplayIsNeverVeiled() {
        assertFalse(veilsDisplay(null, gameDisplay, otherDisplay))
        assertFalse(veilsDisplay(progress(LaunchStep.Launching), gameDisplay, otherDisplay))
    }

    @Test
    fun unknownGameDisplayVeilsNothing() {
        assertFalse(veilsDisplay(progress(LaunchStep.Launching), null, gameDisplay))
        assertFalse(veilsDisplay(progress(LaunchStep.Launching), gameDisplay, null))
        assertFalse(veilsDisplay(progress(LaunchStep.Launching), null, null))
    }

    @Test
    fun singleScreenOverlayDarkensOnceTheGameIsLaunching() {
        assertTrue(darkensForLaunch(progress(LaunchStep.Launching), gameOpensHere = true))
    }

    @Test
    fun singleScreenOverlayStaysCalmBeforeLaunching() {
        quietSteps.forEach { step ->
            assertFalse("$step", darkensForLaunch(progress(step), gameOpensHere = true))
        }
        assertFalse(darkensForLaunch(null, gameOpensHere = true))
    }

    @Test
    fun overlayOnASurfaceTheGameDoesNotOpenOnNeverDarkens() {
        assertFalse(darkensForLaunch(progress(LaunchStep.Launching), gameOpensHere = false))
        quietSteps.forEach { step ->
            assertFalse("$step", darkensForLaunch(progress(step), gameOpensHere = false))
        }
    }

    @Test
    fun gameOpensOnTheOnlySurface() {
        assertTrue(opensOnSurface(hasPresentationSurface = false, gameDisplayId = null, surfaceDisplayId = otherDisplay))
        assertTrue(opensOnSurface(hasPresentationSurface = false, gameDisplayId = gameDisplay, surfaceDisplayId = otherDisplay))
    }

    @Test
    fun gamePlacedOnTheControlDisplayOpensOnTheControlSurface() {
        assertTrue(opensOnSurface(hasPresentationSurface = true, gameDisplayId = otherDisplay, surfaceDisplayId = otherDisplay))
    }

    @Test
    fun gamePlacedOnThePresentationDisplayLeavesTheControlSurfaceCalm() {
        assertFalse(opensOnSurface(hasPresentationSurface = true, gameDisplayId = gameDisplay, surfaceDisplayId = otherDisplay))
    }

    @Test
    fun unplacedGameBesideAPresentationSurfaceLeavesTheControlSurfaceCalm() {
        assertFalse(opensOnSurface(hasPresentationSurface = true, gameDisplayId = null, surfaceDisplayId = otherDisplay))
        assertFalse(opensOnSurface(hasPresentationSurface = true, gameDisplayId = gameDisplay, surfaceDisplayId = null))
    }
}
