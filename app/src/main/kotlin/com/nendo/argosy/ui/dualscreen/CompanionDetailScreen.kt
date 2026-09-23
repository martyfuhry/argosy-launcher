package com.nendo.argosy.ui.dualscreen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import coil.compose.AsyncImage
import com.nendo.argosy.R
import com.nendo.argosy.domain.model.PresentationArt
import com.nendo.argosy.domain.model.PresentationScrim
import com.nendo.argosy.domain.model.PresentationStat
import com.nendo.argosy.domain.model.PresentationStyle
import com.nendo.argosy.ui.components.Box3dCover
import com.nendo.argosy.ui.components.GameTitle
import com.nendo.argosy.util.formatPlayTime
import com.nendo.argosy.util.formatTimeToBeat
import com.nendo.argosy.ui.theme.AspectRatioClass
import com.nendo.argosy.ui.theme.Dimens
import com.nendo.argosy.ui.theme.LocalArgosyTheme
import com.nendo.argosy.ui.theme.LocalUiScale

/**
 * The showcase screen while the driven screen is on Library or Media.
 *
 * It describes what the other screen has focused and takes no input of its own, which is the whole
 * point of the role: one screen is being driven and this one is answering "what is that".
 */
/**
 * @param style applies only to a game's detail; every other destination draws with the defaults.
 */
@Composable
fun CompanionDetailScreen(
    detail: CompanionDetail,
    modifier: Modifier = Modifier,
    style: PresentationStyle = PresentationStyle(),
    footerHints: @Composable (() -> Unit)? = null
) {
    val theme = LocalArgosyTheme.current
    val isWideDisplay = LocalUiScale.current.aspectRatioClass.let {
        it == AspectRatioClass.WIDE || it == AspectRatioClass.ULTRA_WIDE
    }
    val gutter = if (isWideDisplay) Dimens.spacingXxl else Dimens.spacingLg
    val effective = if (detail.isGameTitle) style else PresentationStyle()
    val facts = detail.stats?.let { presentationFacts(it, effective) } ?: detail.facts

    Box(modifier = modifier.fillMaxSize().background(theme.surfaceBase)) {
        detail.backdropUrl?.let { backdrop ->
            AsyncImage(
                model = com.nendo.argosy.ui.common.rememberFileImageModel(backdrop),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .then(
                        if (effective.scrim == PresentationScrim.BLUR) {
                            Modifier.blur(com.nendo.argosy.ui.theme.Motion.blurRadiusDrawer)
                        } else {
                            Modifier
                        }
                    )
            )
            PresentationScrimLayer(style = effective, color = theme.surfaceBase)
        }

        if (effective.art == PresentationArt.TITLE) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(gutter),
                verticalArrangement = Arrangement.spacedBy(Dimens.spacingSm, Alignment.CenterVertically),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                DetailText(
                    detail = detail,
                    facts = facts,
                    titleStyle = MaterialTheme.typography.displayMedium,
                    centered = true,
                    overviewLines = 0
                )
            }
        } else {
            Row(
                modifier = Modifier.fillMaxSize().padding(gutter),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(gutter)
            ) {
                detail.artUrl?.let { art ->
                    DetailArt(
                        artUrl = art,
                        spineUrl = detail.spineUrl.takeIf { effective.art == PresentationArt.BOX_3D },
                        modifier = Modifier
                            .weight(ART_WEIGHT)
                            .fillMaxHeight(if (isWideDisplay) WIDE_ART_HEIGHT else NARROW_ART_HEIGHT)
                    )
                }

                Column(
                    modifier = Modifier.weight(TEXT_WEIGHT),
                    verticalArrangement = Arrangement.spacedBy(Dimens.spacingSm)
                ) {
                    DetailText(
                        detail = detail,
                        facts = facts,
                        titleStyle = MaterialTheme.typography.displaySmall,
                        centered = false,
                        overviewLines = if (isWideDisplay) OVERVIEW_LINES_WIDE else OVERVIEW_LINES_NARROW
                    )
                }
            }
        }

        footerHints?.let { hints ->
            Box(modifier = Modifier.align(Alignment.BottomCenter)) { hints() }
        }
    }
}

@Composable
private fun PresentationScrimLayer(style: PresentationStyle, color: Color) {
    val strength = style.scrimStrength / PERCENT
    val brush = when (style.scrim) {
        PresentationScrim.NONE -> return
        PresentationScrim.GRADIENT -> Brush.verticalGradient(
            listOf(color.copy(alpha = strength * GRADIENT_TOP_SHARE), color.copy(alpha = strength))
        )
        PresentationScrim.SOLID, PresentationScrim.BLUR -> SolidColor(color.copy(alpha = strength))
    }
    Box(modifier = Modifier.fillMaxSize().background(brush))
}

