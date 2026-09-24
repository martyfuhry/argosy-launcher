package com.nendo.argosy.ui.screens.common

import com.nendo.argosy.data.repository.EmulatorConfigRepository
import com.nendo.argosy.domain.usecase.game.ConfigureEmulatorUseCase
import com.nendo.argosy.util.AttachedScreen
import com.nendo.argosy.util.DisplayAffinityHelper
import com.nendo.argosy.util.ScreenCatalog
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

private const val GAME_ID = 7L
private const val TOP_KEY = "display:0:1080x1920"
private const val BOTTOM_KEY = "display:4:1080x1240"

/**
 * A Thor pin written and read back while the roles swap and the panels renumber across a reboot.
 */
class AppLaunchScreenSettingsTest {

    private var storedTarget: String? = null
    private var attached = thor(top = 0, bottom = 4)
    private var layoutPair: Pair<Int, Int>? = 0 to 4

    private fun thor(top: Int, bottom: Int) = listOf(
        AttachedScreen(TOP_KEY, top, 1, 1080, 1920, builtIn = true),
        AttachedScreen(BOTTOM_KEY, bottom, 2, 1080, 1240, builtIn = true)
    )

    private val screenCatalog = mockk<ScreenCatalog> {
        every { attachedScreens() } answers { attached }
        every { screenFor(any()) } answers { attached.find { it.displayId == firstArg<Int>() } }
    }
    private val displayAffinityHelper = mockk<DisplayAffinityHelper> {
        every { roleDisplayIds } answers { layoutPair }
        every { appTargetDisplayId } returns null
    }
    private val emulatorConfigRepository = mockk<EmulatorConfigRepository> {
        coEvery { getDisplayTargetForGame(GAME_ID) } answers { storedTarget }
    }
    private val configureEmulatorUseCase = mockk<ConfigureEmulatorUseCase> {
        coEvery { setDisplayTargetForGame(GAME_ID, any()) } answers { storedTarget = secondArg() }
    }

    private val settings = AppLaunchScreenSettings(
        screenCatalog = screenCatalog,
        displayAffinityHelper = displayAffinityHelper,
        emulatorConfigRepository = emulatorConfigRepository,
        configureEmulatorUseCase = configureEmulatorUseCase
    )

    @Test
    fun `a pinned panel is stored by its screen key`() = runTest {
        settings.store(GAME_ID, 0)

        assertEquals(AppLaunchScreenSettings.screenToken(TOP_KEY), storedTarget)
    }

    @Test
    fun `a role swap leaves the pinned panel where it was`() = runTest {
        settings.store(GAME_ID, 0)
        layoutPair = 4 to 0

        assertEquals(0, settings.storedChoice(GAME_ID)?.displayId)
    }

    @Test
    fun `a reboot that renumbers the panels still finds the pinned one`() = runTest {
        settings.store(GAME_ID, 0)
        attached = thor(top = 2, bottom = 3)

        assertEquals(2, settings.storedChoice(GAME_ID)?.displayId)
    }

    @Test
    fun `a pinned panel that is not attached names no screen`() = runTest {
        settings.store(GAME_ID, 4)
        attached = thor(top = 0, bottom = 4).take(1)

        assertNull(settings.storedChoice(GAME_ID))
    }

    @Test
    fun `clearing the pin stores nothing`() = runTest {
        settings.store(GAME_ID, 0)
        settings.store(GAME_ID, null)

        assertNull(storedTarget)
        assertNull(settings.storedChoice(GAME_ID))
    }

    @Test
    fun `a role name stored by an earlier build reads as the panel the layout gives it`() = runTest {
        storedTarget = "PRIMARY"

        assertEquals(0, settings.storedChoice(GAME_ID)?.displayId)
    }

    @Test
    fun `every attached panel is a choice`() {
        assertEquals(listOf(TOP_KEY, BOTTOM_KEY), settings.choices().map { it.screenKey })
    }
}
