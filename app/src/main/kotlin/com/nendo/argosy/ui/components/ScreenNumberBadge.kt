package com.nendo.argosy.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import com.nendo.argosy.ui.theme.Dimens
import com.nendo.argosy.ui.theme.LocalArgosyTheme

@Composable
fun ScreenNumberBadge(number: Int, modifier: Modifier = Modifier) {
    val theme = LocalArgosyTheme.current
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(Dimens.radiusPanel))
            .background(theme.focusAccent.copy(alpha = 0.18f))
            .border(
                width = Dimens.borderMedium,
                color = theme.focusAccent,
                shape = RoundedCornerShape(Dimens.radiusPanel)
            )
            .padding(horizontal = Dimens.spacingXl, vertical = Dimens.spacingLg),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = number.toString(),
            style = MaterialTheme.typography.displayLarge,
            color = theme.textPrimary
        )
    }
}
