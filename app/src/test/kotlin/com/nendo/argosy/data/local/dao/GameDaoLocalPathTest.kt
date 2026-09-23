package com.nendo.argosy.data.local.dao

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class GameDaoLocalPathTest {

    private val updateLocalPathQuery: String by lazy {
        val source = listOf(File("src/main/kotlin"), File("app/src/main/kotlin"))
            .map { File(it, "com/nendo/argosy/data/local/dao/GameDao.kt") }
            .first { it.exists() }
            .readText()
        Regex("""@Query\("([^"]*)"\)\s*suspend fun updateLocalPath\(""")
            .find(source)
            ?.groupValues
            ?.get(1)
            ?: error("updateLocalPath query not found")
    }

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
}
