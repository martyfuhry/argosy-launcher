package com.nendo.argosy.ui.dualscreen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.nendo.argosy.ui.common.rememberFileImageModel
import com.nendo.argosy.ui.components.FooterBar
import com.nendo.argosy.ui.components.animateScrollToItemCentered
import com.nendo.argosy.ui.theme.generated.ComponentDefaults
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
            is PresentationSlot.PlayTimeline -> PlayTimelineSlot(slot)
            is PresentationSlot.Breakdown -> BreakdownSlot(slot)
            is PresentationSlot.Detail -> CompanionDetailScreen(
                detail = slot.detail,
                modifier = Modifier.fillMaxSize(),
                footerHints = slot.detail.hints
                    .takeIf { it.isNotEmpty() }
                    ?.let { hints ->
                        { FooterBar(hints = hints.map { it.button to it.label }) }
                    }
            )
            is PresentationSlot.PlatformShowcase -> PlatformShowcaseContent(slot)
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
private fun PlayTimelineSlot(slot: PresentationSlot.PlayTimeline) {
    val theme = LocalArgosyTheme.current
    val listState = rememberLazyListState()
    LaunchedEffect(slot.selectedIndex, slot.dots.size) {
        if (slot.selectedIndex in slot.dots.indices) {
            listState.animateScrollToItemCentered(slot.selectedIndex)
        }
    }
    Column(
        modifier = Modifier.fillMaxSize().padding(Dimens.spacingXl),
        verticalArrangement = Arrangement.spacedBy(Dimens.spacingLg)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom
        ) {
            Text(
                text = slot.dayLabel,
                style = MaterialTheme.typography.displaySmall,
                color = theme.textPrimary
            )
            slot.dayTotal?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.headlineSmall,
                    color = theme.focusAccent
                )
            }
        }
        LazyRow(
            state = listState,
            userScrollEnabled = false,
            modifier = Modifier.fillMaxWidth().height(Dimens.timelineTrackHeight),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Dimens.spacingXl)
        ) {
            itemsIndexed(slot.dots) { index, dot ->
                val selected = index == slot.selectedIndex
                val size = when {
                    selected && dot.hasActivity -> Dimens.timelineDotSelected
                    dot.hasActivity -> Dimens.timelineDotActive
                    else -> Dimens.timelineDotEmpty
                }
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(Dimens.spacingXs)
                ) {
                    Box(
                        modifier = Modifier
                            .size(size)
                            .then(
                                if (dot.hasActivity) {
                                    Modifier.clip(CircleShape).background(dot.color)
                                } else {
                                    Modifier
                                }
                            )
                    )
                    dot.label?.let {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.labelSmall,
                            color = theme.textDim
                        )
                    }
                }
            }
        }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(Dimens.spacingMd)) {
            items(slot.games, key = { it.gameId }) { game ->
                Row(
                    horizontalArrangement = Arrangement.spacedBy(Dimens.spacingMd),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AsyncImage(
                        model = rememberFileImageModel(game.coverPath),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .height(Dimens.timelineCoverHeight)
                            .aspectRatio(COVER_ASPECT)
                            .clip(RoundedCornerShape(Dimens.radiusSm))
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = game.title,
                            style = MaterialTheme.typography.titleMedium,
                            color = theme.textPrimary
                        )
                        game.subtitle?.let {
                            Text(
                                text = it,
                                style = MaterialTheme.typography.bodySmall,
                                color = theme.textDim
                            )
                        }
                    }
                    Text(
                        text = game.detail,
                        style = MaterialTheme.typography.titleMedium,
                        color = theme.textDim
                    )
                }
            }
        }
    }
}

@Composable
private fun BreakdownSlot(slot: PresentationSlot.Breakdown) {
    val theme = LocalArgosyTheme.current
    Column(
        modifier = Modifier.fillMaxSize().padding(Dimens.spacingXl),
        verticalArrangement = Arrangement.spacedBy(Dimens.spacingMd)
    ) {
        Text(
            text = slot.title,
            style = MaterialTheme.typography.headlineMedium,
            color = theme.textPrimary
        )
        slot.subtitle?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.titleSmall,
                color = theme.textDim
            )
        }
        slot.rows.forEach { row ->
            Column(verticalArrangement = Arrangement.spacedBy(Dimens.spacingXs)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = row.label,
                        style = MaterialTheme.typography.bodyLarge,
                        color = theme.textPrimary
                    )
                    Text(
                        text = row.value,
                        style = MaterialTheme.typography.bodyLarge,
                        color = theme.textDim
                    )
                }
                val barShape = RoundedCornerShape(ComponentDefaults.VolumeMeter.radius.dp)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(ComponentDefaults.VolumeMeter.height.dp)
                        .clip(barShape)
                        .background(theme.surfaceRaised)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(row.fraction.coerceIn(0f, 1f))
                            .fillMaxHeight()
                            .clip(barShape)
                            .background(row.color)
                    )
                }
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
