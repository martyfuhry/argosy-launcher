package com.nendo.argosy.ui.components

import android.graphics.Bitmap
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.nendo.argosy.ui.common.coverSizeWithin
import com.nendo.argosy.ui.common.rememberCoverAspectRatio
import com.nendo.argosy.ui.screens.home.GameDownloadIndicator
import com.nendo.argosy.ui.theme.Dimens
import com.nendo.argosy.ui.theme.LocalBoxArtStyle
import com.nendo.argosy.ui.theme.LocalMotionTier
import com.nendo.argosy.ui.theme.Motion
import com.nendo.argosy.ui.theme.MotionTier
import com.nendo.argosy.ui.util.clickableNoFocus
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.sign

/**
 * How many games either side of the focused one stay composed, drawn but invisible, so a step
 * brings in a cover that has already decoded and laid out.
 */
internal const val SPOTLIGHT_STAGED_REACH = 2

/**
 * The staged reach while covers are drawn as 3D boxes, which decode three faces each.
 */
internal const val SPOTLIGHT_BOX_ART_REACH = 1

internal const val SPOTLIGHT_SLIDE_WIDTH_FRACTION = 0.28f

/**
 * The share of one step's travel over which a game fades between fully shown and hidden. Less than
 * one step, so the outgoing game is gone before it has slid the whole way and the two covers never
 * read as a pair.
 */
internal const val SPOTLIGHT_FADE_REACH = 0.6f

internal const val SPOTLIGHT_MAX_COVER_WIDTH_FRACTION = 0.5f

/**
 * Where a game at [distance] steps from the focused one rests, in steps. Games inside [reach] keep
 * their own distance, so each one moves one step per press. A game further out rests one step
 * beyond the centre, which is where a game leaving after a long jump slides to.
 */
internal fun spotlightRestingPosition(distance: Int, reach: Int): Float = when {
    abs(distance) <= reach -> distance.toFloat()
    else -> distance.sign.toFloat()
}

internal fun spotlightAlphaAt(position: Float): Float =
    (1f - abs(position) / SPOTLIGHT_FADE_REACH).coerceIn(0f, 1f)

@Composable
fun SpotlightStage(
    items: List<CarouselItem>,
    focusedIndex: Int,
    onStep: (Int) -> Unit,
    onItemTap: (Int) -> Unit,
    modifier: Modifier = Modifier,
    showPlatformBadge: Boolean = true,
    useBoxArt: Boolean = false,
    showFocusVisuals: Boolean = true,
    downloadIndicatorFor: (CarouselItem) -> GameDownloadIndicator = { GameDownloadIndicator.NONE },
    onItemLongPress: ((Int) -> Unit)? = null,
    onCoverLoadFailed: ((Long, String) -> Unit)? = null,
    onCoverLoaded: ((Long, Bitmap) -> Unit)? = null,
    onPosterLoaded: ((String, Bitmap) -> Unit)? = null
) {
    val reach = if (useBoxArt) SPOTLIGHT_BOX_ART_REACH else SPOTLIGHT_STAGED_REACH
    val readingSign = if (LocalLayoutDirection.current == LayoutDirection.Rtl) -1f else 1f
    val reducedMotion = LocalMotionTier.current == MotionTier.Reduced

    val lastFocus = remember { mutableIntStateOf(focusedIndex) }
    val entryDirection = (focusedIndex - lastFocus.intValue).sign
    var leavingKey by remember { mutableStateOf<String?>(null) }
    val jumpedFromKey = items.getOrNull(lastFocus.intValue)?.key
        ?.takeIf { abs(focusedIndex - lastFocus.intValue) > reach }
    SideEffect {
        if (jumpedFromKey != null) leavingKey = jumpedFromKey
        lastFocus.intValue = focusedIndex
    }
    LaunchedEffect(leavingKey) {
        if (leavingKey == null) return@LaunchedEffect
        delay(Motion.durationSlide.toLong())
        leavingKey = null
    }

    val currentOnStep by rememberUpdatedState(onStep)
    val indicatorFor by rememberUpdatedState(downloadIndicatorFor)
    val swipeThresholdPx = with(LocalDensity.current) { Dimens.spacingXxl.toPx() }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(readingSign) {
                var dragged = 0f
                detectHorizontalDragGestures(
                    onDragStart = { dragged = 0f },
                    onDragEnd = {
                        when {
                            dragged * readingSign < -swipeThresholdPx -> currentOnStep(1)
                            dragged * readingSign > swipeThresholdPx -> currentOnStep(-1)
                        }
                    },
                    onHorizontalDrag = { change, amount ->
                        change.consume()
                        dragged += amount
                    }
                )
            }
    ) {
        val slideDistancePx = if (reducedMotion) {
            0f
        } else {
            with(LocalDensity.current) { (maxWidth * SPOTLIGHT_SLIDE_WIDTH_FRACTION).toPx() }
        }
        val geometry = rememberSpotlightGeometry(maxWidth = maxWidth, maxHeight = maxHeight)

        items.forEachIndexed { index, item ->
            val distance = index - focusedIndex
            val staged = abs(distance) <= reach || item.key == leavingKey || item.key == jumpedFromKey
            if (!staged) return@forEachIndexed
            key(item.key) {
                val isFocused = distance == 0
                val target = spotlightRestingPosition(distance, reach)
                val position = remember {
                    Animatable(if (isFocused && entryDirection != 0) entryDirection.toFloat() else target)
                }
                LaunchedEffect(target) {
                    position.animateTo(
                        targetValue = target,
                        animationSpec = tween(Motion.durationSlide, easing = Motion.argosyEase)
                    )
                }
                val indicator by remember(item) { derivedStateOf { indicatorFor(item) } }
                SpotlightSlot(
                    item = item,
                    isFocused = isFocused,
                    showFocusVisuals = showFocusVisuals,
                    geometry = geometry,
                    showPlatformBadge = showPlatformBadge,
                    useBoxArt = useBoxArt,
                    downloadIndicator = indicator,
                    onTap = if (isFocused) ({ onItemTap(index) }) else null,
                    onLongPress = if (isFocused) onItemLongPress?.let { { it(index) } } else null,
                    onCoverLoadFailed = onCoverLoadFailed,
                    onCoverLoaded = onCoverLoaded,
                    onPosterLoaded = onPosterLoaded,
                    modifier = Modifier
                        .zIndex(if (isFocused) 1f else 0f)
                        .graphicsLayer {
                            translationX = position.value * slideDistancePx * readingSign
                            alpha = spotlightAlphaAt(position.value)
                        }
                        .then(if (isFocused) Modifier else Modifier.clearAndSetSemantics { })
                )
            }
        }
    }
}

