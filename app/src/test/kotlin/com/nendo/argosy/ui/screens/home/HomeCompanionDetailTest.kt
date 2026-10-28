package com.nendo.argosy.ui.screens.home

import androidx.compose.ui.graphics.Color
import com.nendo.argosy.ui.dualscreen.CompanionDetail
import com.nendo.argosy.ui.dualscreen.CompanionGameStats
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

private const val PACKAGE = "com.example.port"
private const val ICON_PATH = "/cache/argosy_images/android/icons/appicon_123.png"
private const val LAST_PLAYED = 1_700_000_000_000L
private val ACCENTS = Color.Red to Color.Blue

class HomeCompanionDetailTest {

    private fun item(
        isAndroidApp: Boolean = false,
        isRommGame: Boolean = false,
        coverPath: String? = null,
        packageName: String? = null
    ) = HomeGameUi(
        id = 7,
        title = "Item",
        platformId = 1,
        platformSlug = "android",
        platformDisplayName = "Android",
        coverPath = coverPath,
        gradientColors = ACCENTS,
        backgroundPath = coverPath,
        developer = null,
        releaseYear = null,
        genre = null,
        isFavorite = false,
        isDownloaded = true,
        isRommGame = isRommGame,
        isAndroidApp = isAndroidApp,
        packageName = packageName,
        playTimeMinutes = 30,
        lastPlayedAt = LAST_PLAYED
    )

    @Test
    fun `a sideloaded app with only its cached icon carries its package accents and last played`() {
        val detail = item(isAndroidApp = true, coverPath = ICON_PATH, packageName = PACKAGE).toCompanionDetail()

        assertEquals(PACKAGE, detail.appPackageName)
        assertEquals(ACCENTS, detail.accentColors)
        assertEquals(LAST_PLAYED, detail.stats?.lastPlayedAt)
    }

    @Test
    fun `a RomM app that RomM gave real box art keeps the game treatment`() {
        val detail = item(
            isAndroidApp = true,
            isRommGame = true,
            coverPath = "/cache/argosy_images/android/covers/cover_9.jpg",
            packageName = PACKAGE
        ).toCompanionDetail()

        assertNull(detail.appPackageName)
    }

    @Test
    fun `a sideloaded app whose cover is a user override or a store graphic keeps the game treatment`() {
        val detail = item(
            isAndroidApp = true,
            coverPath = "/cache/argosy_images/android/covers/cover_override_7_abc.png",
            packageName = PACKAGE
        ).toCompanionDetail()

        assertNull(detail.appPackageName)
    }

    @Test
    fun `a RomM app whose cover is only its cached icon gets the app treatment`() {
        val detail = item(
            isAndroidApp = true,
            isRommGame = true,
            coverPath = ICON_PATH,
            packageName = PACKAGE
        ).toCompanionDetail()

        assertEquals(PACKAGE, detail.appPackageName)
    }

    @Test
    fun `a game maps as before with no package and its accents and last played as typed values`() {
        val detail = item(isRommGame = true, coverPath = "/cover.png").toCompanionDetail()

        assertEquals(
            CompanionDetail(
                title = "Item",
                subtitle = "Android",
                platformSlug = "android",
                artUrl = "/cover.png",
                backdropUrl = "/cover.png",
                isGameTitle = true,
                gameId = 7,
                accentColors = ACCENTS,
                stats = CompanionGameStats(playTimeMinutes = 30, lastPlayedAt = LAST_PLAYED)
            ),
            detail
        )
    }
}
