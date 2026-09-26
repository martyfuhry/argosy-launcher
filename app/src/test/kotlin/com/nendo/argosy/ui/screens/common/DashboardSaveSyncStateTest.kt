package com.nendo.argosy.ui.screens.common

import com.nendo.argosy.data.preferences.SessionStateStore
import io.mockk.every
import io.mockk.mockk
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
class DashboardSaveSyncStateTest {

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
    fun `a session without a watched save shows no save sync until the next session starts`() {
        val dsm = launchTestDualScreenManager(
            scope = CoroutineScope(testDispatcher + SupervisorJob()),
            displayAffinityHelper = mockk(relaxed = true),
            sessionStateStore = mockk<SessionStateStore>(relaxed = true) {
                every { hasActiveSession() } returns false
            },
            gameDao = mockk(relaxed = true)
        )

        dsm.updateCompanionSaveSyncApplicable(false)
        assertFalse(dsm.swappedCompanionState.value.saveSyncApplicable)

        dsm.updateCompanionSaveSyncApplicable(true)
        assertTrue(dsm.swappedCompanionState.value.saveSyncApplicable)
    }
}
