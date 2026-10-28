package com.nendo.argosy.ui.common

import android.content.Context
import androidx.compose.ui.graphics.Color
import com.nendo.argosy.data.cache.GradientPreset
import com.nendo.argosy.data.local.entity.GameEntity
import com.nendo.argosy.data.model.GameSource
import com.nendo.argosy.data.model.ResolvedGameArt
import com.nendo.argosy.data.repository.GameRepository
import com.nendo.argosy.data.repository.PlatformRepository
import com.nendo.argosy.ui.screens.common.GradientExtractionDelegate
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

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
        mockk<Context>(relaxed = true),
        gameRepository,
        platformRepository,
        mockk(relaxed = true),
        mockk(relaxed = true),
        gradients
    )

    @Test
    fun `an app whose gradient is only saved on its art still gets its accent colours`() = runTest {
        val detail = source.gameDetail(gameId)

        assertEquals(savedPair, detail?.accentColors)
    }
}
