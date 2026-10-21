package com.nendo.argosy.ui.screens.home.delegates

import com.nendo.argosy.data.emulator.ActiveSession
import com.nendo.argosy.domain.model.LaunchProgress
import com.nendo.argosy.domain.model.LaunchStep
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class HoldsRecentsTest {
    private val progress = LaunchProgress(gameTitle = "Game", step = LaunchStep.Launching, gameId = 1L)
    private val session = ActiveSession(
        gameId = 1L,
        startTime = Instant.ofEpochSecond(100),
        emulatorPackage = "com.example.emulator"
    )

    @Test
    fun `a launch in progress holds the row`() {
        assertTrue(holdsRecents(progress, null))
    }

    @Test
    fun `a running session holds the row`() {
        assertTrue(holdsRecents(null, session))
    }

    @Test
    fun `a launch handing over to its session holds the row`() {
        assertTrue(holdsRecents(progress, session))
    }

    @Test
    fun `nothing launching and nothing running lets the row move`() {
        assertFalse(holdsRecents(null, null))
    }

    @Test
    fun `the row settles once when the launch and its session are both over`() = runTest {
        val launching = MutableStateFlow<LaunchProgress?>(null)
        val active = MutableStateFlow<ActiveSession?>(null)
        val recents = MutableStateFlow(listOf("b", "a"))
        val out = mutableListOf<List<String>>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            recents.heldWhile(combine(launching, active) { p, s -> holdsRecents(p, s) }).collect { out += it }
        }
        runCurrent()

        launching.value = progress
        runCurrent()
        recents.value = listOf("a", "b")
        runCurrent()
        active.value = session
        launching.value = null
        runCurrent()
        assertEquals(listOf(listOf("b", "a")), out)

        active.value = null
        runCurrent()
        assertEquals(listOf(listOf("b", "a"), listOf("a", "b")), out)
    }
}
