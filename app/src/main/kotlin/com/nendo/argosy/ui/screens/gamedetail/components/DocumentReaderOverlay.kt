package com.nendo.argosy.ui.screens.gamedetail.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.layout.Row
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
import com.nendo.argosy.ui.util.clickableNoFocus

data class DocumentReaderState(
    val title: String,
    val textPages: List<String> = emptyList(),
    val pages: List<android.graphics.Bitmap> = emptyList(),
    val pageIndex: Int = 0,
    val isLoading: Boolean = true,
    val errorReason: String? = null
) {
    val pageCount: Int get() = if (pages.isNotEmpty()) pages.size else textPages.size
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
private const val DEFAULT_LINE_SPACING = 1.4f
private const val MIN_LINES_PER_PAGE = 8
private const val MAX_LINES_PER_PAGE = 120

@Composable
fun DocumentReaderOverlay(
    state: DocumentReaderState,
    onLinesPerPageMeasured: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.scrim.copy(alpha = READER_SCRIM_ALPHA))
            .clickableNoFocus(onClick = onDismiss)
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
                    Text(
                        text = stringResource(
                            R.string.gamedetail_document_reader_page,
                            state.pageIndex + 1,
                            state.pageCount
                        ),
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
                    state.pages.isNotEmpty() -> state.pages.getOrNull(state.pageIndex)?.let { page ->
                        androidx.compose.foundation.Image(
                            bitmap = page.asImageBitmap(),
                            contentDescription = null,
                            contentScale = androidx.compose.ui.layout.ContentScale.Fit,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                    else -> {
                        val textStyle = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = FontFamily.Monospace
                        )
                        val density = androidx.compose.ui.platform.LocalDensity.current
                        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
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