@Composable
private fun DetailArt(artUrl: String, spineUrl: String?, modifier: Modifier = Modifier) {
    val localSpine = spineUrl?.takeIf { artUrl.startsWith("/") && it.startsWith("/") }
    if (localSpine != null) {
        Box(modifier = modifier, contentAlignment = Alignment.Center) {
            Box3dCover(
                frontPath = artUrl,
                spinePath = localSpine,
                isInteractive = false,
                modifier = Modifier.fillMaxHeight()
            )
        }
        return
    }
    AsyncImage(
        model = com.nendo.argosy.ui.common.rememberFileImageModel(artUrl),
        contentDescription = null,
        contentScale = ContentScale.Fit,
        alignment = Alignment.Center,
        modifier = modifier.clip(RoundedCornerShape(Dimens.radiusSm))
    )
}

@Composable
private fun DetailText(
    detail: CompanionDetail,
    facts: List<CompanionFact>,
    titleStyle: TextStyle,
    centered: Boolean,
    overviewLines: Int
) {
    val theme = LocalArgosyTheme.current
    val textAlign = if (centered) TextAlign.Center else TextAlign.Start
    val horizontal = if (centered) Alignment.CenterHorizontally else Alignment.Start
    detail.subtitle?.let { subtitle ->
        Text(
            text = subtitle,
            style = MaterialTheme.typography.labelLarge,
            color = theme.focusAccent,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = textAlign
        )
    }
    if (detail.isGameTitle) {
        GameTitle(
            title = detail.title,
            titleStyle = titleStyle,
            titleColor = theme.textPrimary,
            maxLines = 2,
            textAlign = textAlign,
            horizontalAlignment = horizontal
        )
    } else {
        Text(
            text = detail.title,
            style = titleStyle,
            color = theme.textPrimary,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = textAlign
        )
    }
    if (facts.isNotEmpty()) {
        Column(verticalArrangement = Arrangement.spacedBy(Dimens.spacingSm)) {
            facts.chunked(FACTS_PER_ROW).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(Dimens.spacingMd)) {
                    row.forEach { fact ->
                        Column(
                            modifier = Modifier.weight(1f),
                            horizontalAlignment = horizontal
                        ) {
                            Text(
                                text = fact.label,
                                style = MaterialTheme.typography.labelSmall,
                                color = theme.textMute,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = fact.value,
                                style = MaterialTheme.typography.titleMedium,
                                color = theme.textPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    repeat(FACTS_PER_ROW - row.size) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
    if (overviewLines > 0) {
        detail.overview?.let { overview ->
            Text(
                text = overview,
                style = MaterialTheme.typography.bodyMedium,
                color = theme.textDim,
                maxLines = overviewLines,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

/**
 * The facts [style] asks for, in [PresentationStat] order, each present only when the game has a
 * value for it.
 */
@Composable
internal fun presentationFacts(stats: CompanionGameStats, style: PresentationStyle): List<CompanionFact> {
    val context = LocalContext.current
    val labels = PresentationStat.entries.associateWith { presentationFactLabel(it) }
    return PresentationStat.entries.filter { style.shows(it) }.mapNotNull { stat ->
        val value = when (stat) {
            PresentationStat.DEVELOPER -> stats.developer
            PresentationStat.RELEASE_YEAR -> stats.releaseYear?.toString()
            PresentationStat.GENRE -> stats.genre
            PresentationStat.COMMUNITY_RATING -> stats.communityRating?.let { "${it.toInt()}%" }
            PresentationStat.USER_RATING -> stats.userRating.takeIf { it > 0 }?.let { "$it/10" }
            PresentationStat.PLAY_TIME -> stats.playTimeMinutes.takeIf { it > 0 }
                ?.let { formatPlayTime(context, it) }
            PresentationStat.TIME_TO_BEAT -> formatTimeToBeat(context, stats.timeToBeatMainSec)
            PresentationStat.ACHIEVEMENTS -> stats.achievementCount.takeIf { it > 0 }
                ?.let { "${stats.earnedAchievementCount}/$it" }
            PresentationStat.FRIENDS -> null
        }
        value?.let { CompanionFact(labels.getValue(stat), it) }
    }
}

@Composable
private fun presentationFactLabel(stat: PresentationStat): String = when (stat) {
    PresentationStat.DEVELOPER -> stringResource(R.string.dual_presentation_fact_developer)
    PresentationStat.RELEASE_YEAR -> stringResource(R.string.dual_presentation_fact_released)
    PresentationStat.GENRE -> stringResource(R.string.dual_presentation_fact_genre)
    PresentationStat.COMMUNITY_RATING -> stringResource(R.string.dual_presentation_fact_rating)
    PresentationStat.USER_RATING -> stringResource(R.string.dual_presentation_fact_user_rating)
    PresentationStat.PLAY_TIME -> stringResource(R.string.dual_presentation_fact_play_time)
    PresentationStat.TIME_TO_BEAT -> stringResource(R.string.dual_presentation_fact_time_to_beat)
    PresentationStat.ACHIEVEMENTS -> stringResource(R.string.dual_presentation_fact_achievements)
    PresentationStat.FRIENDS -> ""
}

/**
 * The art and the text share the row by weight rather than the art sizing itself.
 *
 * An image asked only for a height reports whatever width its bitmap wants, which on a backdrop is
 * wider than the screen, so the text beside it is measured at nothing and never appears.
 */
private const val ART_WEIGHT = 0.4f
private const val TEXT_WEIGHT = 1f
/**
 * What the buttons do on the screen being driven, spelled out on the screen describing it.
 *
 * The showcase takes no input of its own, so these name the other screen's actions; a viewer
 * looking at the description still needs to be told what pressing A over there will do.
 */
@Composable
fun companionDetailHints(
    detail: CompanionDetail,
    viewMode: String = ""
): List<Pair<com.nendo.argosy.ui.components.InputButton, String>> = when {
    detail.isGameTitle -> listOf(
        com.nendo.argosy.ui.components.InputButton.LB_RB to
            stringResource(R.string.dual_detail_hint_game_section),
        com.nendo.argosy.ui.components.InputButton.A to
            stringResource(R.string.dual_detail_hint_game_open),
        com.nendo.argosy.ui.components.InputButton.B to
            stringResource(R.string.dual_detail_hint_game_back)
    )
    viewMode == "MEDIA_GRID" -> listOf(
        com.nendo.argosy.ui.components.InputButton.LB_RB to
            stringResource(R.string.dual_detail_hint_media_grid_library),
        com.nendo.argosy.ui.components.InputButton.Y to
            stringResource(R.string.dual_detail_hint_media_grid_resume),
        com.nendo.argosy.ui.components.InputButton.X to
            stringResource(R.string.dual_detail_hint_media_grid_options),
        com.nendo.argosy.ui.components.InputButton.A to
            stringResource(R.string.dual_detail_hint_media_grid_play),
        com.nendo.argosy.ui.components.InputButton.B to
            stringResource(R.string.dual_detail_hint_media_grid_back)
    )
    viewMode == "MEDIA_INFO" -> listOf(
        com.nendo.argosy.ui.components.InputButton.LB_RB to
            stringResource(R.string.dual_detail_hint_media_info_title),
        com.nendo.argosy.ui.components.InputButton.DPAD_HORIZONTAL to
            stringResource(R.string.dual_detail_hint_media_info_season),
        com.nendo.argosy.ui.components.InputButton.A to
            stringResource(R.string.dual_detail_hint_media_info_watch),
        com.nendo.argosy.ui.components.InputButton.B to
            stringResource(R.string.dual_detail_hint_media_info_back)
    )
    else -> listOf(
        com.nendo.argosy.ui.components.InputButton.LB_RB to
            stringResource(R.string.dual_detail_hint_default_section),
        com.nendo.argosy.ui.components.InputButton.Y to
            stringResource(R.string.dual_detail_hint_default_favorite),
        com.nendo.argosy.ui.components.InputButton.X to
            stringResource(R.string.dual_detail_hint_default_options),
        com.nendo.argosy.ui.components.InputButton.A to
            stringResource(R.string.dual_detail_hint_default_play),
        com.nendo.argosy.ui.components.InputButton.B to
            stringResource(R.string.dual_detail_hint_default_back)
    )
}

private const val GRADIENT_TOP_SHARE = 0.6f
private const val PERCENT = 100f
private const val FACTS_PER_ROW = 3
private const val WIDE_ART_HEIGHT = 0.72f
private const val NARROW_ART_HEIGHT = 0.55f
private const val OVERVIEW_LINES_WIDE = 5
private const val OVERVIEW_LINES_NARROW = 3
