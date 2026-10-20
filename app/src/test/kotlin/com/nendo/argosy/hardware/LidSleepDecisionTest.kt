package com.nendo.argosy.hardware

import org.junit.Assert.assertEquals
import org.junit.Test

class LidSleepDecisionTest {

    private val closedAt = 10_000L
    private val wokeAt = 11_200L
    private val refreshedAt = 11_500L

    private fun strayWake(
        enabled: Boolean = true,
        lidClosed: Boolean = true,
        closeConfirmed: Boolean = true,
        interactive: Boolean = true,
        lidReadAt: Long = refreshedAt,
        screenOnAt: Long = wokeAt,
        staleRetries: Int = 0,
        externalDisplayInUse: Boolean = false,
        attemptsInWindow: Int = 0,
        serviceReady: Boolean = true
    ) = LidSleepState(
        enabled = enabled,
        lidClosed = lidClosed,
        closeConfirmed = closeConfirmed,
        interactive = interactive,
        lidReadAt = lidReadAt,
        screenOnAt = screenOnAt,
        staleRetries = staleRetries,
        externalDisplayInUse = externalDisplayInUse,
        attemptsInWindow = attemptsInWindow,
        serviceReady = serviceReady
    )

    private fun verdict(state: LidSleepState) = LidSleepDecision.decide(state)

    @Test
    fun `a button wake under a closed lid is answered with sleep`() {
        assertEquals(LidSleepVerdict.SLEEP, verdict(strayWake()))
    }

    @Test
    fun `the close itself sleeps once the system has confirmed it sleeps on close`() {
        assertEquals(LidSleepVerdict.SLEEP, verdict(strayWake(lidReadAt = closedAt, screenOnAt = 0L)))
    }

    @Test
    fun `an open lid is left alone`() {
        assertEquals(LidSleepVerdict.IDLE, verdict(strayWake(lidClosed = false)))
    }

    @Test
    fun `a close the system never slept on is left alone`() {
        assertEquals(LidSleepVerdict.IDLE, verdict(strayWake(closeConfirmed = false)))
    }

    @Test
    fun `a sleeping device is left alone`() {
        assertEquals(LidSleepVerdict.IDLE, verdict(strayWake(interactive = false)))
    }

    @Test
    fun `the setting off is left alone`() {
        assertEquals(LidSleepVerdict.IDLE, verdict(strayWake(enabled = false)))
    }

    @Test
    fun `a reading older than the wake is refreshed before acting`() {
        assertEquals(LidSleepVerdict.RETRY_READING, verdict(strayWake(lidReadAt = closedAt)))
    }

    @Test
    fun `a reading that never refreshes after the wake does not sleep`() {
        assertEquals(
            LidSleepVerdict.STALE_READING,
            verdict(strayWake(lidReadAt = closedAt, staleRetries = LidSleepDecision.MAX_STALE_RETRIES))
        )
    }

    @Test
    fun `a lit external display keeps the device awake`() {
        assertEquals(LidSleepVerdict.EXTERNAL_DISPLAY, verdict(strayWake(externalDisplayInUse = true)))
    }

    @Test
    fun `attempts are rate limited per window`() {
        assertEquals(
            LidSleepVerdict.RATE_LIMITED,
            verdict(strayWake(attemptsInWindow = LidSleepDecision.MAX_ATTEMPTS_PER_WINDOW))
        )
        assertEquals(
            LidSleepVerdict.SLEEP,
            verdict(strayWake(attemptsInWindow = LidSleepDecision.MAX_ATTEMPTS_PER_WINDOW - 1))
        )
    }

    @Test
    fun `without the accessibility service nothing is requested`() {
        assertEquals(LidSleepVerdict.SERVICE_OFF, verdict(strayWake(serviceReady = false)))
    }

    @Test
    fun `a screen-off right after the close arms the guard`() {
        assertEquals(true, LidSleepDecision.armsOnScreenOff(closedAt = closedAt, screenOffAt = closedAt + 200))
        assertEquals(
            true,
            LidSleepDecision.armsOnScreenOff(closedAt = closedAt, screenOffAt = closedAt + LidSleepDecision.ARM_WINDOW_MS)
        )
    }

    @Test
    fun `a screen-off long after a closed reading does not arm the guard`() {
        assertEquals(false, LidSleepDecision.armsOnScreenOff(closedAt = closedAt, screenOffAt = closedAt + 60_000))
    }

    @Test
    fun `a screen-off before any closed reading does not arm the guard`() {
        assertEquals(false, LidSleepDecision.armsOnScreenOff(closedAt = 0L, screenOffAt = closedAt))
        assertEquals(false, LidSleepDecision.armsOnScreenOff(closedAt = closedAt, screenOffAt = closedAt - 1))
    }

    @Test
    fun `the external display check comes before the service check`() {
        assertEquals(
            LidSleepVerdict.EXTERNAL_DISPLAY,
            verdict(strayWake(externalDisplayInUse = true, serviceReady = false))
        )
    }
}
