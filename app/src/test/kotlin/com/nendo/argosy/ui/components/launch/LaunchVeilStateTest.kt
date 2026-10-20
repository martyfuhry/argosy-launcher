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
    fun launchingStepVeilsWithNoGameYet() {
        assertTrue(veilFor(progress(LaunchStep.Launching), gameActive = false))
    }

    @Test
    fun activeGameVeilsWithNoLaunchInProgress() {
        assertTrue(veilFor(null, gameActive = true))
    }

    @Test
    fun activeGameVeilsOverAQuietStep() {
        quietSteps.forEach { step -> assertTrue(veilFor(progress(step), gameActive = true)) }
    }

    @Test
    fun quietStepWithNoGameLeavesTheShowcaseUp() {
        quietSteps.forEach { step -> assertFalse("$step", veilFor(progress(step), gameActive = false)) }
    }

    @Test
    fun noLaunchAndNoGameLeavesTheShowcaseUp() {
        assertFalse(veilFor(null, gameActive = false))
    }

    @Test
    fun displayTheGameOpensOnIsVeiledWhileLaunching() {
        assertTrue(veilsDisplay(progress(LaunchStep.Launching), false, gameDisplay, gameDisplay))
    }

    @Test
    fun displayTheGameRunsOnStaysVeiledForTheSession() {
        assertTrue(veilsDisplay(null, true, gameDisplay, gameDisplay))
    }

    @Test
    fun dashboardOnAnotherDisplayIsNeverVeiled() {
        assertFalse(veilsDisplay(null, true, gameDisplay, otherDisplay))
        assertFalse(veilsDisplay(progress(LaunchStep.Launching), true, gameDisplay, otherDisplay))
    }

    @Test
    fun unknownGameDisplayVeilsNothing() {
        assertFalse(veilsDisplay(progress(LaunchStep.Launching), true, null, gameDisplay))
        assertFalse(veilsDisplay(progress(LaunchStep.Launching), true, gameDisplay, null))
    }

    @Test
    fun sessionEndLiftsTheVeilOnTheGameDisplay() {
        assertFalse(veilsDisplay(null, false, gameDisplay, gameDisplay))
    }

    @Test
    fun singleScreenOverlayDarkensOnceTheGameIsLaunching() {
        assertTrue(darkensForLaunch(progress(LaunchStep.Launching), hasPresentationSurface = false))
    }

    @Test
    fun singleScreenOverlayStaysCalmBeforeLaunching() {
        quietSteps.forEach { step ->
            assertFalse("$step", darkensForLaunch(progress(step), hasPresentationSurface = false))
        }
        assertFalse(darkensForLaunch(null, hasPresentationSurface = false))
    }

    @Test
    fun overlayBesideAPresentationSurfaceNeverDarkens() {
        assertFalse(darkensForLaunch(progress(LaunchStep.Launching), hasPresentationSurface = true))
        quietSteps.forEach { step ->
            assertFalse("$step", darkensForLaunch(progress(step), hasPresentationSurface = true))
        }
    }
}
