package com.nendo.argosy.ui.common

import android.content.Context
import com.nendo.argosy.R
import com.nendo.argosy.data.local.dao.PlatformShowcaseStats
import com.nendo.argosy.data.repository.GameRepository
import com.nendo.argosy.ui.dualscreen.CompanionFact
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

private const val PLATFORM_ID = 7L
private const val OTHER_PLATFORM_ID = 8L

class PresentationShowcaseSourceTest {

    private val context = mockk<Context> {
        every { getString(any<Int>()) } answers { "res:${firstArg<Int>()}" }
    }
    private val gameRepository = mockk<GameRepository>()
    private val source = PresentationShowcaseSource(context, gameRepository, mockk(), mockk(), mockk())

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
    fun `a platform showcase reads one cover per group and that platform's own stats`() = runBlocking {
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
    fun `a platform with no stats row falls back to a zero game count and no other facts`() = runBlocking {
        coEvery { gameRepository.showcaseCovers(PLATFORM_ID, true) } returns emptyList()
        coEvery { gameRepository.statsByPlatform() } returns mapOf(OTHER_PLATFORM_ID to stats(OTHER_PLATFORM_ID, 40))

        val showcase = source.platformShowcase(PLATFORM_ID, "Super Nintendo")

        assertEquals(listOf(CompanionFact("res:${R.string.library_showcase_fact_games}", "0")), showcase.facts)
        assertNull(showcase.yearSpan)
    }
}
