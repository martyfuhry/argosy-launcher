package com.nendo.argosy.ui.dualscreen

/**
 * What the showcase screen draws while the driven screen is somewhere other than Home.
 *
 * One shape rather than one per destination. Library and Media focus different things, a platform
 * and a title, but the showcase says the same kind of thing about both: what it is called, a line
 * of context, some artwork and whatever facts are worth reading from across a room. Modelling each
 * destination separately would mean a second surface to keep in step every time either changes.
 *
 * [backdropUrl] is the full-bleed image and [artUrl] the upright one; a destination that has only
 * one of them leaves the other null rather than substituting, since a poster stretched across a
 * landscape frame is what makes a background look soft.
 *
 * [isGameTitle] routes [title] through the series/entry formatter. A film's title is one phrase and
 * splitting it on a colon would misread it, so the destination that knows it is publishing a game
 * says so rather than the renderer guessing from the punctuation.
 */
data class CompanionDetail(
    val title: String,
    val subtitle: String? = null,
    val overview: String? = null,
    val artUrl: String? = null,
    val backdropUrl: String? = null,
    val facts: List<CompanionFact> = emptyList(),
    val isGameTitle: Boolean = false,
    val spineUrl: String? = null,
    val stats: CompanionGameStats? = null
)

/**
 * A focused game's values, kept typed so the presentation style decides which appear and the
 * renderer formats them. Zero counts and null values mean the game has no such value.
 */
data class CompanionGameStats(
    val developer: String? = null,
    val releaseYear: Int? = null,
    val genre: String? = null,
    val communityRating: Float? = null,
    val userRating: Int = 0,
    val playTimeMinutes: Int = 0,
    val timeToBeatMainSec: Int? = null,
    val achievementCount: Int = 0,
    val earnedAchievementCount: Int = 0,
    val friends: List<CompanionFriend> = emptyList()
)

data class CompanionFriend(
    val name: String,
    val playingNow: Boolean
)

data class CompanionFact(
    val label: String,
    val value: String
)

/**
 * What a button does on the screen being driven, spelled out on the screen describing it.
 */
data class CompanionHint(
    val button: com.nendo.argosy.ui.components.InputButton,
    val label: String
)
