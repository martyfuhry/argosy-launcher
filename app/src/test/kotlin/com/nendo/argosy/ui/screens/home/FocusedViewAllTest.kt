package com.nendo.argosy.ui.screens.home

import com.nendo.argosy.domain.model.HomeLayoutKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

private const val PLATFORM_ID = 7L
private const val GAME_ID = 11L

class FocusedViewAllTest {

    private val platform = HomePlatformUi(
        id = PLATFORM_ID,
        slug = "snes",
        name = "Super Nintendo",
        shortName = "SNES",
        displayName = "Super Nintendo",
        logoPath = null
    )
    private val platformViewAll = HomeRowItem.ViewAll(platformId = PLATFORM_ID, platformName = "Super Nintendo")

    private fun platformRow(layoutKind: HomeLayoutKind, focusedIndex: Int) = HomeUiState(
        layoutKind = layoutKind,
        platforms = listOf(platform),
        currentRow = HomeRow.Platform(0),
        platformItems = listOf(HomeRowItem.Game(game(GAME_ID)), platformViewAll),
        platformItemsFor = PLATFORM_ID,
        focusedGameIndex = focusedIndex
    )

    @Test
    fun `a platform row focused on its last item answers with that View All`() {
        assertEquals(platformViewAll, platformRow(HomeLayoutKind.CAROUSEL, 1).focusedViewAll)
    }

    @Test
    fun `favorites focused on its View All answers with the favorites filter`() {
        val state = HomeUiState(
            layoutKind = HomeLayoutKind.AUTO_GRID,
            currentRow = HomeRow.Favorites,
            favoriteGames = listOf(game(GAME_ID)),
            focusedGameIndex = 1
        )

        assertEquals("FAVORITES", state.focusedViewAll?.sourceFilter)
    }

    @Test
    fun `continue under the carousel focused on its View All answers with the playable filter`() {
        val state = HomeUiState(
            layoutKind = HomeLayoutKind.CAROUSEL,
            currentRow = HomeRow.Continue,
            recentGames = listOf(game(GAME_ID)),
            focusedGameIndex = 1
        )

        assertEquals("PLAYABLE", state.focusedViewAll?.sourceFilter)
    }

    @Test
    fun `continue under the auto grid has no View All past its last game`() {
        val state = HomeUiState(
            layoutKind = HomeLayoutKind.AUTO_GRID,
            currentRow = HomeRow.Continue,
            recentGames = listOf(game(GAME_ID)),
            focusedGameIndex = 1
        )

        assertNull(state.focusedViewAll)
    }

    @Test
    fun `the custom grid answers with no View All whatever the row cursor holds`() {
        assertNull(platformRow(HomeLayoutKind.CUSTOM_GRID, 1).focusedViewAll)
    }

    @Test
    fun `a platform row focused on a game answers with no View All`() {
        assertNull(platformRow(HomeLayoutKind.CAROUSEL, 0).focusedViewAll)
    }

    private fun game(id: Long) = HomeGameUi(
        id = id,
        title = "Game $id",
        platformId = PLATFORM_ID,
        platformSlug = "snes",
        platformDisplayName = "Super Nintendo",
        coverPath = null,
        backgroundPath = null,
        developer = null,
        releaseYear = null,
        genre = null,
        isFavorite = true,
        isDownloaded = true
    )
}
