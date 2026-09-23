package com.nendo.argosy.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Whatshot
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import com.nendo.argosy.ui.theme.ALauncherColors
import com.nendo.argosy.ui.theme.Dimens
import com.nendo.argosy.util.formatTimeToBeat

@Composable
fun GameStatBadges(
    rating: Float?,
    userRating: Int,
    userDifficulty: Int,
    achievementCount: Int,
    earnedAchievementCount: Int,
    timeToBeatMainSec: Int?,
    modifier: Modifier = Modifier,
    textColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    tintOverride: Color? = null,
    stacked: Boolean = false
) {
    val timeToBeat = formatTimeToBeat(LocalContext.current, timeToBeatMainSec)
    val badges = buildList {
        if (rating != null) {
            add(StatBadge(Icons.Default.Public, MaterialTheme.colorScheme.primary, "${rating.toInt()}%"))
        }
        if (userRating > 0) {
            add(StatBadge(Icons.Default.Star, ALauncherColors.StarGold, "$userRating/10"))
        }
        if (userDifficulty > 0) {
            add(StatBadge(Icons.Default.Whatshot, ALauncherColors.DifficultyRed, "$userDifficulty/10"))
        }
        if (achievementCount > 0) {
            add(
                StatBadge(
                    Icons.Filled.EmojiEvents,
                    ALauncherColors.TrophyAmber,
                    "$earnedAchievementCount/$achievementCount"
                )
            )
        }
        if (timeToBeat != null) {
            add(StatBadge(Icons.Default.Schedule, textColor, timeToBeat))
        }
    }
    if (badges.isEmpty()) return

    val content: @Composable () -> Unit = {
        badges.forEach { badge ->
            StatBadgeItem(badge = badge, tint = tintOverride ?: badge.tint, textColor = textColor)
        }
    }
    if (stacked) {
        Column(
            modifier = modifier,
            verticalArrangement = Arrangement.spacedBy(Dimens.spacingSm)
        ) { content() }
    } else {
        Row(
            modifier = modifier,
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Dimens.radiusLg)
        ) { content() }
    }
}

private data class StatBadge(val icon: ImageVector, val tint: Color, val label: String)

@Composable
private fun StatBadgeItem(badge: StatBadge, tint: Color, textColor: Color) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Dimens.spacingXs)
    ) {
        Icon(
            imageVector = badge.icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(Dimens.iconXs)
        )
        Text(
            text = badge.label,
            style = MaterialTheme.typography.labelMedium,
            color = textColor
        )
    }
}