internal data class SpotlightGeometry(
    val titleReserve: Dp,
    val coverMaxWidth: Dp,
    val coverMaxHeight: Dp,
    val statsBesideCover: Boolean
)

@Composable
private fun rememberSpotlightGeometry(maxWidth: Dp, maxHeight: Dp): SpotlightGeometry {
    val typography = MaterialTheme.typography
    val density = LocalDensity.current
    val statsBesideCover = maxWidth >= maxHeight
    val lineGap = Dimens.spacingXs
    val coverGap = Dimens.spacingMd
    val minCoverHeight = Dimens.spacingXxl
    return remember(maxWidth, maxHeight, typography, density, lineGap, coverGap, minCoverHeight) {
        with(density) {
            val titleLine = typography.headlineMedium.lineHeight.toDp()
            val bodyLine = typography.bodyMedium.lineHeight.toDp()
            val statsLine = if (statsBesideCover) 0.dp else lineGap + bodyLine
            val titleReserve = titleLine * 2 + lineGap + bodyLine + statsLine
            SpotlightGeometry(
                titleReserve = titleReserve,
                coverMaxWidth = maxWidth * SPOTLIGHT_MAX_COVER_WIDTH_FRACTION,
                coverMaxHeight = (maxHeight - titleReserve - coverGap * 2 - NEW_BADGE_TOP_OVERFLOW)
                    .coerceAtLeast(minCoverHeight),
                statsBesideCover = statsBesideCover
            )
        }
    }
}

@Composable
private fun SpotlightSlot(
    item: CarouselItem,
    isFocused: Boolean,
    showFocusVisuals: Boolean,
    geometry: SpotlightGeometry,
    showPlatformBadge: Boolean,
    useBoxArt: Boolean,
    downloadIndicator: GameDownloadIndicator,
    onTap: (() -> Unit)?,
    onLongPress: (() -> Unit)?,
    onCoverLoadFailed: ((Long, String) -> Unit)?,
    onCoverLoaded: ((Long, Bitmap) -> Unit)?,
    onPosterLoaded: ((String, Bitmap) -> Unit)?,
    modifier: Modifier = Modifier
) {
    val tapModifier = when {
        onTap == null -> Modifier
        onLongPress != null -> Modifier.clickableNoFocus(onClick = onTap, onLongClick = onLongPress)
        else -> Modifier.clickableNoFocus(onClick = onTap)
    }
    Column(
        modifier = modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(geometry.titleReserve)
                .padding(horizontal = Dimens.spacingXl),
            contentAlignment = Alignment.BottomCenter
        ) {
            SpotlightDetails(item = item, statsInline = !geometry.statsBesideCover)
        }
        Spacer(modifier = Modifier.height(Dimens.spacingMd))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Bottom
        ) {
            Spacer(modifier = Modifier.weight(1f))
            SpotlightCover(
                item = item,
                isFocused = isFocused && showFocusVisuals,
                geometry = geometry,
                showPlatformBadge = showPlatformBadge,
                useBoxArt = useBoxArt,
                downloadIndicator = downloadIndicator,
                tapModifier = tapModifier,
                onTap = onTap,
                onCoverLoadFailed = onCoverLoadFailed,
                onCoverLoaded = onCoverLoaded,
                onPosterLoaded = onPosterLoaded
            )
            Box(
                modifier = Modifier.weight(1f),
                contentAlignment = Alignment.BottomStart
            ) {
                val game = (item as? CarouselItem.Game)?.game
                if (game != null && geometry.statsBesideCover) {
                    GameStatBadges(
                        rating = game.rating,
                        userRating = game.userRating,
                        userDifficulty = game.userDifficulty,
                        achievementCount = game.achievementCount,
                        earnedAchievementCount = game.earnedAchievementCount,
                        timeToBeatMainSec = game.timeToBeatMainSec,
                        stacked = true,
                        modifier = Modifier.padding(start = Dimens.spacingLg)
                    )
                }
            }
        }
    }
}

