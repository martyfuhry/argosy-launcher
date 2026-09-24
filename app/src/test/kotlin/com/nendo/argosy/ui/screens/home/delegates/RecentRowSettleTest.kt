package com.nendo.argosy.ui.screens.home.delegates

import com.nendo.argosy.data.local.entity.GameEntity
import com.nendo.argosy.data.model.GameSource
import com.nendo.argosy.data.preferences.UserPreferences
import com.nendo.argosy.data.preferences.UserPreferencesRepository
import com.nendo.argosy.data.repository.GameRepository
import com.nendo.argosy.ui.screens.common.GradientExtractionDelegate
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.temporal.ChronoUnit

@OptIn(ExperimentalCoroutinesApi::class)
class RecentRowSettleTest {

    private val tableWrites = MutableSharedFlow<List<GameEntity>>(extraBufferCapacity = 256)
    private var played = listOf(game(299, lastPlayed = Instant.now().minus(1, ChronoUnit.HOURS)))
    private var added = listOf(game(2384, addedAt = Instant.now().minus(2, ChronoUnit.HOURS)))

    private val gameRepository = mockk<GameRepository>(relaxed = true).also {
        coEvery { it.awaitStorageReady(any()) } returns true
        every { it.observeRecentlyPlayed(any()) } returns tableWrites
        coEvery { it.getRecentlyPlayed(any()) } answers { played }
        coEvery { it.getNewlyAdded(any(), any(), any()) } answers { added }
    }
    private val preferences = mockk<UserPreferencesRepository>(relaxed = true).also {
        every { it.userPreferences } returns flowOf(UserPreferences())
    }
    private val gradients = mockk<GradientExtractionDelegate>(relaxed = true).also {
        every { it.getGradient(any()) } returns null
    }

    private val delegate = HomeLibraryDelegate(
        context = mockk(relaxed = true),
        preferencesRepository = preferences,
        gameRepository = gameRepository,
        platformRepository = mockk(relaxed = true),
        generateRecommendationsUseCase = mockk(relaxed = true),
        getPinnedCollectionsUseCase = mockk(relaxed = true),
        getGamesForPinnedCollectionUseCase = mockk(relaxed = true),
        gradientExtractionDelegate = gradients,
        emulatorDetector = mockk(relaxed = true),
        notificationManager = mockk(relaxed = true),
        repairImageCacheUseCase = mockk(relaxed = true),
        downloadFileStatusRepository = mockk(relaxed = true),
        steamPathResolver = mockk(relaxed = true),
        collectionRepository = mockk(relaxed = true),
        appsRepository = mockk(relaxed = true)
    )

    @Test
    fun `a burst of library writes publishes the row once`() = runTest {
        val published = mutableListOf<List<Long>>()
        delegate.observeRecentlyPlayedChanges(backgroundScope) { rows -> published += rows.map { it.id } }
        runCurrent()

        repeat(40) { write ->
            added = added + game(3000L + write, addedAt = Instant.now().minus(3, ChronoUnit.HOURS))
            tableWrites.emit(played)
            advanceTimeBy(100)
        }
        advanceTimeBy(1_000)
        runCurrent()

        assertEquals(1, published.size)
        assertEquals(32, published.single().size)
        assertEquals(listOf(299L, 2384L, 3039L), published.single().take(3))
    }

    @Test
    fun `writes that leave the row as it was publish nothing`() = runTest {
        val published = mutableListOf<List<Long>>()
        delegate.observeRecentlyPlayedChanges(backgroundScope) { rows -> published += rows.map { it.id } }
        runCurrent()
        tableWrites.emit(played)
        advanceTimeBy(1_000)
        runCurrent()

        repeat(3) {
            tableWrites.emit(played)
            advanceTimeBy(1_000)
            runCurrent()
        }

        assertEquals(1, published.size)
        assertEquals(listOf(299L, 2384L), delegate.state.value.recentGames.map { it.id })
    }

    @Test
    fun `a write that changes the row publishes the new row after it settles`() = runTest {
        val published = mutableListOf<List<Long>>()
        delegate.observeRecentlyPlayedChanges(backgroundScope) { rows -> published += rows.map { it.id } }
        runCurrent()
        tableWrites.emit(played)
        advanceTimeBy(1_000)
        runCurrent()

        added = added + game(2400, addedAt = Instant.now().minus(1, ChronoUnit.MINUTES))
        tableWrites.emit(played)
        advanceTimeBy(100)
        runCurrent()
        assertEquals(1, published.size)

        advanceTimeBy(1_000)
        runCurrent()
        assertEquals(listOf(299L, 2400L, 2384L), published.last())
    }

    private fun game(id: Long, lastPlayed: Instant? = null, addedAt: Instant = Instant.now().minus(90, ChronoUnit.DAYS)) =
        GameEntity(
            id = id,
            platformId = 1L,
            platformSlug = "gba",
            title = "Game $id",
            sortTitle = "game $id",
            localPath = null,
            rommId = id,
            igdbId = null,
            source = GameSource.ROMM_REMOTE,
            lastPlayed = lastPlayed,
            addedAt = addedAt
        )
}
