package com.nendo.argosy.ui.screens.home

import com.nendo.argosy.data.local.entity.GameEntity
import com.nendo.argosy.data.model.GameSource
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.temporal.ChronoUnit

class RecentRowOrderTest {

    private val now = Instant.parse("2026-09-23T00:40:00Z")

    @Test
    fun `games sharing a timestamp come out in one order whatever order they went in`() {
        val stamp = now.minus(2, ChronoUnit.HOURS)
        val games = listOf(
            game(id = 4, addedAt = stamp),
            game(id = 9, addedAt = stamp),
            game(id = 1, addedAt = stamp),
            game(id = 7, lastPlayed = stamp.minus(10, ChronoUnit.DAYS), addedAt = stamp.minus(30, ChronoUnit.DAYS)),
            game(id = 3, lastPlayed = stamp.minus(10, ChronoUnit.DAYS), addedAt = stamp.minus(30, ChronoUnit.DAYS))
        )

        val orders = listOf(games, games.reversed(), games.shuffled(java.util.Random(1)), games.shuffled(java.util.Random(2)))
            .map { input -> orderRecentGames(input, now).map { it.id } }

        assertEquals(listOf(9L, 4L, 1L, 7L, 3L), orders.first())
        orders.forEach { assertEquals(orders.first(), it) }
    }

    @Test
    fun `a game played in the last hours leads a game added after it`() {
        val played = game(id = 299, lastPlayed = Instant.parse("2026-09-23T00:29:10Z"), addedAt = now.minus(90, ChronoUnit.DAYS))
        val added = game(id = 2384, addedAt = Instant.parse("2026-09-23T00:29:37Z"))

        assertEquals(listOf(299L, 2384L), orderRecentGames(listOf(added, played), now).map { it.id })
    }

    @Test
    fun `an unplayed new game leads a game last played days ago`() {
        val played = game(id = 299, lastPlayed = now.minus(3, ChronoUnit.DAYS), addedAt = now.minus(90, ChronoUnit.DAYS))
        val added = game(id = 2384, addedAt = now.minus(1, ChronoUnit.HOURS))

        assertEquals(listOf(2384L, 299L), orderRecentGames(listOf(played, added), now).map { it.id })
    }

    private fun game(id: Long, lastPlayed: Instant? = null, addedAt: Instant) = GameEntity(
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
