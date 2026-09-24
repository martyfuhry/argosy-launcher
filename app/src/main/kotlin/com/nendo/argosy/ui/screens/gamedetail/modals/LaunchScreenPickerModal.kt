package com.nendo.argosy.ui.screens.gamedetail.modals

import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Tv
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.nendo.argosy.R
import com.nendo.argosy.ui.components.CenteredModal
import com.nendo.argosy.ui.components.FocusedScroll
import com.nendo.argosy.ui.components.InputButton
import com.nendo.argosy.ui.screens.gamedetail.components.OptionItem
import com.nendo.argosy.ui.screens.gamedetail.delegates.LaunchScreenOption
import com.nendo.argosy.ui.theme.Dimens

@Composable
fun LaunchScreenPickerModal(
    screens: List<LaunchScreenOption>,
    focusIndex: Int,
    rememberedDisplayId: Int?,
    onSelect: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    CenteredModal(
        title = stringResource(R.string.gamedetail_launch_screen_picker_title),
        baseWidth = Dimens.modalWidth,
        onDismiss = onDismiss,
        footerHints = listOf(
            InputButton.DPAD_VERTICAL to
                stringResource(R.string.gamedetail_launch_screen_picker_footer_navigate),
            InputButton.A to stringResource(R.string.gamedetail_launch_screen_picker_footer_launch),
            InputButton.B to stringResource(R.string.gamedetail_launch_screen_picker_footer_cancel)
        )
    ) {
        val listState = rememberLazyListState()
        FocusedScroll(listState = listState, focusedIndex = focusIndex)

        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f, fill = false)
        ) {
            itemsIndexed(screens, key = { _, screen -> screen.displayId }) { index, screen ->
                OptionItem(
                    icon = Icons.Default.Tv,
                    label = stringResource(
                        R.string.gamedetail_launch_screen_picker_option,
                        screen.number
                    ),
                    isFocused = focusIndex == index,
                    isSelected = screen.displayId == rememberedDisplayId,
                    onClick = { onSelect(screen.displayId) }
                )
            }
        }
    }
}
