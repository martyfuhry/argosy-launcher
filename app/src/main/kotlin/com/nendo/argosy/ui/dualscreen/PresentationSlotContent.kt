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
import com.nendo.argosy.ui.components.HomeLayoutPreview
import com.nendo.argosy.ui.theme.Dimens
import com.nendo.argosy.ui.theme.LocalArgosyTheme

/**
 * Draws whatever the screen on the control surface published. Each case renders the composable that
 * screen authored for this display; the fallback draws nothing at all, so an idle presentation
 * screen shows the wallpaper behind it rather than a menu nobody can reach.
 */
@Composable
fun PresentationSlotContent(slot: PresentationSlot) {
    when (slot) {
        PresentationSlot.Fallback -> Unit
        is PresentationSlot.HomeLayoutPreview -> Box(
            modifier = Modifier.fillMaxSize().padding(Dimens.spacingLg),
            contentAlignment = Alignment.Center
        ) {
            HomeLayoutPreview(settings = slot.settings, modifier = Modifier.fillMaxWidth())
        }
        is PresentationSlot.PlayTime -> PlayTimeSlot(slot)
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
