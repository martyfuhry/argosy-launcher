package com.nendo.argosy.ui.screens.gamedetail

import androidx.compose.ui.graphics.Color
import com.nendo.argosy.core.game.AchievementUi
import com.nendo.argosy.data.local.entity.GameEntity
import com.nendo.argosy.data.model.GameSource
import com.nendo.argosy.ui.dualscreen.CompanionDetail
import com.nendo.argosy.ui.dualscreen.CompanionGameStats
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant

class GameDetailCompanionDetailTest {
    @Test
    fun `detail publishes artwork and typed stats to the shared showcase`() {
        val game = GameEntity(
            id = 42,
            platformId = 1,
            platformSlug = "snes",
            title = "Test game",
            sortTitle = "Test game",
            localPath = null,
            rommId = 42,
            igdbId = null,
            source = GameSource.ROMM_REMOTE
        ).toGameDetailUi(
            art = null,
            screenshotRows = emptyList(),
            platformName = "SNES",
            emulatorName = null,
            canPlay = false
        ).copy(
            coverPath = "/cover.png",
            backgroundPath = "/background.png",
            boxSpinePath = "/spine.png",
            developer = "Studio",
            releaseYear = 1995,
            players = "1-2",
            genre = "Platformer",
            rating = 84f,
            userRating = 8,
            userDifficulty = 3,
            playTimeMinutes = 120,
            timeToBeatMainSec = 3600,
            timeToBeatCompletionistSec = 7200,
            status = "finished",
            achievements = listOf(
                AchievementUi(1, "First", null, 5, null, null, isUnlocked = true),
                AchievementUi(2, "Second", null, 5, null, null)
            )
        )

        assertEquals(
            CompanionDetail(
                title = "Test game",
                subtitle = "SNES",
                platformSlug = "snes",
                artUrl = "/cover.png",
                backdropUrl = "/background.png",
                backdropIsScreenshotOrArt = true,
                spineUrl = "/spine.png",
                isGameTitle = true,
                gameId = 42,
                stats = CompanionGameStats(
                    developer = "Studio",
                    releaseYear = 1995,
                    players = "1-2",
                    genre = "Platformer",
                    communityRating = 84f,
                    userRating = 8,
                    userDifficulty = 3,
                    playTimeMinutes = 120,
                    timeToBeatMainSec = 3600,
                    timeToBeatCompletionistSec = 7200,
                    status = "finished",
                    achievementCount = 2,
                    earnedAchievementCount = 1
                )
            ),
            game.toCompanionDetail()
        )
        assertNull(game.toCompanionDetail().accentColors)
        assertNull(game.toCompanionDetail().stats?.lastPlayedAt)
    }

    @Test
    fun `an app whose cover is its cached icon publishes its package for the app treatment`() {
        val app = appDetail(isRommGame = true, coverPath = "/cache/argosy_images/android/icons/appicon_5.png")

        assertEquals("com.example.port", app.toCompanionDetail().appPackageName)
    }

    @Test
    fun `an app publishes its accent colours and last played time for the wash and the stats`() {
        val accent = Color.Red to Color.Blue
        val app = appDetail(
            isRommGame = false,
            coverPath = "/cache/argosy_images/android/icons/appicon_5.png",
            lastPlayed = Instant.ofEpochMilli(1_700_000_000_000)
        )

        val detail = app.toCompanionDetail(accentColors = accent)

        assertEquals(accent, detail.accentColors)
        assertEquals(1_700_000_000_000, detail.stats?.lastPlayedAt)
    }

    @Test
    fun `an app that RomM gave real box art publishes no package and keeps the game treatment`() {
        val app = appDetail(isRommGame = true, coverPath = "/cache/argosy_images/android/covers/cover_5.jpg")

        assertNull(app.toCompanionDetail().appPackageName)
    }

    private fun appDetail(isRommGame: Boolean, coverPath: String, lastPlayed: Instant? = null) = GameEntity(
        id = 5,
        platformId = 1,
        platformSlug = "android",
        title = "Port",
        sortTitle = "Port",
        localPath = null,
        rommId = null,
        igdbId = null,
        source = GameSource.ANDROID_APP,
        lastPlayed = lastPlayed
    ).toGameDetailUi(
        art = null,
        screenshotRows = emptyList(),
        platformName = "Android",
        emulatorName = null,
        canPlay = true
    ).copy(
        coverPath = coverPath,
        isRommGame = isRommGame,
        isAndroidApp = true,
        packageName = "com.example.port"
    )
}
