package com.nendo.argosy.ui.screens.gamedetail.components

import com.nendo.argosy.data.local.entity.GameEntity
import com.nendo.argosy.data.local.entity.GameFileEntity
import com.nendo.argosy.data.model.VariantCategory
import com.nendo.argosy.data.preferences.DownloadDefaults
import com.nendo.argosy.data.remote.romm.RomMRepository
import com.nendo.argosy.data.repository.DocumentContent
import com.nendo.argosy.data.repository.GameDocumentLoader
import com.nendo.argosy.ui.screens.gamedetail.GameDocument
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private const val PDF_PAGE_WIDTH_PX = 1080
private const val PROGRESS_DEBOUNCE_MS = 1500L

/**
 * The documents a game offers: the metadata provider's manual, then its RomM manual and
 * walkthrough files.
 */
fun gameDocuments(
    game: GameEntity?,
    files: List<GameFileEntity>,
    romMRepository: RomMRepository
): List<GameDocument> = providerManual(game, romMRepository) + files
    .filter { it.category in DownloadDefaults.DOCUMENT_KEYS }
    .mapNotNull { file ->
        val rommFileId = file.rommFileId ?: return@mapNotNull null
        GameDocument(
            fileName = file.fileName,
            title = file.docTitle?.takeIf { it.isNotBlank() } ?: file.fileName,
            category = file.category,
            rommFileId = rommFileId,
            source = file.docSource,
            localPath = file.localPath?.takeIf { file.isLocallyPresent() },
            sizeBytes = file.fileSize
        )
    }

private fun providerManual(game: GameEntity?, romMRepository: RomMRepository): List<GameDocument> {
    if (game == null || !game.hasManual) return emptyList()
    val url = romMRepository.buildResourceUrlPublic(game.manualPath) ?: return emptyList()
    return listOf(
        GameDocument(
            fileName = game.manualPath?.substringAfterLast('/') ?: "manual.pdf",
            title = game.title,
            category = VariantCategory.MANUAL.key,
            remoteUrl = url
        )
    )
}

/**
 * Owns one open document: loading, pagination, two-page spreads, and the reading position
 * synced to RomM.
 */
class DocumentReaderController(
    private val scope: CoroutineScope,
    private val loader: GameDocumentLoader,
    private val romMRepository: RomMRepository
) {
    private val _state = MutableStateFlow<DocumentReaderState?>(null)
    val state: StateFlow<DocumentReaderState?> = _state.asStateFlow()

    private var document: GameDocument? = null
    private var romId: Long? = null
    private var body: String? = null
    private var linesPerPage: Int = TEXT_LINES_PER_PAGE
    private var progressJob: Job? = null

    fun open(document: GameDocument, romId: Long?) {
        this.document = document
        this.romId = romId
        body = null
        progressJob?.cancel()
        _state.value = DocumentReaderState(title = document.title)
        scope.launch {
            val resume = async { resumeFraction(document, romId) }
            val content = load(document)
            val fraction = resume.await()
            _state.update { reader ->
                if (reader == null || this@DocumentReaderController.document != document) {
                    return@update reader
                }
                when (content) {
                    is DocumentContent.Text -> {
                        body = content.body
                        val pages = paginateText(content.body, linesPerPage)
                        reader.copy(
                            textPages = pages,
                            pageIndex = pageAt(fraction, pages.size),
                            isLoading = false
                        )
                    }
                    is DocumentContent.Pages -> reader.copy(
                        pages = content.pages,
                        pageIndex = pageAt(fraction, content.pages.size),
                        isLoading = false
                    )
                    is DocumentContent.Unavailable ->
                        reader.copy(isLoading = false, errorReason = content.reason ?: "unavailable")
                }
            }
        }
    }

    /**
     * Re-splits an open text document for [linesPerPage], keeping the reader's place.
     */
    fun setLinesPerPage(linesPerPage: Int) {
        if (linesPerPage == this.linesPerPage) return
        this.linesPerPage = linesPerPage
        val text = body ?: return
        _state.update { reader ->
            reader ?: return@update null
            val pages = paginateText(text, linesPerPage)
            reader.copy(textPages = pages, pageIndex = pageAt(reader.fraction, pages.size))
        }
    }

    fun setShowsSpreads(showsSpreads: Boolean) {
        _state.update { it?.copy(showsSpreads = showsSpreads) }
    }

    fun turnPage(delta: Int) {
        val reader = _state.value ?: return
        if (reader.pageCount <= 1) return
        val next = if (reader.usesSpreads) {
            spreadStartAfter(reader.pageIndex, reader.pageCount, delta)
        } else {
            (reader.pageIndex + delta).coerceIn(0, reader.pageCount - 1)
        }
        if (next == reader.pageIndex) return
        _state.update { it?.copy(pageIndex = next) }
        progressJob?.cancel()
        progressJob = scope.launch {
            delay(PROGRESS_DEBOUNCE_MS)
            saveProgress()
        }
    }

    fun dismiss() {
        if (_state.value == null) return
        progressJob?.cancel()
        scope.launch {
            saveProgress()
            body = null
            document = null
            _state.value = null
        }
    }

    private suspend fun load(document: GameDocument): DocumentContent =
        if (document.isPdf) {
            loader.readPdf(
                localPath = document.localPath,
                rommFileId = document.rommFileId,
                remoteUrl = document.remoteUrl,
                fileName = document.fileName,
                pageWidthPx = PDF_PAGE_WIDTH_PX
            )
        } else {
            loader.readText(
                localPath = document.localPath,
                rommFileId = document.rommFileId,
                remoteUrl = document.remoteUrl,
                fileName = document.fileName
            )
        }

    private suspend fun resumeFraction(document: GameDocument, romId: Long?): Float {
        val fileId = document.rommFileId ?: return 0f
        if (romId == null) return 0f
        return romMRepository.getDocumentProgress(romId, fileId)?.progress?.coerceIn(0f, 1f) ?: 0f
    }

    private suspend fun saveProgress() {
        val reader = _state.value ?: return
        val open = document ?: return
        val fileId = open.rommFileId ?: return
        val rom = romId ?: return
        romMRepository.updateDocumentProgress(
            romId = rom,
            fileId = fileId,
            progress = reader.fraction,
            lastPage = reader.pageIndex.takeIf { open.isPdf }
        )
    }

    private fun pageAt(fraction: Float, pageCount: Int): Int =
        if (pageCount <= 1) 0 else ((pageCount - 1) * fraction).toInt().coerceIn(0, pageCount - 1)
}
