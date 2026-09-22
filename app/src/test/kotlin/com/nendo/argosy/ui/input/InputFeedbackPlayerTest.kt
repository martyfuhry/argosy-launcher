package com.nendo.argosy.ui.input

import com.nendo.argosy.core.input.SoundType
import io.mockk.mockk
import io.mockk.verify
import org.junit.Test

class InputFeedbackPlayerTest {

    private val haptics = mockk<HapticFeedbackManager>(relaxed = true)
    private val player = InputFeedbackPlayer(hapticManager = haptics)

    @Test
    fun `a focus move ticks with the focus pattern`() {
        listOf(GamepadEvent.Up, GamepadEvent.Down, GamepadEvent.Left, GamepadEvent.Right).forEach {
            player.play(it, InputResult.handled())
        }

        verify(exactly = 4) { haptics.vibrate(HapticPattern.FOCUS_CHANGE) }
        verify(exactly = 0) { haptics.vibrate(HapticPattern.SECTION_CHANGE) }
    }

    @Test
    fun `a section or trigger page ticks with the section pattern`() {
        listOf(
            GamepadEvent.PrevSection,
            GamepadEvent.NextSection,
            GamepadEvent.PrevTrigger,
            GamepadEvent.NextTrigger
        ).forEach {
            player.play(it, InputResult.handled())
        }

        verify(exactly = 4) { haptics.vibrate(HapticPattern.SECTION_CHANGE) }
        verify(exactly = 0) { haptics.vibrate(HapticPattern.FOCUS_CHANGE) }
    }

    @Test
    fun `holding against a boundary buzzes once`() {
        repeat(3) { player.play(GamepadEvent.Down, InputResult.handled(SoundType.BOUNDARY)) }

        verify(exactly = 1) { haptics.vibrate(HapticPattern.FOCUS_CHANGE) }
    }

    @Test
    fun `an unhandled event stays silent`() {
        player.play(GamepadEvent.NextSection, InputResult(handled = false))

        verify(exactly = 0) { haptics.vibrate(any()) }
    }
}
