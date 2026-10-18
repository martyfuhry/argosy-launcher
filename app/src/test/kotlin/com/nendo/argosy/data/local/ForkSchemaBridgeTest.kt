package com.nendo.argosy.data.local

import android.database.Cursor
import androidx.sqlite.db.SupportSQLiteDatabase
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ForkSchemaBridgeTest {

    private val upstream205Settings = listOf("id", "platformId", "shader", "preferNewerServerSave")
    private val forkSettings = upstream205Settings + listOf("audioBufferFrames", "gbColorStyle")
    private val addFlag = "ALTER TABLE `pending_conflicts` ADD COLUMN `isHardcoreDowngrade` INTEGER NOT NULL DEFAULT 0"

    private class Run(val repaired: Boolean, val statements: List<String>)

    private fun run(
        version: Int,
        settingsColumns: List<String>,
        pendingConflictColumns: List<String> = listOf("id", "gameId")
    ): Run {
        val captured = mutableListOf<String>()
        val db = mockk<SupportSQLiteDatabase>(relaxed = true)
        every { db.version } returns version
        every { db.execSQL(any<String>()) } answers { captured += firstArg<String>() }
        every { db.query(any<String>()) } answers {
            val sql = firstArg<String>()
            cursorOf(if ("pending_conflicts" in sql) pendingConflictColumns else settingsColumns)
        }
        return Run(ForkSchemaBridge.repair(db), captured)
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
    fun `a database at another version is left alone`() {
        val run = run(version = 215, settingsColumns = forkSettings)

        assertFalse(run.repaired)
        assertTrue(run.statements.isEmpty())
    }

    @Test
    fun `an upstream 205 database is left alone`() {
        val run = run(version = 205, settingsColumns = upstream205Settings)

        assertFalse(run.repaired)
        assertTrue(run.statements.isEmpty())
    }

    @Test
    fun `a fork 205 database gains the upstream tables and flag and loses the fork columns`() {
        val run = run(version = 205, settingsColumns = forkSettings)

        assertTrue(run.repaired)
        assertTrue(run.statements.any { it.startsWith("CREATE TABLE IF NOT EXISTS `sigil_sync_state`") })
        assertTrue(run.statements.any { it.startsWith("CREATE TABLE IF NOT EXISTS `snapshot_channels`") })
        assertEquals(1, run.statements.count { it == addFlag })
        val rebuild = run.statements.dropWhile { !it.contains("`platform_libretro_settings_bridge`") }
        assertTrue(rebuild[0].startsWith("CREATE TABLE IF NOT EXISTS `platform_libretro_settings_bridge`"))
        assertTrue(rebuild[1].startsWith("INSERT INTO `platform_libretro_settings_bridge`"))
        assertFalse(rebuild[1].contains("audioBufferFrames"))
        assertFalse(rebuild[1].contains("gbColorStyle"))
        assertEquals("DROP TABLE `platform_libretro_settings`", rebuild[2])
        assertEquals("ALTER TABLE `platform_libretro_settings_bridge` RENAME TO `platform_libretro_settings`", rebuild[3])
        assertTrue(rebuild[4].startsWith("CREATE UNIQUE INDEX IF NOT EXISTS `index_platform_libretro_settings_platformId`"))
        assertEquals(5, rebuild.size)
    }

    @Test
    fun `a fork database that already has the hardcore flag is not altered twice`() {
        val run = run(
            version = 205,
            settingsColumns = forkSettings,
            pendingConflictColumns = listOf("id", "gameId", "isHardcoreDowngrade")
        )

        assertTrue(run.repaired)
        assertTrue(run.statements.none { it == addFlag })
    }
}
