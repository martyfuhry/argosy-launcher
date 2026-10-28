package com.nendo.argosy.ui.dualscreen

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.nendo.argosy.R
import com.nendo.argosy.domain.model.PresentationStat
import com.nendo.argosy.domain.model.PresentationStyle
import com.nendo.argosy.ui.coil.AppIconData
import com.nendo.argosy.ui.common.rememberFileImageModel
import com.nendo.argosy.ui.theme.LocalArgosyTheme
import com.nendo.argosy.ui.theme.generated.ComponentDefaults
import com.nendo.argosy.util.formatRelativeTime
import java.time.Instant

@Composable
internal fun ShowcaseAppWash(accent: Pair<Color, Color>?, crossfadeMs: Int) {
    val base = LocalArgosyTheme.current.surfaceBase
    val washAlpha = ComponentDefaults.GameShowcase.appWashAlpha
    val spec = tween<Color>(durationMillis = crossfadeMs)
    val top by animateColorAsState(accent?.let { lerp(base, it.first, washAlpha) } ?: base, spec, label = "appWashTop")
    val bottom by animateColorAsState(
        accent?.let { lerp(base, it.second, washAlpha) } ?: base,
        spec,
        label = "appWashBottom"
    )
    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(top, bottom))))
}

@Composable
internal fun ShowcaseAppIcon(
    detail: CompanionDetail,
    modifier: Modifier = Modifier,
    fallback: @Composable () -> Unit = {}
) {
    val packageName = detail.appPackageName ?: return
    val liveModel = remember(packageName) { AppIconData(packageName) }
    val fileModel = rememberFileImageModel(detail.artUrl)
    var liveFailed by remember(packageName) { mutableStateOf(false) }
    var fileFailed by remember(detail.artUrl) { mutableStateOf(false) }
    val model = when {
        !liveFailed -> liveModel
        !fileFailed -> fileModel
        else -> null
    }
    val iconSize = ComponentDefaults.GameShowcase.appIconDp.dp
    val halo = detail.accentColors?.first?.copy(alpha = ComponentDefaults.GameShowcase.appHaloAlpha)
    Box(
        modifier = modifier.drawBehind {
            if (halo != null) {
                val radius = iconSize.toPx()
                drawCircle(
                    brush = Brush.radialGradient(listOf(halo, Color.Transparent), center = center, radius = radius),
                    radius = radius,
                    center = center
                )
            }
        },
        contentAlignment = Alignment.Center
    ) {
        if (model == null) {
            fallback()
        } else {
            AsyncImage(
                model = model,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                onError = { if (model === liveModel) liveFailed = true else fileFailed = true },
                modifier = Modifier.size(iconSize)
            )
        }
    }
}

@Composable
internal fun ShowcaseAppPackageLine(detail: CompanionDetail, style: PresentationStyle) {
    val packageName = detail.appPackageName?.takeIf { style.shows(PresentationStat.DEVELOPER) } ?: return
    Text(
        text = packageName,
        style = MaterialTheme.typography.labelMedium,
        color = LocalArgosyTheme.current.textDim,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
    )
}

@Composable
internal fun showcaseAppLastPlayed(detail: CompanionDetail, style: PresentationStyle): String? {
    if (detail.appPackageName == null || !style.shows(PresentationStat.PLAY_TIME)) return null
    val millis = detail.stats?.lastPlayedAt ?: return null
    val context = LocalContext.current
    val relative = remember(millis) { formatRelativeTime(context, Instant.ofEpochMilli(millis)) }
    return stringResource(R.string.dual_showcase_last_played, relative)
}
