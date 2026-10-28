package com.nendo.argosy.ui.common

import android.content.Context
import androidx.compose.ui.graphics.Color
import com.nendo.argosy.R
import com.nendo.argosy.data.cache.GradientPreset
import com.nendo.argosy.data.local.dao.PlatformShowcaseStats
import com.nendo.argosy.data.local.entity.GameEntity
import com.nendo.argosy.data.model.GameSource
import com.nendo.argosy.data.model.ResolvedGameArt
import com.nendo.argosy.data.repository.GameRepository
import com.nendo.argosy.data.repository.PlatformRepository
import com.nendo.argosy.ui.dualscreen.CompanionFact
import com.nendo.argosy.ui.screens.common.GradientExtractionDelegate
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

private const val PLATFORM_ID = 7L
private const val OTHER_PLATFORM_ID = 8L

class PresentationShowcaseSourceTest {

    private val gameId = 7L
    private val savedJson = "saved-gradient"
    private val savedPair = Color.Red to Color.Blue

    private val app = GameEntity(
        id = gameId,
        platformId = 1L,
        title = "Test App",
        sortTitle = "test app",
        localPath = null,
        rommId = null,
        igdbId = null,
        packageName = "com.example.app",
        source = GameSource.ANDROID_APP
    )

    private val context = mockk<Context>(relaxed = true) {
        every { getString(any<Int>()) } answers { "res:${firstArg<Int>()}" }
    }

    private val gameRepository = mockk<GameRepository>(relaxed = true).also {
        coEvery { it.getById(gameId) } returns app
        coEvery { it.getArt(gameId) } returns ResolvedGameArt(coverPath = "/covers/app.png", gradientColors = savedJson)
        coEvery { it.getScreenshots(gameId) } returns emptyList()
    }

    private val platformRepository = mockk<PlatformRepository>(relaxed = true).also {
        coEvery { it.getById(any()) } returns null
    }

    private val extractor = mockk<GradientColorExtractor>(relaxed = true).also {
        every { it.deserializeAllPresets(savedJson) } returns mapOf(GradientPreset.BALANCED to savedPair)
    }

    private val gradients = GradientExtractionDelegate(
        extractor,
        gameRepository,
        mockk(relaxed = true),
        mockk(relaxed = true),
        mockk(relaxed = true)
    )

    private val source = PresentationShowcaseSource(
        context,
        gameRepository,
        platformRepository,
        mockk(relaxed = true),
        mockk(relaxed = true),
        gradients
    )

    private fun stats(platformId: Long, games: Int) = PlatformShowcaseStats(
        platformId = platformId,
        gameCount = games,
        installedCount = 2,
        achievementsEarned = 0,
        achievementsTotal = 0,
        playTimeMinutes = 0,
        earliestYear = 1991,
        latestYear = 1996
    )

    @Test
    fun `an app whose gradient is only saved on its art still gets its accent colours`() = runTest {
        val detail = source.gameDetail(gameId)

        assertEquals(savedPair, detail?.accentColors)
    }

    @Test
    fun `a platform showcase reads one cover per group and that platform's own stats`() = runTest {
        coEvery { gameRepository.showcaseCovers(PLATFORM_ID, true) } returns listOf("a.png", "b.png")
        coEvery { gameRepository.statsByPlatform() } returns mapOf(
            PLATFORM_ID to stats(PLATFORM_ID, 12),
            OTHER_PLATFORM_ID to stats(OTHER_PLATFORM_ID, 40)
        )

        val showcase = source.platformShowcase(PLATFORM_ID, "Super Nintendo")

        coVerify { gameRepository.showcaseCovers(PLATFORM_ID, true) }
        assertEquals("Super Nintendo", showcase.name)
        assertEquals(listOf("a.png", "b.png"), showcase.coverPaths)
        assertEquals("1991-1996", showcase.yearSpan)
        assertEquals(CompanionFact("res:${R.string.library_showcase_fact_games}", "12"), showcase.facts.first())
    }

    @Test
    fun `a platform with no stats row falls back to a zero game count and no other facts`() = runTest {
        coEvery { gameRepository.showcaseCovers(PLATFORM_ID, true) } returns emptyList()
        coEvery { gameRepository.statsByPlatform() } returns mapOf(OTHER_PLATFORM_ID to stats(OTHER_PLATFORM_ID, 40))

        val showcase = source.platformShowcase(PLATFORM_ID, "Super Nintendo")

        assertEquals(listOf(CompanionFact("res:${R.string.library_showcase_fact_games}", "0")), showcase.facts)
        assertNull(showcase.yearSpan)
    }
}
