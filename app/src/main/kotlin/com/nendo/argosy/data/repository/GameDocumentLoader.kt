package com.nendo.argosy.data.repository

import com.nendo.argosy.data.remote.romm.RomMRepository
import com.nendo.argosy.data.remote.romm.RomMResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

sealed interface DocumentContent {
    data class Text(val body: String) : DocumentContent
    data class Pages(val pages: List<android.graphics.Bitmap>) : DocumentContent
    data class Unavailable(val reason: String?) : DocumentContent
}

@Singleton
class GameDocumentLoader @Inject constructor(
    private val romMRepository: RomMRepository,
    @dagger.hilt.android.qualifiers.ApplicationContext private val context: android.content.Context
) {
    /**
     * A PDF rendered to one bitmap per page, up to [MAX_PDF_PAGES]. A remote document is staged
     * in the cache directory and deleted once its pages are rasterised.
     */
    suspend fun readPdf(
        localPath: String?,
        rommFileId: Long?,
        remoteUrl: String?,
        fileName: String,
        pageWidthPx: Int
    ): DocumentContent = withContext(Dispatchers.IO) {
        val source = localPath?.let { File(it) }?.takeIf { it.isFile }
            ?: stageRemote(rommFileId, remoteUrl, fileName)
            ?: return@withContext DocumentContent.Unavailable("unreachable")
        try {
            renderPages(source, pageWidthPx)
        } catch (e: Exception) {
            DocumentContent.Unavailable(e.message)
        } finally {
            if (source.parentFile == context.cacheDir) source.delete()
        }
    }

    private fun renderPages(file: File, pageWidthPx: Int): DocumentContent {
        android.os.ParcelFileDescriptor.open(
            file,
            android.os.ParcelFileDescriptor.MODE_READ_ONLY
        ).use { descriptor ->
            android.graphics.pdf.PdfRenderer(descriptor).use { renderer ->
                val pages = (0 until minOf(renderer.pageCount, MAX_PDF_PAGES)).map { index ->
                    renderer.openPage(index).use { page ->
                        val scale = pageWidthPx.toFloat() / page.width
                        val bitmap = android.graphics.Bitmap.createBitmap(
                            pageWidthPx,
                            (page.height * scale).toInt().coerceAtLeast(1),
                            android.graphics.Bitmap.Config.ARGB_8888
                        )
                        bitmap.eraseColor(android.graphics.Color.WHITE)
                        page.render(
                            bitmap,
                            null,
                            null,
                            android.graphics.pdf.PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY
                        )
                        bitmap
                    }
                }
                return DocumentContent.Pages(pages)
            }
        }
    }

    private suspend fun stageRemote(
        rommFileId: Long?,
        remoteUrl: String?,
        fileName: String
    ): File? {
        val target = File(context.cacheDir, "doc_${System.nanoTime()}_$fileName")
        val body = when {
            rommFileId != null -> when (val r = romMRepository.downloadRomFile(rommFileId, fileName)) {
                is RomMResult.Success -> r.data.body
                is RomMResult.Error -> return null
            }
            remoteUrl != null -> romMRepository.openResource(remoteUrl) ?: return null
            else -> return null
        }
        return runCatching {
            body.use { source ->
                target.outputStream().use { out -> source.byteStream().copyTo(out) }
            }
            target
        }.getOrNull()
    }

    /**
     * A document's text, read from disk when the game was downloaded and streamed from the server
     * when it was not. A streamed read is never written to disk: a document belongs to a game the
     * player keeps, not to one they are only looking at.
     */
    suspend fun readText(
        localPath: String?,
        rommFileId: Long?,
        remoteUrl: String? = null,
        fileName: String,
        maxBytes: Int = MAX_TEXT_BYTES
    ): DocumentContent = withContext(Dispatchers.IO) {
        localPath?.let { path ->
            val file = File(path)
            if (file.isFile) {
                return@withContext runCatching { DocumentContent.Text(file.readText()) }
                    .getOrElse { DocumentContent.Unavailable(it.message) }
            }
        }

        val body = when {
            rommFileId != null -> when (val r = romMRepository.downloadRomFile(rommFileId, fileName)) {
                is RomMResult.Success -> r.data.body
                is RomMResult.Error -> return@withContext DocumentContent.Unavailable(r.message)
            }
            remoteUrl != null -> romMRepository.openResource(remoteUrl)
                ?: return@withContext DocumentContent.Unavailable("unreachable")
            else -> return@withContext DocumentContent.Unavailable("no source")
        }
        runCatching {
            body.use { source ->
                DocumentContent.Text(String(source.byteStream().readNBytes(maxBytes), Charsets.UTF_8))
            }
        }.getOrElse { DocumentContent.Unavailable(it.message) }
    }

    private companion object {
        const val MAX_TEXT_BYTES = 4 * 1024 * 1024
        const val MAX_PDF_PAGES = 200
    }
}
