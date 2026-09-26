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
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SessionEndDisplayTest {

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `a session end the dashboard never saw still clears the stored game display`() {
        val store = mockk<SessionStateStore>(relaxed = true) {
            every { hasActiveSession() } returns false
        }
        val dsm = launchTestDualScreenManager(
            scope = CoroutineScope(testDispatcher + SupervisorJob()),
            displayAffinityHelper = mockk(relaxed = true),
            sessionStateStore = store,
            gameDao = mockk(relaxed = true)
        )
        dsm.setEmulatorDisplay(0)

        dsm.onSessionChanged(-1L)

        verify { store.setEmulatorDisplayId(null) }
    }
}
