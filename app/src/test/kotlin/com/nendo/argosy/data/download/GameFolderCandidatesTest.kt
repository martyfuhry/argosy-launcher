package com.nendo.argosy.data.download

import org.junit.Assert.assertEquals
import org.junit.Test

class GameFolderCandidatesTest {

    @Test
    fun `a blank folder name gives way to the title`() {
        assertEquals(
            listOf("Super Mario Bros"),
            DownloadManager.gameFolderCandidates(null, "  ", "Super Mario Bros")
        )
    }

    @Test
    fun `self and parent references give way to the title`() {
        assertEquals(
            listOf("Metroid"),
            DownloadManager.gameFolderCandidates(".", "..", "Metroid")
        )
    }

    @Test
    fun `ordinary names are folded and kept in order`() {
        assertEquals(
            listOf("Zelda_ Link", "The Legend of Zelda"),
            DownloadManager.gameFolderCandidates("Zelda: Link", "The Legend of Zelda")
        )
    }

    @Test
    fun `a separator cannot survive into a candidate`() {
        assertEquals(
            listOf(".._.._x"),
            DownloadManager.gameFolderCandidates("../../x")
        )
    }
}
