package com.nendo.argosy.ui.screens.common

import android.content.Context
import android.content.SharedPreferences
import com.nendo.argosy.data.local.entity.GameEntity
import com.nendo.argosy.data.model.GameSource
import com.nendo.argosy.data.preferences.UserPreferences
import com.nendo.argosy.data.preferences.UserPreferencesRepository
import com.nendo.argosy.data.repository.EmulatorConfigRepository
import com.nendo.argosy.data.repository.GameRepository
import com.nendo.argosy.domain.usecase.game.ConfigureEmulatorUseCase
import com.nendo.argosy.util.AttachedScreen
import com.nendo.argosy.util.DisplayAffinityHelper
import com.nendo.argosy.util.DisplayAffinityHelper.Companion.resolveDisplayTargetId
import com.nendo.argosy.util.DisplayAffinityHelper.Companion.resolveRoleDisplayIds
import com.nendo.argosy.util.ScreenCatalog
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

private const val GAME_ID = 7L
private const val PACKAGE = "com.example.banjo"
private const val TOP_KEY = "display:0:1080x1920"
private const val BOTTOM_KEY = "display:4:1080x1240"

/**
 * A Thor pin written and read back while the roles swap and the panels renumber across a reboot.
 * The pin lives in the per-package app display targets the apps drawer uses.
 */
class AppLaunchScreenSettingsTest {

    private var appTargets = mapOf<String, String>()
    private var legacyTarget: String? = null
    private var attached = thor(top = 0, bottom = 4)
    private var layoutPair: Pair<Int, Int>? = 0 to 4
    private var rolesSwapped = false

    private fun thor(top: Int, bottom: Int) = listOf(
        AttachedScreen(TOP_KEY, top, 1, 1080, 1920, builtIn = true),
        AttachedScreen(BOTTOM_KEY, bottom, 2, 1080, 1240, builtIn = true)
    )

    private val screenCatalog = mockk<ScreenCatalog> {
        every { attachedScreens() } answers { attached }
        every { screenFor(any()) } answers { attached.find { it.displayId == firstArg<Int>() } }
    }
    private val displayAffinityHelper = mockk<DisplayAffinityHelper> {
        every { getDisplayTargetId(any(), any()) } answers {
            val roles = resolveRoleDisplayIds(
                layoutPair,
                attached.map { it.displayId }.toSet(),
                null,
                secondArg()
            )
            resolveDisplayTargetId(firstArg(), roles, appScreenDisplayId = null)
        }
    }
    private val context = mockk<Context> {
        val prefs = mockk<SharedPreferences> { every { getBoolean(any(), any()) } answers { rolesSwapped } }
        every { getSharedPreferences(any(), any()) } returns prefs
    }
    private val preferencesRepository = mockk<UserPreferencesRepository> {
        every { preferences } answers { flow { emit(UserPreferences(appDisplayTargets = appTargets)) } }
        coEvery { setAppDisplayTarget(PACKAGE, any()) } answers {
            val key = secondArg<String?>()
            appTargets = if (key == null) appTargets - PACKAGE else appTargets + (PACKAGE to key)
        }
    }
    private val gameRepository = mockk<GameRepository> {
        coEvery { getById(GAME_ID) } returns GameEntity(
            id = GAME_ID,
            platformId = 1L,
            title = "Banjo",
            sortTitle = "banjo",
            localPath = null,
            rommId = null,
            igdbId = null,
            raId = null,
            source = GameSource.ANDROID_APP,
            packageName = PACKAGE
        )
    }
    private val emulatorConfigRepository = mockk<EmulatorConfigRepository> {
        coEvery { getDisplayTargetForGame(GAME_ID) } answers { legacyTarget }
    }
    private val configureEmulatorUseCase = mockk<ConfigureEmulatorUseCase> {
        coEvery { setDisplayTargetForGame(GAME_ID, any()) } answers { legacyTarget = secondArg() }
    }

    private val settings = AppLaunchScreenSettings(
        context = context,
        screenCatalog = screenCatalog,
        displayAffinityHelper = displayAffinityHelper,
        preferencesRepository = preferencesRepository,
        gameRepository = gameRepository,
        emulatorConfigRepository = emulatorConfigRepository,
        configureEmulatorUseCase = configureEmulatorUseCase
    )

    @Test
    fun `a pinned panel is stored as the package's screen key`() = runTest {
        settings.store(GAME_ID, 0)

        assertEquals(mapOf(PACKAGE to TOP_KEY), appTargets)
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

        assertEquals(emptyMap<String, String>(), appTargets)
        assertNull(settings.storedChoice(GAME_ID))
    }

    @Test
    fun `an apps-drawer pin is the game's launch screen too`() = runTest {
        appTargets = mapOf(PACKAGE to BOTTOM_KEY)

        assertEquals(4, settings.storedChoice(GAME_ID)?.displayId)
    }

    @Test
    fun `a per-game screen token from an earlier build moves into the pin`() = runTest {
        legacyTarget = "screen:$BOTTOM_KEY"

        assertEquals(4, settings.storedChoice(GAME_ID)?.displayId)
        assertEquals(mapOf(PACKAGE to BOTTOM_KEY), appTargets)
        assertNull(legacyTarget)
    }

    @Test
    fun `a role name stored by an earlier build moves in as the panel holding that role now`() = runTest {
        legacyTarget = "PRIMARY"

        assertEquals(4, settings.storedChoice(GAME_ID)?.displayId)
        assertEquals(mapOf(PACKAGE to BOTTOM_KEY), appTargets)
        assertNull(legacyTarget)
    }

    @Test
    fun `a role name stored by an earlier build follows a role swap`() = runTest {
        legacyTarget = "PRIMARY"
        rolesSwapped = true

        assertEquals(0, settings.storedChoice(GAME_ID)?.displayId)
        assertEquals(mapOf(PACKAGE to TOP_KEY), appTargets)
        assertNull(legacyTarget)
    }

    @Test
    fun `a role name no attached panel holds is kept for a later read`() = runTest {
        legacyTarget = "PRESENTATION"
        layoutPair = null
        attached = thor(top = 0, bottom = 4).take(1)

        assertNull(settings.storedChoice(GAME_ID))
        assertEquals("PRESENTATION", legacyTarget)
        assertEquals(emptyMap<String, String>(), appTargets)
    }

    @Test
    fun `every attached panel is a choice`() {
        assertEquals(listOf(TOP_KEY, BOTTOM_KEY), settings.choices().map { it.screenKey })
    }
}
