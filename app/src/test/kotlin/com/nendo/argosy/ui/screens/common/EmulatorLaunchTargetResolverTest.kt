package com.nendo.argosy.ui.screens.common

import android.content.Context
import android.content.SharedPreferences
import com.nendo.argosy.data.local.entity.GameEntity
import com.nendo.argosy.data.model.GameSource
import com.nendo.argosy.data.repository.GameRepository
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
 * Which screen a launch is explicitly sent to before the planner places it.
 */
class EmulatorLaunchTargetResolverTest {

    private val gameRepository = mockk<GameRepository>()
    private val appLaunchScreenSettings = mockk<AppLaunchScreenSettings>()

    private val resolver = EmulatorLaunchTargetResolver(
        context = swappedContext(),
        displayAffinityHelper = mockk(relaxed = true),
        launchDisplayPlanner = mockk(relaxed = true),
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

    private fun stored(source: GameSource, appScreen: LaunchScreenChoice? = null) {
        coEvery { appLaunchScreenSettings.storedChoice(GAME_ID) } returns appScreen
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
    fun `an android app goes to the panel pinned as its launch screen`() = runTest {
        stored(GameSource.ANDROID_APP, appScreen = LaunchScreenChoice(TOP, 1, "top"))

        assertEquals(TOP, resolver.chosenDisplayIdFor(GAME_ID))
    }

    @Test
    fun `a game names no screen of its own and is left to the planner`() = runTest {
        stored(GameSource.ROMM_SYNCED, appScreen = LaunchScreenChoice(TOP, 1, "top"))

        assertNull(resolver.chosenDisplayIdFor(GAME_ID))
    }

    @Test
    fun `an android app whose pinned screen is not attached is left to the planner`() = runTest {
        stored(GameSource.ANDROID_APP, appScreen = null)

        assertNull(resolver.chosenDisplayIdFor(GAME_ID))
    }

    @Test
    fun `a one-shot screen wins over the pinned one`() = runTest {
        stored(GameSource.ANDROID_APP, appScreen = LaunchScreenChoice(TOP, 1, "top"))

        assertEquals(BOTTOM, resolver.chosenDisplayIdFor(GAME_ID, overrideDisplayId = BOTTOM))
    }
}
