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
 * The Thor with its roles swapped: the layout gives PRIMARY to the top panel, but the swap has
 * moved that role to the bottom one.
 */
class EmulatorLaunchTargetResolverTest {

    private val displayAffinityHelper = mockk<DisplayAffinityHelper> {
        every { getLayoutDisplayTargetId(EmulatorDisplayTarget.PRIMARY) } returns TOP
        every { getDisplayTargetId(EmulatorDisplayTarget.PRIMARY, rolesSwapped = true) } returns BOTTOM
        every { getDisplayTargetId(EmulatorDisplayTarget.DEFAULT, any()) } returns null
    }
    private val emulatorConfigRepository = mockk<EmulatorConfigRepository>()
    private val gameRepository = mockk<GameRepository>()

    private val resolver = EmulatorLaunchTargetResolver(
        context = swappedContext(),
        displayAffinityHelper = displayAffinityHelper,
        emulatorConfigRepository = emulatorConfigRepository,
        gameRepository = gameRepository
    )

    private fun swappedContext(): Context {
        val prefs = mockk<SharedPreferences> {
            every { getBoolean(any(), any()) } returns true
        }
        return mockk {
            every { getSharedPreferences(any(), any()) } returns prefs
        }
    }

    private fun storedTarget(
        target: String?,
        source: GameSource,
        platformTarget: String? = null
    ) {
        coEvery { emulatorConfigRepository.getDisplayTargetForGame(GAME_ID) } returns target
        coEvery { emulatorConfigRepository.getEffectiveDisplayTarget(GAME_ID) } returns
            (target ?: platformTarget)
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
    fun `an android app's launch screen stays on the layout's screen through a swap`() = runTest {
        storedTarget(EmulatorDisplayTarget.PRIMARY.name, GameSource.ANDROID_APP)

        assertEquals(TOP, resolver.launchDisplayIdFor(GAME_ID))
    }

    @Test
    fun `a game's display target follows the swapped roles`() = runTest {
        storedTarget(EmulatorDisplayTarget.PRIMARY.name, GameSource.ROMM_SYNCED)

        assertEquals(BOTTOM, resolver.launchDisplayIdFor(GAME_ID))
    }

    @Test
    fun `an android app with no stored screen is placed like any launch`() = runTest {
        storedTarget(null, GameSource.ANDROID_APP)

        assertNull(resolver.launchDisplayIdFor(GAME_ID))
    }

    @Test
    fun `an android app with only a platform target follows the swapped roles`() = runTest {
        storedTarget(null, GameSource.ANDROID_APP, platformTarget = EmulatorDisplayTarget.PRIMARY.name)

        assertEquals(BOTTOM, resolver.launchDisplayIdFor(GAME_ID))
    }

    @Test
    fun `a one-shot screen wins over the stored one`() = runTest {
        storedTarget(EmulatorDisplayTarget.PRIMARY.name, GameSource.ANDROID_APP)

        assertEquals(BOTTOM, resolver.launchDisplayIdFor(GAME_ID, overrideDisplayId = BOTTOM))
    }
}
