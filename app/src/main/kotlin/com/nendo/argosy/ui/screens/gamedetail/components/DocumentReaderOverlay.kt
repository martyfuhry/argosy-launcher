package com.nendo.argosy.ui.screens.gamedetail.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Row
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import com.nendo.argosy.R
import androidx.compose.ui.res.stringResource
import com.nendo.argosy.ui.components.FooterHints
import com.nendo.argosy.ui.components.InputButton
import com.nendo.argosy.ui.theme.Dimens

data class DocumentReaderState(
    val title: String,
    val textPages: List<String> = emptyList(),
    val pages: List<android.graphics.Bitmap> = emptyList(),
    val pageIndex: Int = 0,
    val isLoading: Boolean = true,
    val errorReason: String? = null,
    val showsSpreads: Boolean = false
) {
    val pageCount: Int get() = if (pages.isNotEmpty()) pages.size else textPages.size

    val usesSpreads: Boolean get() = showsSpreads && pages.size > 1

    val fraction: Float get() = if (pageCount > 1) pageIndex.toFloat() / (pageCount - 1) else 0f

    val visiblePages: IntRange
        get() = if (usesSpreads) spreadOf(pageIndex, pages.size) else pageIndex..pageIndex
}

/**
 * The pages shown together as a book spread when [pageIndex] is open: the cover alone, then each
 * even page beside the odd page after it.
 */
fun spreadOf(pageIndex: Int, pageCount: Int): IntRange {
    if (pageCount <= 0) return 0..0
    val index = pageIndex.coerceIn(0, pageCount - 1)
    if (index == 0) return 0..0
    val start = if (index % 2 == 1) index else index - 1
    return start..minOf(start + 1, pageCount - 1)
}

/**
 * The first page of the spread [delta] spreads away from the one holding [pageIndex], clamped to
 * the book.
 */
fun spreadStartAfter(pageIndex: Int, pageCount: Int, delta: Int): Int {
    if (pageCount <= 0) return 0
    val current = spreadOf(pageIndex, pageCount)
    return when {
        delta > 0 -> (current.last + 1).coerceAtMost(pageCount - 1).let { spreadOf(it, pageCount).first }
        delta < 0 -> spreadOf((current.first - 1).coerceAtLeast(0), pageCount).first
        else -> current.first
    }
}

/**
 * Splits a plain-text document into pages of [linesPerPage] lines each.
 */
fun paginateText(body: String, linesPerPage: Int = TEXT_LINES_PER_PAGE): List<String> =
    body.lines()
        .chunked(linesPerPage)
        .map { it.joinToString("\n") }
        .ifEmpty { listOf("") }

const val TEXT_LINES_PER_PAGE = 34
private const val TEXT_COLUMNS = 80
private const val DEFAULT_LINE_SPACING = 1.4f
private const val MIN_LINES_PER_PAGE = 8
private const val MAX_LINES_PER_PAGE = 120

@Composable
fun DocumentReaderOverlay(
    state: DocumentReaderState,
    onLinesPerPageMeasured: (Int) -> Unit,
    onDismiss: () -> Unit,
    onTurnPage: (Int) -> Unit,
    onSpreadsMeasured: (Boolean) -> Unit = {}
) {
    val currentOnTurnPage by androidx.compose.runtime.rememberUpdatedState(onTurnPage)
    val currentOnDismiss by androidx.compose.runtime.rememberUpdatedState(onDismiss)
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.scrim.copy(alpha = READER_SCRIM_ALPHA))
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    val zone = size.width / TAP_ZONES
                    when {
                        offset.x < zone -> currentOnTurnPage(-1)
                        offset.x > size.width - zone -> currentOnTurnPage(1)
                        else -> currentOnDismiss()
                    }
                }
            }
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(Dimens.spacingXl)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = state.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                if (state.pageCount > 1) {
                    val shown = state.visiblePages
                    Text(
                        text = if (shown.first != shown.last) {
                            stringResource(
                                R.string.gamedetail_document_reader_spread,
                                shown.first + 1,
                                shown.last + 1,
                                state.pageCount
                            )
                        } else {
                            stringResource(
                                R.string.gamedetail_document_reader_page,
                                shown.first + 1,
                                state.pageCount
                            )
                        },
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(modifier = Modifier.height(Dimens.spacingMd))
            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                when {
                    state.isLoading -> CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center)
                    )
                    state.errorReason != null -> Text(
                        text = stringResource(R.string.gamedetail_document_reader_failed),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.align(Alignment.Center)
                    )
                    state.pages.isNotEmpty() -> BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                        val wide = maxWidth > maxHeight
                        LaunchedEffect(wide) { onSpreadsMeasured(wide) }
                        Row(
                            modifier = Modifier.fillMaxSize(),
                            horizontalArrangement = Arrangement.Center
                        ) {
                            state.visiblePages.forEach { index ->
                                state.pages.getOrNull(index)?.let { page ->
                                    androidx.compose.foundation.Image(
                                        bitmap = page.asImageBitmap(),
                                        contentDescription = null,
                                        contentScale = androidx.compose.ui.layout.ContentScale.Fit,
                                        alignment = when {
                                            state.visiblePages.first == state.visiblePages.last -> Alignment.Center
                                            index == state.visiblePages.first -> Alignment.CenterEnd
                                            else -> Alignment.CenterStart
                                        },
                                        modifier = Modifier.weight(1f).fillMaxHeight()
                                    )
                                }
                            }
                        }
                    }
                    else -> {
                        val baseStyle = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = FontFamily.Monospace
                        )
                        val density = androidx.compose.ui.platform.LocalDensity.current
                        val measurer = androidx.compose.ui.text.rememberTextMeasurer()
                        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                            val charWidthPx = measurer.measure("M".repeat(TEXT_COLUMNS), baseStyle)
                                .size.width.toFloat() / TEXT_COLUMNS
                            val scale = if (charWidthPx > 0f) {
                                (constraints.maxWidth / (charWidthPx * TEXT_COLUMNS)).coerceAtMost(1f)
                            } else {
                                1f
                            }
                            val textStyle = baseStyle.copy(
                                fontSize = baseStyle.fontSize * scale,
                                lineHeight = if (baseStyle.lineHeight.isSp) baseStyle.lineHeight * scale else baseStyle.lineHeight
                            )
                            val lineHeightPx = with(density) {
                                textStyle.lineHeight.takeIf { it.isSp }?.toPx()
                                    ?: (textStyle.fontSize.toPx() * DEFAULT_LINE_SPACING)
                            }
                            val fits = ((constraints.maxHeight / lineHeightPx).toInt() - 1)
                                .coerceIn(MIN_LINES_PER_PAGE, MAX_LINES_PER_PAGE)
                            LaunchedEffect(fits) { onLinesPerPageMeasured(fits) }
                            Text(
                                text = state.textPages.getOrNull(state.pageIndex).orEmpty(),
                                style = textStyle,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }
                }
            }
            FooterHints(
                hints = listOf(
                    InputButton.DPAD_HORIZONTAL to
                        stringResource(R.string.gamedetail_document_reader_turn_page)
                )
            )
        }
    }
}

private const val READER_SCRIM_ALPHA = 0.95f
private const val TAP_ZONES = 3
