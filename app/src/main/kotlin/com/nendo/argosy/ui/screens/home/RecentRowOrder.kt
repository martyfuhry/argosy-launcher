package com.nendo.argosy.ui.screens.home

import com.nendo.argosy.data.local.entity.GameEntity
import java.time.Instant
import java.time.temporal.ChronoUnit

internal const val NEW_GAME_THRESHOLD_HOURS = 24L
internal const val RECENT_PLAYED_THRESHOLD_HOURS = 4L
internal const val RECENT_ROW_SETTLE_MS = 500L

/**
 * The Continue row's order at [now]: games played within [RECENT_PLAYED_THRESHOLD_HOURS], then
 * unplayed games added within [NEW_GAME_THRESHOLD_HOURS], then the rest, each newest first with
 * the higher id first between equal times.
 */
internal fun orderRecentGames(games: List<GameEntity>, now: Instant): List<GameEntity> {
    val newThreshold = now.minus(NEW_GAME_THRESHOLD_HOURS, ChronoUnit.HOURS)
    val recentPlayedThreshold = now.minus(RECENT_PLAYED_THRESHOLD_HOURS, ChronoUnit.HOURS)
    return games.sortedWith(
        compareBy<GameEntity> { game ->
            when {
                game.lastPlayed?.isAfter(recentPlayedThreshold) == true -> 0
                game.lastPlayed == null && game.addedAt.isAfter(newThreshold) -> 1
                else -> 2
            }
        }.thenByDescending { game ->
            (game.lastPlayed ?: game.addedAt).toEpochMilli()
        }.thenByDescending { it.id }
    )
}

