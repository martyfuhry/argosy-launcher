package com.nendo.argosy.data.local

import android.database.Cursor
import androidx.sqlite.db.SupportSQLiteDatabase
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ForkSchemaBridgeTest {

    private val upstreamSettings = ForkSchemaBridge.UPSTREAM_COLUMNS
    private val forkSettings = upstreamSettings + "gbColorStyle"
    private val upstream214Platforms = listOf("id", "slug", "name")
    private val upstream215Platforms = upstream214Platforms + "skipFullSync"
    private val deletionColumns = listOf("gameId", "ownerUserId", "deletedBefore")

    private val addSkipFullSync = "ALTER TABLE `platforms` ADD COLUMN `skipFullSync` INTEGER NOT NULL DEFAULT 0"

    private class Run(val repaired: Boolean, val statements: List<String>, val db: SupportSQLiteDatabase)

    private fun run(
        version: Int,
        settingsColumns: List<String>,
        platformColumns: List<String>,
        playRecordDeletionColumns: List<String> = emptyList()
    ): Run {
        val captured = mutableListOf<String>()
        val db = mockk<SupportSQLiteDatabase>(relaxed = true)
        every { db.version } returns version
        every { db.execSQL(any<String>()) } answers { captured += firstArg<String>() }
        every { db.query("PRAGMA table_info(`platform_libretro_settings`)") } answers { cursorOf(settingsColumns) }
        every { db.query("PRAGMA table_info(`platforms`)") } answers { cursorOf(platformColumns) }
        every { db.query("PRAGMA table_info(`play_record_deletions`)") } answers {
            cursorOf(playRecordDeletionColumns)
        }
        return Run(ForkSchemaBridge.repair(db), captured, db)
    }

    private fun cursorOf(columns: List<String>): Cursor {
        val cursor = mockk<Cursor>(relaxed = true)
        var row = -1
        every { cursor.getColumnIndex("name") } returns 1
        every { cursor.moveToNext() } answers { row += 1; row < columns.size }
        every { cursor.getString(1) } answers { columns[row] }
        return cursor
    }

    private fun assertRebuildsWithoutColorStyle(statements: List<String>) {
        assertEquals(ForkSchemaBridge.CREATE_REBUILT, statements[0])
        assertTrue(statements[1].startsWith("INSERT INTO `platform_libretro_settings_bridge`"))
        assertFalse(statements[1].contains("gbColorStyle"))
        upstreamSettings.forEach { assertTrue(it, statements[1].contains("`$it`")) }
        assertEquals("DROP TABLE `platform_libretro_settings`", statements[2])
        assertEquals("ALTER TABLE `platform_libretro_settings_bridge` RENAME TO `platform_libretro_settings`", statements[3])
        assertEquals(ForkSchemaBridge.CREATE_PLATFORM_INDEX, statements[4])
    }

    @Test
    fun `an upstream database without the color style column issues no statements`() {
        listOf(214, 215, 216, 217, 218).forEach { version ->
            val run = run(version = version, settingsColumns = upstreamSettings, platformColumns = upstream215Platforms)

            assertFalse(run.repaired)
            assertTrue(run.statements.isEmpty())
            verify(exactly = 0) { run.db.beginTransaction() }
        }
    }

    @Test
    fun `a fresh install with no tables is left alone`() {
        val run = run(version = 0, settingsColumns = emptyList(), platformColumns = emptyList())

        assertFalse(run.repaired)
        assertTrue(run.statements.isEmpty())
    }

    @Test
    fun `a fork 215 database loses the color style column and gains the full sync skip flag`() {
        val run = run(version = 215, settingsColumns = forkSettings, platformColumns = upstream214Platforms)

        assertTrue(run.repaired)
        assertRebuildsWithoutColorStyle(run.statements)
        assertEquals(listOf(addSkipFullSync), run.statements.drop(5))
        verify(exactly = 1) { run.db.setTransactionSuccessful() }
        verify(exactly = 0) { run.db.version = any() }
    }

    @Test
    fun `a fork 216 database loses the color style column and gains the play record deletions table`() {
        val run = run(version = 216, settingsColumns = forkSettings, platformColumns = upstream215Platforms)

        assertTrue(run.repaired)
        assertRebuildsWithoutColorStyle(run.statements)
        val rest = run.statements.drop(5)
        assertEquals(2, rest.size)
        assertTrue(rest[0].startsWith("CREATE TABLE IF NOT EXISTS `play_record_deletions`"))
        assertTrue(rest[1].startsWith("CREATE INDEX IF NOT EXISTS `index_play_record_deletions_ownerUserId`"))
        verify(exactly = 1) { run.db.setTransactionSuccessful() }
        verify(exactly = 0) { run.db.version = any() }
    }

    @Test
    fun `a color style column at an unrecognised version is left alone`() {
        listOf(214, 217, 218).forEach { version ->
            val run = run(version = version, settingsColumns = forkSettings, platformColumns = upstream215Platforms)

            assertFalse(run.repaired)
            assertTrue(run.statements.isEmpty())
        }
    }

    @Test
    fun `a fork 215 that already has the full sync skip flag is left alone`() {
        val run = run(version = 215, settingsColumns = forkSettings, platformColumns = upstream215Platforms)

        assertFalse(run.repaired)
        assertTrue(run.statements.isEmpty())
    }

    @Test
    fun `a fork 216 that lacks the full sync skip flag or already has the deletions table is left alone`() {
        val noFlag = run(version = 216, settingsColumns = forkSettings, platformColumns = upstream214Platforms)
        val hasTable = run(
            version = 216,
            settingsColumns = forkSettings,
            platformColumns = upstream215Platforms,
            playRecordDeletionColumns = deletionColumns
        )

        assertFalse(noFlag.repaired)
        assertTrue(noFlag.statements.isEmpty())
        assertFalse(hasTable.repaired)
        assertTrue(hasTable.statements.isEmpty())
    }

    @Test
    fun `platform settings with columns beyond the color style are left alone`() {
        val run = run(
            version = 215,
            settingsColumns = forkSettings + "audioBufferFrames",
            platformColumns = upstream214Platforms
        )

        assertFalse(run.repaired)
        assertTrue(run.statements.isEmpty())
    }

    @Test
    fun `the rebuilt table and index match every exported upstream schema the chain passes through`() {
        listOf(215, 216, 217, 218).forEach { version ->
            val schemaPath = "schemas/com.nendo.argosy.data.local.ALauncherDatabase/$version.json"
            val schema = listOf(File(schemaPath), File("app/$schemaPath")).first { it.exists() }
            val entities = JSONObject(schema.readText()).getJSONObject("database").getJSONArray("entities")
            val settings = (0 until entities.length()).map { entities.getJSONObject(it) }
                .first { it.getString("tableName") == "platform_libretro_settings" }
            val fields = settings.getJSONArray("fields")
            val indices = settings.getJSONArray("indices")

            assertEquals(
                "$version",
                settings.getString("createSql").replace("\${TABLE_NAME}", ForkSchemaBridge.REBUILT),
                ForkSchemaBridge.CREATE_REBUILT
            )
            assertEquals(
                "$version",
                upstreamSettings,
                (0 until fields.length()).map { fields.getJSONObject(it).getString("columnName") }
            )
            assertEquals("$version", 1, indices.length())
            assertEquals(
                "$version",
                indices.getJSONObject(0).getString("createSql").replace("\${TABLE_NAME}", "platform_libretro_settings"),
                ForkSchemaBridge.CREATE_PLATFORM_INDEX
            )
        }
    }
}
