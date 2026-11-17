package com.nendo.argosy.ui.dualscreen.dashboard

import com.nendo.argosy.data.emulator.MinarchLinkState
import com.nendo.argosy.data.emulator.MinarchLinkStates
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn

private const val MINARCH_STATES_POLL_MS = 2000L

fun minarchLinkSessionControls(storageRoot: File, romFileName: String): Flow<SessionControls> = flow {
    while (true) {
        emit(minarchLinkSessionControlsOf(MinarchLinkStates.list(storageRoot, romFileName)))
        delay(MINARCH_STATES_POLL_MS)
    }
}.flowOn(Dispatchers.IO)

internal fun minarchLinkSessionControlsOf(states: List<MinarchLinkState>): SessionControls = SessionControls(
    quickStates = states.map {
        DashboardQuickState(
            slotNumber = it.slotNumber,
            savedAtMillis = it.savedAtMillis,
            screenshotPath = it.previewFile?.absolutePath,
            menuSlot = MinarchLinkStates.menuSlotLabel(it.slotNumber)
        )
    },
    screenshotAvailable = false
)
