package com.nendo.argosy.ui.input

import android.view.InputDevice
import android.view.MotionEvent
import com.nendo.argosy.data.preferences.UserPreferencesRepository
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class GamepadInputHandlerTest {

    private val scheduler = TestCoroutineScheduler()
    private val testDispatcher = StandardTestDispatcher(scheduler)
    private val delivered = mutableListOf<Pair<GamepadEvent, Boolean>>()
    private lateinit var handler: GamepadInputHandler

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        val preferences = mockk<UserPreferencesRepository>()
        every { preferences.preferences } returns emptyFlow()
        handler = GamepadInputHandler(preferences)
    }

    @After
    fun tearDown() {
        handler.resetStickMotion()
        Dispatchers.resetMain()
    }

    private fun sample(x: Float = 0f, y: Float = 0f, hatX: Float = 0f, hatY: Float = 0f): MotionEvent {
        val axes = mapOf(
            MotionEvent.AXIS_X to x,
            MotionEvent.AXIS_Y to y,
            MotionEvent.AXIS_HAT_X to hatX,
            MotionEvent.AXIS_HAT_Y to hatY
        )
        val event = mockk<MotionEvent>()
        every { event.isFromSource(InputDevice.SOURCE_CLASS_JOYSTICK) } returns true
        every { event.getAxisValue(any()) } answers { axes[firstArg()] ?: 0f }
        return event
    }

    private fun process(event: MotionEvent): Boolean =
        handler.processStickMotion(event) { direction, isRepeat -> delivered += direction to isRepeat }

    @Test
    fun `a hat press is an edge and a held hat repeats from the app`() {
        assertTrue(process(sample(hatX = 1f)))
        assertEquals(listOf(GamepadEvent.Right to false), delivered)

        scheduler.advanceTimeBy(401)
        assertEquals(listOf(GamepadEvent.Right to false, GamepadEvent.Right to true), delivered)

        scheduler.advanceTimeBy(150)
        assertEquals(3, delivered.size)

        assertFalse(process(sample()))
        scheduler.advanceTimeBy(1000)
        assertEquals(3, delivered.size)
    }

    @Test
    fun `a registered raw motion listener sees a hat sample instead of the trackers`() {
        val seen = mutableListOf<MotionEvent>()
        handler.setRawMotionEventListener { seen += it; false }

        val hatPress = sample(hatX = 1f)
        assertFalse(process(hatPress))
        assertFalse(handler.handleMotionEvent(hatPress))
        scheduler.advanceTimeBy(1000)

        assertEquals(listOf(hatPress), seen)
        assertTrue(delivered.isEmpty())
    }

    @Test
    fun `registering a raw motion listener stops a running repeat`() {
        process(sample(x = 1f))
        handler.setRawMotionEventListener { true }
        scheduler.advanceTimeBy(1000)

        assertEquals(listOf(GamepadEvent.Right to false), delivered)
    }

    @Test
    fun `no tick is delivered after resetStickMotion`() {
        process(sample(y = -1f))
        scheduler.advanceTimeBy(401)
        assertEquals(listOf(GamepadEvent.Up to false, GamepadEvent.Up to true), delivered)

        handler.resetStickMotion()
        scheduler.advanceTimeBy(1000)

        assertEquals(2, delivered.size)
    }

    @Test
    fun `the stick outranks the hat while both are held`() {
        process(sample(x = 1f, hatY = 1f))
        scheduler.advanceTimeBy(401)

        assertEquals(listOf(GamepadEvent.Right to false, GamepadEvent.Right to true), delivered)
    }
}
