package com.nendo.argosy.ui.screens.common

import android.content.Intent
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nendo.argosy.ui.ModalResetSignal
import com.nendo.argosy.ui.screens.gamedetail.GameDetailViewModel
import com.nendo.argosy.ui.screens.home.HomeRow
import com.nendo.argosy.ui.screens.home.HomeViewModel
import com.nendo.argosy.ui.screens.home.delegates.HomeNavigationDelegate
import com.nendo.argosy.ui.screens.library.LibraryViewModel
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel

internal fun launchTestGameLaunchDelegate(
    launch: (scope: CoroutineScope, gameId: Long, onLaunch: (Intent) -> Unit) -> Unit
): GameLaunchDelegate = mockk(relaxed = true) {
    every { launchGame(any(), any(), any(), any(), any(), any(), any(), any(), any(), any()) } answers {
        launch(arg(0), arg(1), arg(8))
    }
    every { launchSimple(any(), any(), any(), any(), any(), any(), any(), any(), any(), any()) } answers {
        launch(arg(0), arg(1), lastArg<LaunchResultCallbacks>().onLaunch)
    }
}

internal fun launchTestLibraryViewModel(vararg overrides: Any): LibraryViewModel =
    relaxedInstance<LibraryViewModel>(*overrides, ModalResetSignal()).withoutBackgroundWork()

internal fun launchTestHomeViewModel(vararg overrides: Any): HomeViewModel =
    relaxedInstance<HomeViewModel>(
        *overrides,
        ModalResetSignal(),
        SavedStateHandle(),
        mockk<HomeNavigationDelegate>(relaxed = true) {
            every { restoreInitialRow(any()) } returns (HomeRow.Continue to 0)
        }
    ).withoutBackgroundWork()

internal fun launchTestGameDetailViewModel(vararg overrides: Any): GameDetailViewModel =
    relaxedInstance<GameDetailViewModel>(*overrides, ModalResetSignal()).withoutBackgroundWork()

private fun <T : ViewModel> T.withoutBackgroundWork(): T = apply { viewModelScope.cancel() }

