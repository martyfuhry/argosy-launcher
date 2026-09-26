package com.nendo.argosy.ui.screens.common

import android.content.Intent
import com.nendo.argosy.ui.screens.gamedetail.GameDetailViewModel
import com.nendo.argosy.ui.screens.home.HomeViewModel
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

/**
 * The start of a tapped game on each launch screen, which must reach the game launch dispatcher
 * rather than an event the screen collects, since the screen can be swapped for the in-game
 * dashboard before it collects anything.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class LaunchDeliveryWiringTest {

    private val testDispatcher = StandardTestDispatcher()
    private val intent = mockk<Intent>(relaxed = true)
    private val gameLaunchDispatcher = mockk<GameLaunchDispatcher>(relaxed = true)
    private val gameLaunchDelegate = launchTestGameLaunchDelegate { _, _, onLaunch -> onLaunch(intent) }

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `a game started from the library reaches the dispatcher`() {
        val viewModel = launchTestLibraryViewModel(gameLaunchDispatcher, gameLaunchDelegate)

        viewModel.launchGame(GAME_ID)

        verify { gameLaunchDispatcher.dispatch(GAME_ID, intent, null) }
    }

    @Test
    fun `a game started from home reaches the dispatcher`() {
        val viewModel = launchTestHomeViewModel(gameLaunchDispatcher, gameLaunchDelegate)

        viewModel.launchGame(GAME_ID, null)

        verify { gameLaunchDispatcher.dispatch(GAME_ID, intent, null) }
    }

    @Test
    fun `a game started on a chosen display from home reaches the dispatcher with that display`() {
        val viewModel = launchTestHomeViewModel(gameLaunchDispatcher, gameLaunchDelegate)

        HomeViewModel::class.java
            .getDeclaredMethod("playGameOnDisplay", Long::class.java, Int::class.java, Boolean::class.java)
            .apply { isAccessible = true }
            .invoke(viewModel, GAME_ID, CHOSEN_DISPLAY_ID, false)

        verify { gameLaunchDispatcher.dispatch(GAME_ID, intent, CHOSEN_DISPLAY_ID) }
    }

    @Test
    fun `a game started from game details reaches the dispatcher`() {
        val viewModel = launchTestGameDetailViewModel(gameLaunchDispatcher, gameLaunchDelegate)
        viewModel.loadGame(GAME_ID)

        viewModel.launchCallbacks(overrideDisplayId = null).onLaunch(intent)

        verify { gameLaunchDispatcher.dispatch(GAME_ID, intent, null) }
    }

    @Test
    fun `a game started on a chosen display from game details reaches the dispatcher with that display`() {
        val viewModel = launchTestGameDetailViewModel(gameLaunchDispatcher, gameLaunchDelegate)
        viewModel.loadGame(GAME_ID)

        viewModel.launchCallbacks(overrideDisplayId = CHOSEN_DISPLAY_ID).onLaunch(intent)

        verify { gameLaunchDispatcher.dispatch(GAME_ID, intent, CHOSEN_DISPLAY_ID) }
    }

    private fun GameDetailViewModel.launchCallbacks(overrideDisplayId: Int?): LaunchResultCallbacks =
        GameDetailViewModel::class.java
            .getDeclaredMethod("makeLaunchCallbacks", Integer::class.java)
            .apply { isAccessible = true }
            .invoke(this, overrideDisplayId) as LaunchResultCallbacks

    private companion object {
        const val GAME_ID = 21L
        const val CHOSEN_DISPLAY_ID = 4
    }
}
