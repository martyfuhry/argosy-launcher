package com.nendo.argosy.ui.dualscreen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.nendo.argosy.R
import com.nendo.argosy.core.game.AchievementUi
import com.nendo.argosy.hardware.CompanionInGameState
import com.nendo.argosy.hardware.CompanionSessionTimer
import com.nendo.argosy.ui.theme.ALauncherColors
import com.nendo.argosy.ui.theme.Dimens
import com.nendo.argosy.ui.theme.LocalArgosyTheme
import com.nendo.argosy.ui.theme.LocalLauncherTheme
import com.nendo.argosy.ui.util.touchOnly
import kotlinx.coroutines.delay

private val COMPANION_ART_HEIGHT =
    com.nendo.argosy.ui.theme.generated.DimensionTokens.Layout.companionArtHeight.dp
private val COMPANION_PROGRESS_HEIGHT =
    com.nendo.argosy.ui.theme.generated.DimensionTokens.Layout.companionProgressHeight.dp

@Composable
fun CompanionDashboard(
    state: CompanionInGameState,
    sessionTimer: CompanionSessionTimer?,
    liveAchievements: List<AchievementUi>,
    onQuickSave: () -> Unit = {},
    onQuickLoad: () -> Unit = {},
    onScreenshot: () -> Unit = {}
) {
    if (!state.isLoaded) return

    val achievementTotal = if (liveAchievements.isNotEmpty()) liveAchievements.size else state.achievementCount
    val achievementEarned = if (liveAchievements.isNotEmpty()) {
        liveAchievements.count { it.isUnlocked }
    } else {
        state.earnedAchievementCount
    }

    var sessionMillis by remember { mutableLongStateOf(sessionTimer?.getActiveMillis() ?: 0L) }

    LaunchedEffect(sessionTimer) {
        if (sessionTimer == null) return@LaunchedEffect
        while (true) {
            delay(1000)
            sessionMillis = sessionTimer.getActiveMillis()
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = Dimens.spacingMd)
    ) {
        item { HeroGameCard(state) }
        item { SessionTimerCard(sessionMillis) }
        if (state.quickActionsAvailable && !state.isHardcore) {
            item {
                QuickActionsRow(
                    hasQuickSave = state.hasQuickSave,
                    onQuickSave = onQuickSave,
                    onQuickLoad = onQuickLoad,
                    onScreenshot = onScreenshot
                )
            }
        }
        if (achievementTotal > 0) {
            item { AchievementProgress(earned = achievementEarned, total = achievementTotal) }
        }
        item { PlayStatsCard(state, sessionMillis) }
    }
}

@Composable
private fun QuickActionsRow(
    hasQuickSave: Boolean,
    onQuickSave: () -> Unit,
    onQuickLoad: () -> Unit,
    onScreenshot: () -> Unit
) {
    val theme = LocalArgosyTheme.current
    var loadArmedUntil by remember { mutableLongStateOf(0L) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Dimens.spacingLg, vertical = Dimens.spacingSm)
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(Dimens.spacingSm)) {
            QuickActionButton(
                label = stringResource(R.string.dual_companion_quick_action_save_state),
                modifier = Modifier.weight(1f),
                onTap = onQuickSave
            )
            QuickActionButton(
                label = stringResource(R.string.dual_companion_quick_action_screenshot),
                modifier = Modifier.weight(1f),
                onTap = onScreenshot
            )
            val loadArmed = android.os.SystemClock.elapsedRealtime() < loadArmedUntil
            QuickActionButton(
                label = if (loadArmed) {
                    stringResource(R.string.dual_companion_quick_action_load_confirm)
                } else {
                    stringResource(R.string.dual_companion_quick_action_load_state)
                },
                accent = loadArmed,
                enabled = hasQuickSave,
                modifier = Modifier.weight(1f),
                onTap = {
                    if (android.os.SystemClock.elapsedRealtime() < loadArmedUntil) {
                        loadArmedUntil = 0L
                        onQuickLoad()
                    } else {
                        loadArmedUntil = android.os.SystemClock.elapsedRealtime() + 3000L
                    }
                }
            )
        }
    }
}

@Composable
private fun QuickActionButton(
    label: String,
    modifier: Modifier = Modifier,
    accent: Boolean = false,
    enabled: Boolean = true,
    onTap: () -> Unit
) {
    val theme = LocalArgosyTheme.current
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(Dimens.radiusControl))
            .background(
                when {
                    !enabled -> Color.White.copy(alpha = 0.03f)
                    accent -> theme.focusAccent.copy(alpha = 0.25f)
                    else -> Color.White.copy(alpha = 0.08f)
                }
            )
            .then(if (enabled) Modifier.touchOnly(onTap) else Modifier)
            .padding(vertical = Dimens.spacingMd),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = when {
                !enabled -> Color.White.copy(alpha = 0.3f)
                accent -> theme.focusAccent
                else -> Color.White
            }
        )
    }
}


