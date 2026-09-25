package com.nendo.argosy.data.repository

import com.nendo.argosy.data.local.dao.HomeTileDao
import com.nendo.argosy.data.local.entity.HomeTileEntity
import com.nendo.argosy.domain.model.FeatureTileKind
import com.nendo.argosy.domain.model.HomeTileTargetRef
import com.nendo.argosy.domain.model.TileRect
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

private const val OWNER = 1L
private const val TILE_ID = 7L
private const val KEY_PICKED_GAME_ID = "pickedGameId"

class HomeTileRepositoryTest {

    private val dao = mockk<HomeTileDao>(relaxed = true)
    private val repository = HomeTileRepository(dao)

    private suspend fun roundTrip(
        target: HomeTileTargetRef.Feature
    ): Pair<HomeTileEntity, HomeTileTargetRef> {
        val written = slot<HomeTileEntity>()
        coEvery { dao.insert(capture(written)) } returns TILE_ID
        repository.place(ownerUserId = OWNER, pageIndex = 0, rect = TileRect(0, 0), target = target)
        val row = written.captured.copy(id = TILE_ID)
        every { dao.observeTiles(OWNER) } returns flowOf(listOf(row))
        every { dao.observeAllEpisodes() } returns flowOf(emptyList())
        return row to repository.observeTiles(OWNER).first().single().target
    }

    @Test
    fun `an RA tile keeps its tracked game across a write and a read`() = runTest {
        val target = HomeTileTargetRef.Feature(FeatureTileKind.RA_SUMMARY, pickedGameId = 42L)

        val (row, read) = roundTrip(target)

        assertEquals(FeatureTileKind.RA_SUMMARY.name, row.featureKind)
        assertEquals(42L, JSONObject(row.featureConfig.orEmpty()).getLong(KEY_PICKED_GAME_ID))
        assertEquals(target, read)
    }

    @Test
    fun `an RA tile with no tracked game stores no pick and reads back null`() = runTest {
        val target = HomeTileTargetRef.Feature(FeatureTileKind.RA_SUMMARY)

        val (row, read) = roundTrip(target)

        assertFalse(JSONObject(row.featureConfig.orEmpty()).has(KEY_PICKED_GAME_ID))
        assertTrue(read is HomeTileTargetRef.Feature)
        assertEquals(null, (read as HomeTileTargetRef.Feature).pickedGameId)
        assertEquals(target, read)
    }

    /**
     * A stored link carries every category the library can apply. One dropped in the round trip
     * is a filter the user set and the opened library never sees.
     */
    @Test
    fun `a library link keeps every filter across a write and a read`() = runTest {
        val target = HomeTileTargetRef.Feature(
            kind = FeatureTileKind.LIBRARY_LINK,
            libraryLink = com.nendo.argosy.domain.model.LibraryLinkFilters(
                source = com.nendo.argosy.data.model.SourceFilter.FAVORITES,
                platformIds = setOf(3L, 9L),
                genres = setOf("Role-playing (RPG)"),
                regions = setOf("Europe"),
                series = setOf("Mega Man"),
                players = com.nendo.argosy.domain.model.PlayerCountBucket.FOUR_PLUS,
                sort = com.nendo.argosy.data.model.ActiveSort(
                    com.nendo.argosy.data.model.SortOption.RELEASE_YEAR,
                    descending = false
                )
            )
        )

        val (row, read) = roundTrip(target)

        assertEquals(FeatureTileKind.LIBRARY_LINK.name, row.featureKind)
        assertEquals(target, read)
    }

    @Test
    fun `a link with no stored config reads back unfiltered`() = runTest {
        every { dao.observeAllEpisodes() } returns flowOf(emptyList())
        every { dao.observeTiles(OWNER) } returns flowOf(
            listOf(
                HomeTileEntity(
                    id = TILE_ID,
                    ownerUserId = OWNER,
                    pageIndex = 0,
                    columnIndex = 0,
                    rowIndex = 0,
                    targetType = "FEATURE",
                    featureKind = FeatureTileKind.LIBRARY_LINK.name
                )
            )
        )

        val read = repository.observeTiles(OWNER).first().single().target

        assertTrue(
            (read as HomeTileTargetRef.Feature).libraryLink?.isUnfiltered == true
        )
    }
}
