package com.nendo.argosy.ui.dualscreen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.nendo.argosy.ui.components.FooterBar
import com.nendo.argosy.ui.components.HomeLayoutPreview
import com.nendo.argosy.ui.components.ScreenNumberBadge
import com.nendo.argosy.ui.theme.Dimens
import com.nendo.argosy.ui.theme.LocalArgosyTheme
import com.nendo.argosy.ui.theme.backdrop.BackdropRole
import com.nendo.argosy.ui.theme.backdrop.surfaceBackdrop

@Composable
fun PresentationSlotContent(slot: PresentationSlot) {
    Box(modifier = Modifier.fillMaxSize().surfaceBackdrop(BackdropRole.WALLPAPER)) {
        when (slot) {
            PresentationSlot.Fallback -> Unit
            is PresentationSlot.HomeLayoutPreview -> Box(
                modifier = Modifier.fillMaxSize().padding(Dimens.spacingLg),
                contentAlignment = Alignment.Center
            ) {
                HomeLayoutPreview(settings = slot.settings, modifier = Modifier.fillMaxWidth())
            }
            is PresentationSlot.PlayTime -> PlayTimeSlot(slot)
            is PresentationSlot.Detail -> CompanionDetailScreen(
                detail = slot.detail,
                modifier = Modifier.fillMaxSize(),
                footerHints = slot.detail.hints
                    .takeIf { it.isNotEmpty() }
                    ?.let { hints ->
                        { FooterBar(hints = hints.map { it.button to it.label }) }
                    }
            )
            is PresentationSlot.InGame -> {
                val manager = com.nendo.argosy.DualScreenManagerHolder.instance
                CompanionDashboard(
                    state = slot.state,
                    sessionTimer = manager?.swappedSessionTimer,
                    liveAchievements = slot.achievements,
                    onQuickSave = { manager?.sessionQuickActions?.quickSave() },
                    onQuickLoad = { manager?.sessionQuickActions?.quickLoad() },
                    onScreenshot = { manager?.sessionQuickActions?.screenshot() }
                )
            }
            is PresentationSlot.ScreenIdentity -> Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.BottomEnd
            ) {
                ScreenNumberBadge(
                    number = slot.number,
                    modifier = Modifier.padding(Dimens.spacingLg)
                )
            }
        }
    }
}

@Composable
private fun PlayTimeSlot(slot: PresentationSlot.PlayTime) {
    val theme = LocalArgosyTheme.current
    Column(
        modifier = Modifier.fillMaxSize().padding(Dimens.spacingLg),
        verticalArrangement = Arrangement.spacedBy(Dimens.spacingSm)
    ) {
        Text(
            text = slot.dateLabel ?: slot.sectionLabel,
            style = MaterialTheme.typography.titleMedium,
            color = theme.textPrimary
        )
        LazyColumn(verticalArrangement = Arrangement.spacedBy(Dimens.spacingXs)) {
            items(slot.games, key = { it.gameId }) { game ->
                Column {
                    Text(
                        text = game.title,
                        style = MaterialTheme.typography.bodyMedium,
                        color = theme.textPrimary
                    )
                    Text(
                        text = game.detail,
                        style = MaterialTheme.typography.labelSmall,
                        color = theme.textDim
                    )
                }
            }
        }
    }
}
