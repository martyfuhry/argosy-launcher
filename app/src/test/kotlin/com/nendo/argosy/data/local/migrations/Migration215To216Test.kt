package com.nendo.argosy.data.local.migrations

import android.database.Cursor
import androidx.sqlite.db.SupportSQLiteDatabase
import io.mockk.every
import io.mockk.mockk
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class Migration215To216Test {

    private val addSkipFullSync = "ALTER TABLE `platforms` ADD COLUMN `skipFullSync` INTEGER NOT NULL DEFAULT 0"
    private val addGbColorStyle =
        "ALTER TABLE `platform_libretro_settings` ADD COLUMN `gbColorStyle` TEXT DEFAULT NULL"

    private fun statements(platformColumns: List<String>, settingsColumns: List<String>): List<String> {
        val captured = mutableListOf<String>()
        val db = mockk<SupportSQLiteDatabase>(relaxed = true)
        every { db.execSQL(any<String>()) } answers { captured += firstArg<String>() }
        every { db.query("PRAGMA table_info(`platforms`)") } answers { cursorOf(platformColumns) }
        every { db.query("PRAGMA table_info(`platform_libretro_settings`)") } answers { cursorOf(settingsColumns) }
        Migration_215_216.migrate(db)
        return captured
    }

    private fun cursorOf(columns: List<String>): Cursor {
        val cursor = mockk<Cursor>(relaxed = true)
        var row = -1
        every { cursor.getColumnIndex("name") } returns 1
        every { cursor.moveToNext() } answers { row += 1; row < columns.size }
        every { cursor.getString(1) } answers { columns[row] }
        return cursor
    }

    @Test
    fun `an upstream 215 database gains only the game boy color style column`() {
        val run = statements(
            platformColumns = listOf("id", "slug", "skipFullSync"),
            settingsColumns = listOf("id", "platformId", "gpuDriver")
        )

        assertEquals(listOf(addGbColorStyle), run)
    }

    @Test
    fun `a fork 215 database gains only the full sync skip flag and keeps its color styles`() {
        val run = statements(
            platformColumns = listOf("id", "slug"),
            settingsColumns = listOf("id", "platformId", "gpuDriver", "gbColorStyle")
        )

        assertEquals(listOf(addSkipFullSync), run)
    }

    @Test
    fun `a database that already has both columns is not altered`() {
        val run = statements(
            platformColumns = listOf("id", "slug", "skipFullSync"),
            settingsColumns = listOf("id", "platformId", "gbColorStyle")
        )

        assertTrue(run.isEmpty())
    }

    @Test
    fun `a fresh install creates both columns at 216`() {
        val schemaPath = "schemas/com.nendo.argosy.data.local.ALauncherDatabase/216.json"
        val schema = listOf(File(schemaPath), File("app/$schemaPath")).first { it.exists() }
        val database = JSONObject(schema.readText()).getJSONObject("database")
        val entities = database.getJSONArray("entities")
        val columns = (0 until entities.length()).map { entities.getJSONObject(it) }.associate { entity ->
            val fields = entity.getJSONArray("fields")
            entity.getString("tableName") to (0 until fields.length()).map { fields.getJSONObject(it).getString("columnName") }
        }

        assertEquals(216, database.getInt("version"))
        assertTrue("skipFullSync" in columns.getValue("platforms"))
        assertTrue("gbColorStyle" in columns.getValue("platform_libretro_settings"))
    }

    @Test
    fun `is the last registered migration and leaves no gap`() {
        assertEquals(Migration_215_216, MigrationRegistry.ALL.last())
        MigrationRegistry.assertContiguous(216)
    }
}
