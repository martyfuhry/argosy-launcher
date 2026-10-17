package com.nendo.argosy.data.local.migrations

import androidx.sqlite.db.SupportSQLiteDatabase
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Test

class Migration214To215Test {

    private fun statements(): List<String> {
        val captured = mutableListOf<String>()
        val db = mockk<SupportSQLiteDatabase>(relaxed = true)
        every { db.execSQL(any<String>()) } answers { captured += firstArg<String>() }
        Migration_214_215.migrate(db)
        return captured
    }

    @Test
    fun `adds the nullable game boy color style column to the platform settings`() {
        assertEquals(
            listOf("ALTER TABLE `platform_libretro_settings` ADD COLUMN `gbColorStyle` TEXT DEFAULT NULL"),
            statements()
        )
    }

    @Test
    fun `is the last registered migration and leaves no gap`() {
        assertEquals(Migration_214_215, MigrationRegistry.ALL.last())
        MigrationRegistry.assertContiguous(215)
    }
}
