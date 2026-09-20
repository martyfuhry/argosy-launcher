package com.nendo.argosy.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.nendo.argosy.R
import com.nendo.argosy.ui.coil.AppIconData
import com.nendo.argosy.ui.primitives.FocusIndicators
import com.nendo.argosy.ui.primitives.argosyFocusIndicators
import com.nendo.argosy.ui.theme.Dimens
import com.nendo.argosy.ui.theme.LocalArgosyTheme
import com.nendo.argosy.ui.util.touchOnly

private val COMPANION_APP_BAR_SLOT_WIDTH =
    com.nendo.argosy.ui.theme.generated.DimensionTokens.Layout.companionAppBarSlotWidth.dp
private const val APP_BAR_SCRIM_ALPHA = 0.8f
private const val FOCUS_PICKER_SCRIM_ALPHA = 0.7f

private val APP_BAR_FOCUS = FocusIndicators(ring = true, fill = true)

/**
 * Focus index meaning no slot is focused. The drawer slot owns -1, so a caller that has not placed
 * focus in the bar has to say so with a value the drawer will not match.
 */
const val APP_BAR_NOTHING_FOCUSED = -2

const val APP_BAR_DRAWER_INDEX = -1

data class CompanionMediaToggle(
    val showingMedia: Boolean,
    val isPlaying: Boolean
)

@Composable
fun CompanionAppBar(
    apps: List<String>,
    onAppClick: (String) -> Unit,
    modifier: Modifier = Modifier,
    focusedIndex: Int = -1,
    onAppLongPress: ((String) -> Unit)? = null,
    onOpenDrawer: () -> Unit = {},
    mediaToggle: CompanionMediaToggle? = null,
    onMediaToggle: () -> Unit = {},
    onKeyboardToggle: (() -> Unit)? = null,
    focusDisplays: List<DisplayFocusTarget> = emptyList(),
    focusPickerOpen: Boolean = false,
    focusPickerIndex: Int = 0,
    onFocusPickerToggle: (() -> Unit)? = null,
    onFocusDisplay: (Int) -> Unit = {}
) {
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()

    LaunchedEffect(focusedIndex) {
        if (focusedIndex >= 0) {
            listState.animateScrollToItem(focusedIndex)
        }
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color.Transparent,
                        MaterialTheme.colorScheme.scrim.copy(alpha = APP_BAR_SCRIM_ALPHA)
                    )
                )
            )
            .padding(vertical = Dimens.spacingSm + Dimens.spacingXs),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier
                .width(COMPANION_APP_BAR_SLOT_WIDTH)
                .touchOnly(onOpenDrawer)
                .padding(Dimens.spacingXs),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(Dimens.iconXl)
                    .argosyFocusIndicators(
                        focused = focusedIndex == -1,
                        indicators = APP_BAR_FOCUS,
                        shape = RoundedCornerShape(Dimens.radiusLg)
                    )
                    .clip(RoundedCornerShape(Dimens.radiusLg))
                    .background(Color.White.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = stringResource(
                        R.string.dual_companion_app_bar_add_description
                    ),
                    tint = Color.White.copy(alpha = 0.7f),
                    modifier = Modifier.size(Dimens.iconMd)
                )
            }
            Spacer(modifier = Modifier.height(Dimens.spacingXs))
        }

        LazyRow(
            state = listState,
            contentPadding = PaddingValues(end = Dimens.spacingLg),
            horizontalArrangement = Arrangement.spacedBy(Dimens.spacingMd),
            modifier = Modifier.weight(1f)
        ) {
            items(apps.size, key = { apps[it] }) { index ->
                CompanionAppItem(
                    packageName = apps[index],
                    isFocused = index == focusedIndex,
                    onClick = { onAppClick(apps[index]) },
                    onLongPress = onAppLongPress?.let { press -> { press(apps[index]) } }
                )
            }
        }

        if (mediaToggle != null) {
            CompanionMediaButton(
                toggle = mediaToggle,
                isFocused = focusedIndex == apps.size,
                onClick = onMediaToggle
            )
        }
        if (onKeyboardToggle != null) {
            val keyboardSlot = apps.size + if (mediaToggle != null) 1 else 0
            CompanionKeyboardButton(
                isFocused = focusedIndex == keyboardSlot,
                onClick = onKeyboardToggle
            )
        }
        if (onFocusPickerToggle != null && focusDisplays.size > 1) {
            val pickerSlot = apps.size + 1
            DisplayFocusButton(
                displays = focusDisplays,
                isOpen = focusPickerOpen,
                isFocused = focusedIndex == pickerSlot,
                selectedIndex = focusPickerIndex,
                onToggle = onFocusPickerToggle,
                onSelect = onFocusDisplay
            )
        }
    }
}

data class DisplayFocusTarget(val displayId: Int, val number: Int)

private object AboveAnchorPositionProvider : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize
    ): IntOffset = IntOffset(
        x = anchorBounds.left + (anchorBounds.width - popupContentSize.width) / 2,
        y = anchorBounds.top - popupContentSize.height
    )
}

