package com.nendo.argosy.ui.common

import androidx.compose.ui.graphics.Color
import com.nendo.argosy.DualScreenManager
import com.nendo.argosy.DualScreenManagerHolder
import com.nendo.argosy.ui.dualscreen.CompanionDetail
import io.mockk.coEvery
import io.mockk.mockk
import io.mockk.verify
import io.mockk.verifyOrder
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PresentationFocusTest {

    private val gameId = 7L
    private val manager = mockk<DualScreenManager>(relaxed = true)
    private val flat = CompanionDetail(title = "Test App", gameId = gameId)
    private val coloured = flat.copy(accentColors = Color.Red to Color.Blue)

    @Before
    fun setUp() {
        DualScreenManagerHolder.instance = manager
    }

    @After
    fun tearDown() {
        DualScreenManagerHolder.instance = null
    }

    @Test
    fun `a focused game published without colours is published again once its colours arrive`() = runTest {
        val arrived = CompletableDeferred<Unit>()
        val source = mockk<PresentationShowcaseSource>()
        coEvery { source.gameDetail(gameId, any()) } returnsMany listOf(flat, coloured)
        coEvery { source.backfillLogo(gameId) } returns false
        coEvery { source.awaitGradient(gameId) } coAnswers { arrived.await() }
        val focus = PresentationFocus("test.focus", Any(), source, backgroundScope)

        focus.startDescribing()
        focus.show(PresentationTarget.Game(gameId))
        runCurrent()

        verify(exactly = 0) { manager.setCompanionDetail(any(), coloured) }

        arrived.complete(Unit)
        runCurrent()

        verifyOrder {
            manager.setCompanionDetail(any(), flat)
            manager.setCompanionDetail(any(), coloured)
        }
    }
}
