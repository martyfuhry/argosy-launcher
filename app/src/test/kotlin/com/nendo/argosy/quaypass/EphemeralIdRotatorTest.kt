package com.nendo.argosy.quaypass

import com.nendo.argosy.data.quaypass.ble.EphemeralIdRotator
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Random

@OptIn(ExperimentalCoroutinesApi::class)
class EphemeralIdRotatorTest {

    private val lifetime = EphemeralIdRotator.MAX_LIFETIME_MS + 1

    @Test
    fun `consecutive rotations yield different ids`() = runTest {
        val rotated = mutableListOf<ByteArray>()
        val rotator = EphemeralIdRotator(
            scope = backgroundScope,
            random = Random(7L),
            onRotate = { rotated += it }
        )
        val initial = rotator.currentId

        rotator.start()
        advanceTimeBy(lifetime)
        runCurrent()
        advanceTimeBy(lifetime)
        runCurrent()

        assertEquals(2, rotated.size)
        assertEquals(EphemeralIdRotator.ID_BYTES, rotated[0].size)
        assertFalse(initial.contentEquals(rotated[0]))
        assertFalse(rotated[0].contentEquals(rotated[1]))
        assertTrue(rotator.currentId.contentEquals(rotated[1]))
    }

    @Test
    fun `lifetime stays within its bounds`() = runTest {
        val rotator = EphemeralIdRotator(
            scope = backgroundScope,
            random = Random(11L),
            onRotate = {}
        )
        repeat(500) {
            val lifetimeMs = rotator.nextLifetimeMillis()
            assertTrue(lifetimeMs >= EphemeralIdRotator.MIN_LIFETIME_MS)
            assertTrue(lifetimeMs <= EphemeralIdRotator.MAX_LIFETIME_MS)
        }
    }

    @Test
    fun `stop ends rotation`() = runTest {
        val rotated = mutableListOf<ByteArray>()
        val rotator = EphemeralIdRotator(
            scope = backgroundScope,
            random = Random(13L),
            onRotate = { rotated += it }
        )

        rotator.start()
        advanceTimeBy(lifetime)
        runCurrent()
        assertEquals(1, rotated.size)

        rotator.stop()
        assertFalse(rotator.isRotating)
        advanceTimeBy(lifetime * 4)
        runCurrent()

        assertEquals(1, rotated.size)
    }

    @Test
    fun `rotation waits for an exchange to finish`() = runTest {
        val rotated = mutableListOf<ByteArray>()
        var busy = true
        val rotator = EphemeralIdRotator(
            scope = backgroundScope,
            random = Random(17L),
            isBusy = { busy },
            onRotate = { rotated += it }
        )
        val held = rotator.currentId

        rotator.start()
        advanceTimeBy(lifetime * 2)
        runCurrent()

        assertTrue(rotated.isEmpty())
        assertTrue(rotator.currentId.contentEquals(held))

        busy = false
        advanceTimeBy(EphemeralIdRotator.BUSY_RETRY_MS + 1)
        runCurrent()

        assertEquals(1, rotated.size)
        assertFalse(held.contentEquals(rotated[0]))
    }
}
