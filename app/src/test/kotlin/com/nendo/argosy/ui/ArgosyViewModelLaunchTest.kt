package com.nendo.argosy.ui

import android.content.Intent
import com.nendo.argosy.data.emulator.LaunchResult
import com.nendo.argosy.data.emulator.PlaySessionTracker
import com.nendo.argosy.data.local.entity.GameEntity
import com.nendo.argosy.data.netplay.NetplayJoinService
import com.nendo.argosy.data.netplay.NetplayJoinState
import com.nendo.argosy.data.netplay.NetplayPreflightChecker
import com.nendo.argosy.data.netplay.NetplayPreflightResult
import com.nendo.argosy.data.repository.GameRepository
import com.nendo.argosy.data.social.NetplayInvitePayload
import com.nendo.argosy.data.social.SocialConnectionState
import com.nendo.argosy.data.social.SocialRepository
import com.nendo.argosy.data.sync.SyncQueueManager
import com.nendo.argosy.domain.usecase.game.LaunchGameUseCase
import com.nendo.argosy.libretro.CoreCrashController
import com.nendo.argosy.ui.components.CoreCrashPrompt
import com.nendo.argosy.ui.screens.common.GameLaunchDispatcher
import com.nendo.argosy.ui.screens.common.relaxedInstance
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

/**
 * Netplay and crash-relaunch starts, which prepare an external emulator's session through the
 * launch use case and so must reach the dispatcher that opens it.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ArgosyViewModelLaunchTest {

    private val testDispatcher = StandardTestDispatcher()
    private val gameLaunchDispatcher = mockk<GameLaunchDispatcher>(relaxed = true)
    private val intent = mockk<Intent>(relaxed = true)
    private val launchGameUseCase = mockk<LaunchGameUseCase> {
        coEvery { this@mockk.invoke(GAME_ID, any(), any(), any(), any(), any(), any(), any(), any()) } returns
            LaunchResult.Success(intent)
    }
    private val netplayJoinService = mockk<NetplayJoinService>(relaxed = true)
    private val invites = MutableSharedFlow<NetplayInvitePayload>(extraBufferCapacity = 1)
    private val socialRepository = mockk<SocialRepository>(relaxed = true) {
        every { netplayInvites } returns invites
        every { connectionState } returns MutableStateFlow(SocialConnectionState.Disconnected)
    }
    private val playSessionTracker = mockk<PlaySessionTracker>(relaxed = true) {
        every { pendingSessionConflict } returns MutableStateFlow(null)
    }

    private val application = mockk<android.app.Application>(relaxed = true) {
        every { getSystemService(android.content.Context.INPUT_SERVICE) } returns mockk<android.hardware.input.InputManager>(relaxed = true)
        every { getSystemService(android.content.Context.AUDIO_SERVICE) } returns mockk<android.media.AudioManager>(relaxed = true)
    }

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        io.mockk.mockkStatic(android.view.InputDevice::class)
        every { android.view.InputDevice.getDeviceIds() } returns intArrayOf()
    }

    @After
    fun tearDown() {
        io.mockk.unmockkStatic(android.view.InputDevice::class)
        Dispatchers.resetMain()
    }

    @Test
    fun `a netplay join ready to start is started by the dispatcher`() {
        val viewModel = viewModel()

        viewModel.launchNetplayJoin(NetplayJoinState.LaunchReady(intent, GAME_ID))

        verify { gameLaunchDispatcher.dispatch(GAME_ID, intent, null) }
        verify { netplayJoinService.reset() }
    }

    @Test
    fun `an accepted netplay invite is started by the dispatcher`() {
        val preflight = mockk<NetplayPreflightChecker> {
            coEvery { check(any()) } returns NetplayPreflightResult.Joinable("/roms/game.sfc", gameId = GAME_ID)
        }
        val gameRepository = mockk<GameRepository>(relaxed = true) {
            coEvery { getByIgdbId(IGDB_ID.toLong()) } returns mockk<GameEntity>(relaxed = true) {
                every { id } returns GAME_ID
            }
        }
        val viewModel = viewModel(preflight, gameRepository)
        testDispatcher.scheduler.runCurrent()
        invites.tryEmit(invite())
        testDispatcher.scheduler.runCurrent()

        viewModel.acceptNetplayInvite()
        testDispatcher.scheduler.runCurrent()

        verify { gameLaunchDispatcher.dispatch(GAME_ID, any(), null) }
    }

    @Test
    fun `a relaunch after a core crash is started by the dispatcher`() {
        val coreCrashController = mockk<CoreCrashController>(relaxed = true) {
            every { prompt } returns MutableStateFlow(
                CoreCrashPrompt(
                    coreId = "snes9x",
                    gameId = GAME_ID,
                    displayName = "Snes9x",
                    platformId = 1L,
                    platformSlug = "snes",
                    options = emptyList()
                )
            )
        }
        val viewModel = viewModel(coreCrashController)

        viewModel.launchFromCoreCrash()
        testDispatcher.scheduler.runCurrent()

        verify { gameLaunchDispatcher.dispatch(GAME_ID, intent, null) }
    }

    private fun invite() = NetplayInvitePayload(
        sessionId = "session",
        hostUserId = "host",
        hostUsername = "Host",
        gameTitle = "Game",
        gameIgdbId = IGDB_ID,
        coreId = "snes9x",
        romHashPrefix = "abc",
        coreHash = "def",
        protocolVersion = 1
    )

    private fun viewModel(vararg overrides: Any): ArgosyViewModel = relaxedInstance(
        *overrides,
        application,
        gameLaunchDispatcher,
        launchGameUseCase,
        netplayJoinService,
        socialRepository,
        playSessionTracker,
        SyncQueueManager()
    )

    private companion object {
        const val GAME_ID = 11L
        const val IGDB_ID = 4242
    }
}
