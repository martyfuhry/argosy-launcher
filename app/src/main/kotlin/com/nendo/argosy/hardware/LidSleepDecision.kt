package com.nendo.argosy.hardware

enum class LidSleepVerdict {
    SLEEP,
    IDLE,
    RETRY_READING,
    STALE_READING,
    EXTERNAL_DISPLAY,
    RATE_LIMITED,
    SERVICE_OFF
}

data class LidSleepState(
    val enabled: Boolean,
    val lidClosed: Boolean,
    val closeConfirmed: Boolean,
    val interactive: Boolean,
    val lidReadAt: Long,
    val screenOnAt: Long,
    val staleRetries: Int,
    val externalDisplayInUse: Boolean,
    val attemptsInWindow: Int,
    val serviceReady: Boolean
)

/**
 * Whether a closed lid with the screen on should be answered with a sleep request right now.
 * [LidSleepState.closeConfirmed] is true once the screen went off within [ARM_WINDOW_MS] of a
 * closed reading in this process, per [armsOnScreenOff], which is what proves the sensor's
 * polarity and that the system sleeps on close at all. A reading older than the last screen-on
 * is not trusted until it has been refreshed.
 */
object LidSleepDecision {

    const val CLOSE_GRACE_MS = 1500L
    const val WAKE_GRACE_MS = 1000L
    const val ARM_WINDOW_MS = 5000L
    const val MAX_STALE_RETRIES = 1
    const val ATTEMPT_WINDOW_MS = 60_000L
    const val MAX_ATTEMPTS_PER_WINDOW = 3

    fun armsOnScreenOff(closedAt: Long, screenOffAt: Long): Boolean =
        closedAt > 0L && screenOffAt - closedAt in 0L..ARM_WINDOW_MS

    fun decide(state: LidSleepState): LidSleepVerdict = when {
        !state.enabled || !state.lidClosed || !state.closeConfirmed || !state.interactive -> LidSleepVerdict.IDLE
        state.lidReadAt < state.screenOnAt ->
            if (state.staleRetries < MAX_STALE_RETRIES) LidSleepVerdict.RETRY_READING else LidSleepVerdict.STALE_READING
        state.externalDisplayInUse -> LidSleepVerdict.EXTERNAL_DISPLAY
        state.attemptsInWindow >= MAX_ATTEMPTS_PER_WINDOW -> LidSleepVerdict.RATE_LIMITED
        !state.serviceReady -> LidSleepVerdict.SERVICE_OFF
        else -> LidSleepVerdict.SLEEP
    }
}
