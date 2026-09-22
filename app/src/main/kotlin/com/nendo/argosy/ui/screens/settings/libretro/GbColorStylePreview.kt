package com.nendo.argosy.ui.screens.settings.libretro

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import com.nendo.argosy.R
import com.nendo.argosy.core.emulator.GbColorStyles
import com.nendo.argosy.ui.components.preferenceContentColor
import com.nendo.argosy.ui.theme.Dimens

private const val SCENE_WIDTH = 32
private const val SCENE_HEIGHT = 24
private const val FRAME_CELLS = 2

private val SCENE = listOf(
    "00000000000000000000000000000000",
    "00000000000000000000000000000000",
    "00000000000000000000000000000000",
    "00000000000000000000000001111000",
    "00000000000000000000000011111100",
    "00000000000000000000000011111100",
    "00000000000000000000000011111100",
    "00000000000000000000000011111100",
    "00000000000000000000000001111000",
    "00000000000200000000000000000000",
    "00000000022222000000000000000000",
    "00000000222222200000000000000000",
    "00000333222222220000020000000000",
    "00003333322222222000222000000000",
    "00003333322222222202222200000000",
    "00003333322222222222222220000000",
    "00023333322222222222222222000000",
    "00222232222222222222222222200000",
    "33333333333333333333333333333333",
    "11111131111111111111111111111111",
    "11111111111111111111111111111111",
    "11111111111111111111111111111111",
    "11111111111111111111111111111111",
    "11111111111111111111111111111111"
)

@Composable
fun gbColorStyleSubtitle(style: String): String? = when (style) {
    GbColorStyles.ORIGINAL -> stringResource(R.string.settings_libretro_def_gb_color_style_subtitle_original)
    GbColorStyles.GBC -> stringResource(R.string.settings_libretro_def_gb_color_style_subtitle_gbc)
    GbColorStyles.SGB -> stringResource(R.string.settings_libretro_def_gb_color_style_subtitle_sgb)
    GbColorStyles.SGB_NOFRAME -> stringResource(R.string.settings_libretro_def_gb_color_style_subtitle_sgb_noframe)
    GbColorStyles.CUSTOM -> stringResource(R.string.settings_libretro_def_gb_color_style_subtitle_custom)
    else -> null
}

@Composable
fun GbColorStylePreview(style: String, isFocused: Boolean) {
    val shades = GbColorStyles.previewShades(style) ?: return
    val colors = remember(shades) { shades.map { Color(it) } }
    val framed = style == GbColorStyles.SGB
    Column(horizontalAlignment = Alignment.End) {
        Canvas(
            modifier = Modifier
                .width(Dimens.avatarXl)
                .aspectRatio(SCENE_WIDTH.toFloat() / SCENE_HEIGHT)
        ) {
            val inset = if (framed) FRAME_CELLS else 0
            val cellW = size.width / (SCENE_WIDTH + inset * 2)
            val cellH = size.height / (SCENE_HEIGHT + inset * 2)
            if (framed) drawRect(colors[2])
            SCENE.forEachIndexed { row, line ->
                line.forEachIndexed { col, shade ->
                    drawRect(
                        color = colors[shade - '0'],
                        topLeft = Offset((col + inset) * cellW, (row + inset) * cellH),
                        size = Size(cellW, cellH)
                    )
                }
            }
        }
        if (style != GbColorStyles.ORIGINAL) {
            Text(
                text = stringResource(R.string.settings_libretro_gb_color_style_preview_varies_caption),
                style = MaterialTheme.typography.labelSmall,
                color = preferenceContentColor(isFocused).copy(alpha = 0.6f)
            )
        }
    }
}