@Composable
private fun DisplayFocusButton(
    displays: List<DisplayFocusTarget>,
    isOpen: Boolean,
    isFocused: Boolean,
    selectedIndex: Int,
    onToggle: () -> Unit,
    onSelect: (Int) -> Unit
) {
    Box(contentAlignment = Alignment.TopCenter) {
        if (isOpen) {
            Popup(
                popupPositionProvider = AboveAnchorPositionProvider,
                properties = PopupProperties(focusable = false)
            ) {
                Column(
                    modifier = Modifier
                        .width(COMPANION_APP_BAR_SLOT_WIDTH)
                        .clip(
                            RoundedCornerShape(
                                topStart = Dimens.radiusLg,
                                topEnd = Dimens.radiusLg
                            )
                        )
                        .background(
                            MaterialTheme.colorScheme.scrim.copy(alpha = FOCUS_PICKER_SCRIM_ALPHA)
                        )
                        .padding(Dimens.spacingXs),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(Dimens.spacingXs)
                ) {
                    displays.forEachIndexed { index, target ->
                        val selected = index == selectedIndex
                        Box(
                            modifier = Modifier
                                .size(Dimens.iconXl)
                                .argosyFocusIndicators(
                                    focused = selected,
                                    indicators = APP_BAR_FOCUS,
                                    shape = RoundedCornerShape(Dimens.radiusLg)
                                )
                                .clip(RoundedCornerShape(Dimens.radiusLg))
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .touchOnly { onSelect(target.displayId) },
                            contentAlignment = Alignment.Center
                        ) {
                            androidx.compose.material3.Text(
                                text = target.number.toString(),
                                style = MaterialTheme.typography.titleLarge,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }
        }
        Column(
            modifier = Modifier
                .width(COMPANION_APP_BAR_SLOT_WIDTH)
                .then(
                    if (isOpen) {
                        Modifier
                            .clip(
                                RoundedCornerShape(
                                    bottomStart = Dimens.radiusLg,
                                    bottomEnd = Dimens.radiusLg
                                )
                            )
                            .background(
                                MaterialTheme.colorScheme.scrim
                                    .copy(alpha = FOCUS_PICKER_SCRIM_ALPHA)
                            )
                    } else {
                        Modifier
                    }
                )
                .touchOnly(onToggle)
                .padding(Dimens.spacingXs),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(Dimens.iconXl)
                    .argosyFocusIndicators(
                        focused = isFocused,
                        indicators = APP_BAR_FOCUS,
                        shape = RoundedCornerShape(Dimens.radiusLg)
                    )
                    .clip(RoundedCornerShape(Dimens.radiusLg))
                    .background(Color.White.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Tv,
                    contentDescription = stringResource(R.string.dual_companion_app_bar_focus_description),
                    tint = Color.White.copy(alpha = 0.7f),
                    modifier = Modifier.size(Dimens.iconMd)
                )
            }
        }
    }
}

@Composable
private fun CompanionKeyboardButton(
    isFocused: Boolean,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .width(COMPANION_APP_BAR_SLOT_WIDTH)
            .touchOnly(onClick)
            .padding(Dimens.spacingXs),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(Dimens.iconXl)
                .argosyFocusIndicators(
                    focused = isFocused,
                    indicators = APP_BAR_FOCUS,
                    shape = RoundedCornerShape(Dimens.radiusLg)
                )
                .clip(RoundedCornerShape(Dimens.radiusLg))
                .background(Color.White.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Keyboard,
                contentDescription = stringResource(R.string.dual_companion_app_bar_keyboard_description),
                tint = Color.White.copy(alpha = 0.7f),
                modifier = Modifier.size(Dimens.iconMd)
            )
        }
        Spacer(modifier = Modifier.height(Dimens.spacingXs))
    }
}

@Composable
private fun CompanionMediaButton(
    toggle: CompanionMediaToggle,
    isFocused: Boolean,
    onClick: () -> Unit
) {
    val theme = LocalArgosyTheme.current
    Column(
        modifier = Modifier
            .width(COMPANION_APP_BAR_SLOT_WIDTH)
            .touchOnly(onClick)
            .padding(Dimens.spacingXs),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(Dimens.iconXl)
                .argosyFocusIndicators(
                    focused = isFocused,
                    indicators = APP_BAR_FOCUS,
                    shape = RoundedCornerShape(Dimens.radiusLg)
                )
                .clip(RoundedCornerShape(Dimens.radiusLg))
                .background(Color.White.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (toggle.showingMedia) Icons.Default.Home else Icons.Default.Movie,
                contentDescription = if (toggle.showingMedia) {
                    stringResource(R.string.dual_companion_app_bar_media_to_library_description)
                } else {
                    stringResource(R.string.dual_companion_app_bar_media_to_player_description)
                },
                tint = if (toggle.isPlaying) theme.focusAccent else Color.White.copy(alpha = 0.7f),
                modifier = Modifier.size(Dimens.iconMd)
            )
        }
        Spacer(modifier = Modifier.height(Dimens.spacingXs))
    }
}

@Composable
internal fun CompanionAppItem(
    packageName: String,
    isFocused: Boolean = false,
    onClick: () -> Unit,
    onLongPress: (() -> Unit)? = null
) {
    Column(
        modifier = Modifier
            .width(COMPANION_APP_BAR_SLOT_WIDTH)
            .let { base ->
                if (onLongPress == null) base.touchOnly(onClick)
                else base.touchOnly(onClick = onClick, onLongPress = onLongPress)
            }
            .padding(Dimens.spacingXs),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        AsyncImage(
            model = AppIconData(packageName),
            contentDescription = null,
            modifier = Modifier
                .size(Dimens.iconXl)
                .argosyFocusIndicators(
                    focused = isFocused,
                    indicators = APP_BAR_FOCUS,
                    shape = RoundedCornerShape(Dimens.radiusLg)
                )
                .clip(RoundedCornerShape(Dimens.radiusLg)),
            contentScale = ContentScale.Crop
        )

        Spacer(modifier = Modifier.height(Dimens.spacingXs))
    }
}
