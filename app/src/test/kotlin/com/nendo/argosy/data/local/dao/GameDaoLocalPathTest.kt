package com.nendo.argosy.data.local.dao

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class GameDaoLocalPathTest {

    private fun mainSource(relativePath: String): String =
        listOf(File("src/main/kotlin"), File("app/src/main/kotlin"))
            .map { File(it, relativePath) }
            .first { it.exists() }
            .readText()

    private val gameDaoSource: String by lazy {
        mainSource("com/nendo/argosy/data/local/dao/GameDao.kt")
    }

    private val downloadManagerSource: String by lazy {
        mainSource("com/nendo/argosy/data/download/DownloadManager.kt")
    }

    private fun queryOf(function: String): String =
        Regex("""@Query\("([^"]*)"\)\s*suspend fun $function\(""")
            .find(gameDaoSource)
            ?.groupValues
            ?.get(1)
            ?: error("$function query not found")

    private val updateLocalPathQuery: String by lazy { queryOf("updateLocalPath") }

    private val markDownloadedQuery: String by lazy { queryOf("markDownloaded") }

    @Test
    fun `pointing a game at a file keeps its added time`() {
        assertFalse(updateLocalPathQuery.contains("addedAt"))
    }

    @Test
    fun `pointing a game at a file records the path, origin and source`() {
        assertTrue(updateLocalPathQuery.contains("localPath = :path"))
        assertTrue(updateLocalPathQuery.contains("fileOrigin = :fileOrigin"))
        assertTrue(updateLocalPathQuery.contains("source = :source"))
    }

    @Test
    fun `marking a game downloaded restamps its added time`() {
        assertTrue(markDownloadedQuery.contains("addedAt = :addedAt"))
    }

    @Test
    fun `marking a game downloaded records the path, origin and source`() {
        assertTrue(markDownloadedQuery.contains("localPath = :path"))
        assertTrue(markDownloadedQuery.contains("fileOrigin = :fileOrigin"))
        assertTrue(markDownloadedQuery.contains("source = :source"))
    }

    @Test
    fun `every download completion claims its file through markDownloaded`() {
        val downloadWrites = Regex("""gameDao\.(\w+)\([^)]*FileOrigin\.ROMM_DOWNLOAD\)""")
            .findAll(downloadManagerSource)
            .map { it.groupValues[1] }
            .toList()

        assertEquals(3, downloadWrites.size)
        assertTrue(downloadWrites.all { it == "markDownloaded" })
    }

    @Test
    fun `only download completion restamps a game's added time`() {
        val callers = listOf(File("src/main/kotlin"), File("app/src/main/kotlin"))
            .first { it.exists() }
            .walkTopDown()
            .filter { it.isFile && it.extension == "kt" && it.readText().contains("gameDao.markDownloaded(") }
            .map { it.name }
            .toList()

        assertEquals(listOf("DownloadManager.kt"), callers)
    }
}