@Composable
private fun SpotlightDetails(item: CarouselItem, statsInline: Boolean) {
    val (title, subtitle) = when (item) {
        is CarouselItem.Game -> item.game.title to item.game.developer
        is CarouselItem.Media -> item.media.title to item.media.subtitle
        is CarouselItem.ViewAll -> return
    }
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        GameTitle(
            title = title,
            titleStyle = MaterialTheme.typography.headlineMedium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        )
        if (subtitle != null) {
            Spacer(modifier = Modifier.height(Dimens.spacingXs))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        val game = (item as? CarouselItem.Game)?.game
        if (statsInline && game != null) {
            GameStatBadges(
                rating = game.rating,
                userRating = game.userRating,
                userDifficulty = game.userDifficulty,
                achievementCount = game.achievementCount,
                earnedAchievementCount = game.earnedAchievementCount,
                timeToBeatMainSec = game.timeToBeatMainSec,
                modifier = Modifier.padding(top = Dimens.spacingXs)
            )
        }
    }
}

@Composable
private fun SpotlightCover(
    item: CarouselItem,
    isFocused: Boolean,
    geometry: SpotlightGeometry,
    showPlatformBadge: Boolean,
    useBoxArt: Boolean,
    downloadIndicator: GameDownloadIndicator,
    tapModifier: Modifier,
    onTap: (() -> Unit)?,
    onCoverLoadFailed: ((Long, String) -> Unit)?,
    onCoverLoaded: ((Long, Bitmap) -> Unit)?,
    onPosterLoaded: ((String, Bitmap) -> Unit)?
) {
    val boxArtStyle = LocalBoxArtStyle.current
    when (item) {
        is CarouselItem.Game -> {
            val ratio = if (boxArtStyle.nativeAspectRatio) {
                item.game.coverAspectRatio ?: rememberCoverAspectRatio(
                    item.coverPathOverride ?: item.game.coverPath,
                    boxArtStyle.aspectRatio
                )
            } else {
                boxArtStyle.aspectRatio
            }
            val size = coverSizeWithin(geometry.coverMaxWidth, geometry.coverMaxHeight, ratio)
            GameCardWithNewBadge(
                game = item.game,
                isFocused = isFocused,
                cardWidth = size.width,
                cardHeight = size.height,
                focusScale = 1f,
                downloadIndicator = downloadIndicator,
                showPlatformBadge = showPlatformBadge,
                useBoxArt = useBoxArt,
                coverPathOverride = item.coverPathOverride,
                onCoverLoadFailed = onCoverLoadFailed,
                onCoverLoaded = onCoverLoaded,
                modifier = tapModifier
            )
        }
        is CarouselItem.Media -> {
            val size = coverSizeWithin(
                geometry.coverMaxWidth,
                geometry.coverMaxHeight,
                mediaPosterAspectRatio
            )
            MediaCard(
                media = item.media,
                isFocused = isFocused,
                focusScale = 1f,
                downloadIndicator = downloadIndicator,
                onPosterLoaded = onPosterLoaded,
                modifier = tapModifier.size(size.width, size.height)
            )
        }
        is CarouselItem.ViewAll -> {
            val size: DpSize = coverSizeWithin(
                geometry.coverMaxWidth,
                geometry.coverMaxHeight,
                boxArtStyle.aspectRatio
            )
            ViewAllCard(
                isFocused = isFocused,
                onClick = onTap,
                remainingCount = item.remainingCount,
                modifier = Modifier.size(size.width, size.height)
            )
        }
    }
}
