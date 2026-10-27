package com.nendo.argosy.hardware

import org.junit.Assert.assertEquals
import org.junit.Test

class CompanionKeyFocusDecisionTest {

    private val companionDisplay = 2
    private val gameDisplay = 0

    private fun decide(
        isGameActive: Boolean = true,
        isShowcaseRole: Boolean = false,
        emulatorDisplayId: Int? = gameDisplay,
        hostDisplayId: Int = companionDisplay,
        focusHandedHere: Boolean = false
    ) = CompanionKeyFocusDecision.decide(
        isGameActive = isGameActive,
        isShowcaseRole = isShowcaseRole,
        emulatorDisplayId = emulatorDisplayId,
        hostDisplayId = hostDisplayId,
        focusHandedHere = focusHandedHere
    )

    @Test
    fun `a game on another display makes the companion yield the pad`() {
        assertEquals(CompanionKeyFocus.YIELDED, decide())
    }

    @Test
    fun `a game with no recorded display counts as on the default display`() {
        assertEquals(CompanionKeyFocus.YIELDED, decide(emulatorDisplayId = null))
        assertEquals(CompanionKeyFocus.OWN, decide(emulatorDisplayId = null, hostDisplayId = 0))
    }

    @Test
    fun `no game running keeps the pad on the companion`() {
        assertEquals(CompanionKeyFocus.OWN, decide(isGameActive = false))
        assertEquals(CompanionKeyFocus.OWN, decide(isGameActive = false, focusHandedHere = true))
    }

    @Test
    fun `the showcase role never yields`() {
        assertEquals(CompanionKeyFocus.OWN, decide(isShowcaseRole = true))
    }

    @Test
    fun `a game on the companion's own display never yields`() {
        assertEquals(CompanionKeyFocus.OWN, decide(emulatorDisplayId = companionDisplay))
    }

    @Test
    fun `focus handed to the companion display makes it focusable during the game`() {
        assertEquals(CompanionKeyFocus.HANDED_HERE, decide(focusHandedHere = true))
    }
}
