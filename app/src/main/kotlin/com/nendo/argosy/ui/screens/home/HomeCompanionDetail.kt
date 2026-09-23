package com.nendo.argosy.ui.screens.home

import com.nendo.argosy.ui.dualscreen.CompanionDetail
import com.nendo.argosy.ui.dualscreen.CompanionGameStats

fun HomeGameUi.toCompanionDetail(): CompanionDetail = CompanionDetail(
    title = title,
    subtitle = platformDisplayName,
    artUrl = coverPath,
    backdropUrl = backgroundPath,
    isGameTitle = true,
    spineUrl = boxSpinePath,
    stats = CompanionGameStats(
        developer = developer,
        releaseYear = releaseYear,
        genre = genre,
        communityRating = rating,
        userRating = userRating,
        playTimeMinutes = playTimeMinutes,
        timeToBeatMainSec = timeToBeatMainSec,
        achievementCount = achievementCount,
        earnedAchievementCount = earnedAchievementCount
    )
)
