package com.nendo.argosy.ui.screens.home.delegates

import com.nendo.argosy.domain.model.LaunchProgress
import com.nendo.argosy.domain.model.LaunchStep
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HoldsRecentsTest {
    private val progress = LaunchProgress(gameTitle = "Game", step = LaunchStep.Launching, gameId = 1L)

    @Test
    fun `a launch in progress holds the row`() {
        assertTrue(holdsRecents(progress))
    }

    @Test
    fun `nothing launching lets the row move`() {
        assertFalse(holdsRecents(null))
    }

    @Test
    fun `the row settles once when the launch is over`() = runTest {
        val launching = MutableStateFlow<LaunchProgress?>(null)
        val recents = MutableStateFlow(listOf("b", "a"))
        val out = mutableListOf<List<String>>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            recents.heldWhile(launching.map { holdsRecents(it) }).collect { out += it }
        }
        runCurrent()

        launching.value = progress
        runCurrent()
        recents.value = listOf("a", "b")
        runCurrent()
        assertEquals(listOf(listOf("b", "a")), out)

        launching.value = null
        runCurrent()
        assertEquals(listOf(listOf("b", "a"), listOf("a", "b")), out)

        recents.value = listOf("c", "a", "b")
        runCurrent()
        assertEquals(listOf(listOf("b", "a"), listOf("a", "b"), listOf("c", "a", "b")), out)
    }
}
