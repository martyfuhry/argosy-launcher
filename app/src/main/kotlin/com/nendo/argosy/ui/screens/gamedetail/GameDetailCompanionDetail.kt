package com.nendo.argosy.ui.screens.gamedetail

import androidx.compose.ui.graphics.Color
import com.nendo.argosy.data.social.FriendActivity
import com.nendo.argosy.ui.dualscreen.CompanionDetail
import com.nendo.argosy.ui.dualscreen.CompanionGameStats
import com.nendo.argosy.ui.dualscreen.showcaseAppPackage

fun GameDetailUi.toCompanionDetail(
    friends: List<FriendActivity> = emptyList(),
    accentColors: Pair<Color, Color>? = null
): CompanionDetail =
    CompanionDetail(
        title = title,
        subtitle = platformName,
        platformSlug = platformSlug,
        artUrl = coverPath,
        backdropUrl = backgroundPath,
        backdropIsScreenshotOrArt = backgroundPath != null,
        isGameTitle = true,
        spineUrl = boxSpinePath,
        backUrl = boxBackPath,
        gameId = id,
        appPackageName = showcaseAppPackage(isAndroidApp, packageName, coverPath),
        accentColors = accentColors,
        stats = CompanionGameStats(
            developer = developer,
            releaseYear = releaseYear,
            players = players,
            genre = genre,
            communityRating = rating,
            userRating = userRating,
            userDifficulty = userDifficulty,
            playTimeMinutes = playTimeMinutes,
            timeToBeatMainSec = timeToBeatMainSec,
            timeToBeatCompletionistSec = timeToBeatCompletionistSec,
            status = status,
            achievementCount = achievements.size,
            earnedAchievementCount = achievements.count { it.isUnlocked },
            friends = friends,
            lastPlayedAt = lastPlayedAt
        )
    )
