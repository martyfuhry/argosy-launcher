package com.nendo.argosy.ui.screens.common

import com.nendo.argosy.data.preferences.SessionStateStore
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TwoScreenAppFocusTest {

    private val testDispatcher = StandardTestDispatcher()
    private val store = mockk<SessionStateStore>(relaxed = true) {
        every { hasActiveSession() } returns true
        every { getEmulatorPackage() } returns "com.aure.banjorecomp"
    }
    private val displayAffinityHelper = mockk<com.nendo.argosy.util.DisplayAffinityHelper>(relaxed = true) {
        every { hasSecondaryDisplay } returns true
    }

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun manager() = launchTestDualScreenManager(
        scope = CoroutineScope(testDispatcher + SupervisorJob()),
        displayAffinityHelper = displayAffinityHelper,
        sessionStateStore = store,
        gameDao = mockk(relaxed = true)
    )

    @Test
    fun `a two-screen app on the default display owns both screens`() {
        val dsm = manager()
        dsm.setEmulatorDisplay(0)

        assertTrue(dsm.sessionDrawsBothScreens())
    }

    @Test
    fun `a single-screen app leaves the companion its screen`() {
        every { store.getEmulatorPackage() } returns "com.example.single"
        val dsm = manager()
        dsm.setEmulatorDisplay(0)

        assertFalse(dsm.sessionDrawsBothScreens())
    }

    @Test
    fun `a two-screen app placed on the other panel leaves the companion its screen`() {
        val dsm = manager()
        dsm.setEmulatorDisplay(4)

        assertFalse(dsm.sessionDrawsBothScreens())
    }

    @Test
    fun `no companion is brought up over a two-screen app even when sessions allow it`() {
        val dsm = manager()
        dsm.setEmulatorDisplay(0)

        dsm.ensureCompanionLaunched(allowDuringSession = true)

        verify(exactly = 0) { store.isForeignAppOnSecondary() }
    }
}
