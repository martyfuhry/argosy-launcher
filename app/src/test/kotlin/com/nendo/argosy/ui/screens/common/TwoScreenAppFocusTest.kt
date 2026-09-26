package com.nendo.argosy.ui.screens.common

import com.nendo.argosy.data.preferences.SessionStateStore
import com.nendo.argosy.hardware.SecondaryHomeActivity
import com.nendo.argosy.util.SecondaryHomeComponent
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
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

    @Test
    fun `a two-screen app still leaves the companion component enabled for later`() {
        every { store.isDualScreenEnabled() } returns true
        mockkObject(SecondaryHomeComponent)
        try {
            every { SecondaryHomeComponent.setEnabled(any(), any()) } returns Unit
            val dsm = manager()
            dsm.setEmulatorDisplay(0)

            dsm.ensureCompanionLaunched(allowDuringSession = true)

            verify { SecondaryHomeComponent.setEnabled(any(), true) }
        } finally {
            unmockkObject(SecondaryHomeComponent)
        }
    }

    @Test
    fun `the companion does not take focus back from a two-screen app`() {
        val dsm = manager()
        dsm.setEmulatorDisplay(0)
        val companion = companionOver(dsm)

        companion.refocusSelf()

        verify(exactly = 0) { companion.startActivity(any()) }
    }

    @Test
    fun `the companion does not take focus back from a two-screen emulator`() {
        every { store.getEmulatorPackage() } returns "org.azahar_emu.azahar"
        val dsm = manager()
        dsm.setEmulatorDisplay(0)
        val companion = companionOver(dsm)

        companion.refocusSelf()

        verify(exactly = 0) { companion.startActivity(any()) }
    }

    @Test
    fun `the companion takes focus back from a single-screen app`() {
        every { store.getEmulatorPackage() } returns "com.example.single"
        val dsm = manager()
        dsm.setEmulatorDisplay(0)
        val companion = companionOver(dsm)

        companion.refocusSelf()

        verify(exactly = 1) { companion.startActivity(any()) }
    }

    private fun companionOver(dsm: com.nendo.argosy.DualScreenManager): SecondaryHomeActivity =
        mockk<SecondaryHomeActivity>(relaxed = true).also { companion ->
            every { companion.refocusSelf() } answers { callOriginal() }
            SecondaryHomeActivity::class.java.getDeclaredField("dsm").apply { isAccessible = true }.set(companion, dsm)
        }
}