@Composable
private fun HeroGameCard(state: CompanionInGameState) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(COMPANION_ART_HEIGHT)
    ) {
        if (state.coverPath != null) {
            AsyncImage(
                model = state.coverPath,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Transparent,
                            Color.Black.copy(alpha = 0.85f)
                        ),
                        startY = 40f
                    )
                )
        )

        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .padding(horizontal = Dimens.spacingLg, vertical = Dimens.spacingMd)
        ) {
            Text(
                text = state.title,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(Dimens.spacingXs))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = state.platformName,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.7f)
                    )
                    if (state.developer != null) {
                        MetadataDot()
                        Text(
                            text = state.developer,
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.7f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    if (state.channelName != null) {
                        MetadataDot()
                        Text(
                            text = state.channelName,
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.7f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    if (state.releaseYear != null) {
                        MetadataDot()
                        Text(
                            text = state.releaseYear.toString(),
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.7f)
                        )
                    }
                }

                SaveStateIndicator(isDirty = state.isDirty)
            }
        }
    }
}

@Composable
private fun MetadataDot() {
    Text(
        text = "  \u00B7  ",
        style = MaterialTheme.typography.bodySmall,
        color = Color.White.copy(alpha = 0.4f)
    )
}

@Composable
private fun SessionTimerCard(activeMillis: Long) {
    if (activeMillis <= 0L) return

    val totalSeconds = activeMillis / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    val formatted = "%d:%02d:%02d".format(hours, minutes, seconds)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Dimens.spacingLg, vertical = Dimens.spacingMd),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = stringResource(R.string.dual_companion_session_label),
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White.copy(alpha = 0.6f)
        )
        Spacer(modifier = Modifier.width(Dimens.spacingSm + Dimens.spacingXs))
        Text(
            text = formatted,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )
    }
}

@Composable
private fun AchievementProgress(earned: Int, total: Int) {
    val progress = if (total > 0) earned.toFloat() / total else 0f

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Dimens.spacingLg)
            .padding(bottom = Dimens.spacingMd)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.dual_companion_achievements_label),
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.6f)
            )
            Text(
                text = "$earned / $total",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = ALauncherColors.TrophyAmber
            )
        }
        Spacer(modifier = Modifier.height(Dimens.spacingSm))
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier
                .fillMaxWidth()
                .height(COMPANION_PROGRESS_HEIGHT)
                .clip(RoundedCornerShape(Dimens.radiusPill)),
            color = ALauncherColors.TrophyAmber,
            trackColor = Color.White.copy(alpha = 0.12f),
            strokeCap = StrokeCap.Round
        )
    }
}

@Composable
private fun PlayStatsCard(state: CompanionInGameState, sessionMillis: Long) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Dimens.spacingLg)
            .padding(top = Dimens.spacingSm)
    ) {
        val sessionMinutes = (sessionMillis / 60_000).toInt()
        val totalMinutes = state.playTimeMinutes + sessionMinutes
        val hours = totalMinutes / 60
        val mins = totalMinutes % 60
        val timeText = when {
            hours > 0 && mins > 0 -> stringResource(
                R.string.dual_companion_play_time_hours_minutes, hours, mins
            )
            hours > 0 -> stringResource(R.string.dual_companion_play_time_hours, hours)
            mins > 0 -> stringResource(R.string.dual_companion_play_time_minutes, mins)
            else -> stringResource(R.string.dual_companion_play_time_zero)
        }

        StatRow(
            label = stringResource(R.string.dual_companion_stat_play_time),
            value = timeText
        )
        Spacer(modifier = Modifier.height(Dimens.spacingSm))
        StatRow(
            label = stringResource(R.string.dual_companion_stat_play_count),
            value = state.playCount.toString()
        )
    }
}

@Composable
private fun StatRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White.copy(alpha = 0.6f)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )
    }
}

@Composable
private fun SaveStateIndicator(
    isDirty: Boolean,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Dimens.spacingXs)
    ) {
        Text(
            text = if (isDirty) {
                stringResource(R.string.dual_companion_saves_dirty)
            } else {
                stringResource(R.string.dual_companion_saves_synced)
            },
            style = MaterialTheme.typography.labelSmall,
            color = Color.White.copy(alpha = 0.7f)
        )
        SaveStateDot(isDirty)
    }
}

@Composable
private fun SaveStateDot(isDirty: Boolean) {
    Box(
        modifier = Modifier
            .size(Dimens.iconLg)
            .clip(CircleShape)
            .background(
                with(LocalLauncherTheme.current.semanticColors) { if (isDirty) warning else success }
            ),
        contentAlignment = Alignment.Center
    ) {
        if (isDirty) {
            Box(
                modifier = Modifier
                    .size(Dimens.spacingSm)
                    .clip(CircleShape)
                    .background(Color.White)
            )
        } else {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = stringResource(
                    R.string.dual_companion_saves_synced_icon_description
                ),
                tint = Color.White,
                modifier = Modifier.size(Dimens.iconSm)
            )
        }
    }
}

