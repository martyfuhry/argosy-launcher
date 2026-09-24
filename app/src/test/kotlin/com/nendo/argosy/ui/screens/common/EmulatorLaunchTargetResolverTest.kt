package com.nendo.argosy.ui.screens.common

import android.content.Context
import android.content.SharedPreferences
import com.nendo.argosy.data.local.entity.GameEntity
import com.nendo.argosy.data.model.GameSource
import com.nendo.argosy.data.preferences.EmulatorDisplayTarget
import com.nendo.argosy.data.repository.EmulatorConfigRepository
import com.nendo.argosy.data.repository.GameRepository
import com.nendo.argosy.util.DisplayAffinityHelper
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

private const val TOP = 0
private const val BOTTOM = 4
private const val GAME_ID = 42L

/**
 * The Thor with its roles swapped, so the PRIMARY role now sits on the bottom panel.
 */
class EmulatorLaunchTargetResolverTest {

    private val displayAffinityHelper = mockk<DisplayAffinityHelper> {
        every { getDisplayTargetId(EmulatorDisplayTarget.PRIMARY, rolesSwapped = true) } returns BOTTOM
        every { getDisplayTargetId(EmulatorDisplayTarget.DEFAULT, any()) } returns null
    }
    private val emulatorConfigRepository = mockk<EmulatorConfigRepository>()
    private val gameRepository = mockk<GameRepository>()
    private val appLaunchScreenSettings = mockk<AppLaunchScreenSettings>()

    private val resolver = EmulatorLaunchTargetResolver(
        context = swappedContext(),
        displayAffinityHelper = displayAffinityHelper,
        emulatorConfigRepository = emulatorConfigRepository,
        gameRepository = gameRepository,
        appLaunchScreenSettings = appLaunchScreenSettings
    )

    private fun swappedContext(): Context {
        val prefs = mockk<SharedPreferences> {
            every { getBoolean(any(), any()) } returns true
        }
        return mockk {
            every { getSharedPreferences(any(), any()) } returns prefs
        }
    }

    private fun stored(
        source: GameSource,
        appScreen: LaunchScreenChoice? = null,
        effectiveTarget: String? = null
    ) {
        coEvery { appLaunchScreenSettings.storedChoice(GAME_ID) } returns appScreen
        coEvery { emulatorConfigRepository.getEffectiveDisplayTarget(GAME_ID) } returns effectiveTarget
        coEvery { gameRepository.getById(GAME_ID) } returns GameEntity(
            id = GAME_ID,
            platformId = 1L,
            title = "Game",
            sortTitle = "game",
            localPath = null,
            rommId = null,
            igdbId = null,
            raId = null,
            source = source
        )
    }

    @Test
    fun `an android app goes to the panel stored as its launch screen`() = runTest {
        stored(GameSource.ANDROID_APP, appScreen = LaunchScreenChoice(TOP, 1, "top"))

        assertEquals(TOP, resolver.launchDisplayIdFor(GAME_ID))
    }

    @Test
    fun `a game's display target follows the swapped roles`() = runTest {
        stored(GameSource.ROMM_SYNCED, effectiveTarget = EmulatorDisplayTarget.PRIMARY.name)

        assertEquals(BOTTOM, resolver.launchDisplayIdFor(GAME_ID))
    }

    @Test
    fun `an android app whose stored screen is not attached is placed like any launch`() = runTest {
        stored(GameSource.ANDROID_APP, appScreen = null, effectiveTarget = "screen:gone")

        assertNull(resolver.launchDisplayIdFor(GAME_ID))
    }

    @Test
    fun `an android app with only a platform target follows the swapped roles`() = runTest {
        stored(GameSource.ANDROID_APP, effectiveTarget = EmulatorDisplayTarget.PRIMARY.name)

        assertEquals(BOTTOM, resolver.launchDisplayIdFor(GAME_ID))
    }

    @Test
    fun `a one-shot screen wins over the stored one`() = runTest {
        stored(GameSource.ANDROID_APP, appScreen = LaunchScreenChoice(TOP, 1, "top"))

        assertEquals(BOTTOM, resolver.launchDisplayIdFor(GAME_ID, overrideDisplayId = BOTTOM))
    }
}
