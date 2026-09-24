package com.nendo.argosy.data.repository

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Highlighted line ranges of text documents, kept on this device only. Each document's ranges
 * live in one file named by [documentKey], one `first last` pair of document line numbers per line.
 */
@Singleton
class DocumentHighlightStore @Inject constructor(
    @ApplicationContext private val context: Context
) {
    suspend fun load(key: String): List<IntRange> = withContext(Dispatchers.IO) {
        val file = fileFor(key)
        if (!file.isFile) return@withContext emptyList()
        decodeHighlights(file.readText())
    }

    suspend fun save(key: String, highlights: List<IntRange>) = withContext(Dispatchers.IO) {
        val file = fileFor(key)
        if (highlights.isEmpty()) {
            file.delete()
        } else {
            file.parentFile?.mkdirs()
            file.writeText(encodeHighlights(highlights))
        }
    }

    private fun fileFor(key: String): File =
        File(File(context.filesDir, HIGHLIGHT_DIR), "$key.txt")

    private companion object {
        const val HIGHLIGHT_DIR = "document_highlights"
    }
}

internal fun encodeHighlights(highlights: List<IntRange>): String =
    highlights.sortedBy { it.first }.joinToString("\n") { "${it.first} ${it.last}" }

internal fun decodeHighlights(text: String): List<IntRange> =
    text.lineSequence().mapNotNull { line ->
        val parts = line.trim().split(' ')
        val first = parts.getOrNull(0)?.toIntOrNull() ?: return@mapNotNull null
        val last = parts.getOrNull(1)?.toIntOrNull() ?: return@mapNotNull null
        if (last < first) null else first..last
    }.toList()
