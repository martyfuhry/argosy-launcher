package com.nendo.argosy.data.quaypass.ble

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.security.SecureRandom
import java.util.Random

/**
 * The ephemeral id carried in the QuayPass advert and the loop that replaces it
 * with a fresh one every [MIN_LIFETIME_MS] to [MAX_LIFETIME_MS], held back while
 * [isBusy] reports an exchange in flight.
 */
class EphemeralIdRotator(
    private val scope: CoroutineScope,
    private val random: Random = SecureRandom(),
    private val isBusy: () -> Boolean = { false },
    private val onRotate: (ByteArray) -> Unit
) {

    @Volatile
    var currentId: ByteArray = newId()
        private set

    private var job: Job? = null

    val isRotating: Boolean
        get() = job?.isActive == true

    fun start() {
        job?.cancel()
        job = scope.launch {
            while (isActive) {
                delay(nextLifetimeMillis())
                while (isBusy()) delay(BUSY_RETRY_MS)
                val id = newId()
                currentId = id
                onRotate(id)
            }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
    }

    fun nextLifetimeMillis(): Long =
        MIN_LIFETIME_MS + (random.nextDouble() * (MAX_LIFETIME_MS - MIN_LIFETIME_MS)).toLong()

    private fun newId(): ByteArray = ByteArray(ID_BYTES).also { random.nextBytes(it) }

    companion object {
        const val ID_BYTES: Int = 8
        const val MIN_LIFETIME_MS: Long = 10L * 60 * 1000
        const val MAX_LIFETIME_MS: Long = 15L * 60 * 1000
        const val BUSY_RETRY_MS: Long = 1_000
    }
}
