package com.nendo.argosy.ui.screens.home

import com.nendo.argosy.domain.model.HomeLayoutKind
import org.junit.Assert.assertEquals
import org.junit.Test

class RecentRowFocusTest {

    @Test
    fun `the focused game stays focused when the row reorders around it`() {
        val state = HomeUiState(
            currentRow = HomeRow.Continue,
            layoutKind = HomeLayoutKind.CAROUSEL,
            recentGames = listOf(game(1), game(2), game(3)),
            focusedGameIndex = 1
        )

        val updated = state.withRecentGames(listOf(game(5), game(3), game(1), game(2)))

        assertEquals(2L, updated.focusedGame?.id)
    }

    @Test
    fun `a focused game that left the row leaves the cursor inside the row`() {
        val state = HomeUiState(
            currentRow = HomeRow.Continue,
            layoutKind = HomeLayoutKind.AUTO_GRID,
            recentGames = listOf(game(1), game(2), game(3)),
            focusedGameIndex = 2
        )

        val updated = state.withRecentGames(listOf(game(1), game(2)))

        assertEquals(2L, updated.focusedGame?.id)
    }

    private fun game(id: Long) = HomeGameUi(
        id = id,
        title = "Game $id",
        platformId = 1L,
        platformSlug = "gba",
        platformDisplayName = "Game Boy Advance",
        coverPath = null,
        backgroundPath = null,
        developer = null,
        releaseYear = null,
        genre = null,
        isFavorite = false,
        isDownloaded = true
    )
}
