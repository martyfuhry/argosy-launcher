package com.nendo.argosy.ui.dualscreen

import com.nendo.argosy.hardware.CompanionInGameState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CompanionSlotGateTest {

    private val inGame = PresentationSlot.InGame(CompanionInGameState(isLoaded = true), emptyList())
    private val detail = PresentationSlot.Detail(CompanionDetail(title = "Metroid Fusion", isGameTitle = true))
    private val platform = PresentationSlot.PlatformShowcase("Game Boy Advance", null, emptyList(), emptyList())

    @Test
    fun `a game's dashboard renders once the in-game slot arrives`() {
        assertEquals(inGame, companionSlotToRender(showsDashboard = true, slot = inGame))
    }

    @Test
    fun `the game hero is held black while the in-game state loads`() {
        assertNull(companionSlotToRender(showsDashboard = true, slot = detail))
    }

    @Test
    fun `a platform showcase is held black while the in-game state loads`() {
        assertNull(companionSlotToRender(showsDashboard = true, slot = platform))
    }

    @Test
    fun `the fallback is held black while the in-game state loads`() {
        assertNull(companionSlotToRender(showsDashboard = true, slot = PresentationSlot.Fallback))
    }

    @Test
    fun `a swapped companion without a game dashboard renders every slot as published`() {
        listOf(inGame, detail, platform, PresentationSlot.Fallback).forEach {
            assertEquals(it, companionSlotToRender(showsDashboard = false, slot = it))
        }
    }
}
