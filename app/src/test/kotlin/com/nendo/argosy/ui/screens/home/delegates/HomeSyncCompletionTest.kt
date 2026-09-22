package com.nendo.argosy.ui.screens.home.delegates

import com.nendo.argosy.data.remote.romm.PlatformSyncRow
import com.nendo.argosy.data.remote.romm.PlatformSyncState
import com.nendo.argosy.data.remote.romm.SyncProgress
import com.nendo.argosy.data.sync.PlatformSyncQueue
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * A home row reloads once per platform a sync finishes and once per job that leaves the queue.
 * Sync progress ticks once per game, and a reload on every tick would re-query the row hundreds
 * of times during a first sync and move the cursor under the user's thumb each time.
 */
class HomeSyncCompletionTest {

    private val nes = 1L
    private val snes = 2L

    private fun row(id: Long, state: PlatformSyncState, gamesDone: Int = 0) =
        PlatformSyncRow(platformId = id, name = "p$id", slug = "p$id", state = state, gamesDone = gamesDone)

    @Test
    fun `a platform is reported once when its games land, not once per game`() = runTest {
        val progress = flowOf(
            SyncProgress(isSyncing = true, platforms = listOf(row(nes, PlatformSyncState.SYNCING, 1))),
            SyncProgress(isSyncing = true, platforms = listOf(row(nes, PlatformSyncState.SYNCING, 2))),
            SyncProgress(isSyncing = true, platforms = listOf(row(nes, PlatformSyncState.DONE, 2))),
            SyncProgress(
                isSyncing = true,
                platforms = listOf(row(nes, PlatformSyncState.DONE, 2), row(snes, PlatformSyncState.SYNCING, 1))
            ),
            SyncProgress(
                isSyncing = true,
                platforms = listOf(row(nes, PlatformSyncState.DONE, 2), row(snes, PlatformSyncState.DONE, 3))
            ),
            SyncProgress(
                isSyncing = false,
                platforms = listOf(row(nes, PlatformSyncState.DONE, 2), row(snes, PlatformSyncState.DONE, 3))
            )
        )

        assertEquals(listOf(nes, snes), progress.platformsFinished().toList())
    }

    @Test
    fun `a platform a resumed pass skipped or failed wrote nothing and is not reported`() = runTest {
        val progress = flowOf(
            SyncProgress(
                isSyncing = true,
                platforms = listOf(row(nes, PlatformSyncState.ALREADY_SYNCED), row(snes, PlatformSyncState.FAILED))
            )
        )

        assertEquals(emptyList<Long>(), progress.platformsFinished().toList())
    }

    @Test
    fun `a later pass reports a platform again`() = runTest {
        val progress = flowOf(
            SyncProgress(isSyncing = true, platforms = listOf(row(nes, PlatformSyncState.DONE))),
            SyncProgress(isSyncing = true),
            SyncProgress(isSyncing = true, platforms = listOf(row(nes, PlatformSyncState.DONE)))
        )

        assertEquals(listOf(nes, nes), progress.platformsFinished().toList())
    }

    @Test
    fun `a job is reported once, when it leaves the queue`() = runTest {
        val library = PlatformSyncQueue.Job.Library(initializeFirst = true)
        val platform = PlatformSyncQueue.Job.Platform(nes, "p1")
        val active = flowOf(null, library, library, null, platform, null, null)

        assertEquals(listOf(library, platform), active.jobCompletions().toList())
    }

    @Test
    fun `a job replaced by the next one without an idle gap between them still counts as finished`() = runTest {
        val library = PlatformSyncQueue.Job.Library(initializeFirst = false)
        val platform = PlatformSyncQueue.Job.Platform(snes, "p2")
        val active = flowOf(library, platform, null)

        assertEquals(listOf(library, platform), active.jobCompletions().toList())
    }
}
