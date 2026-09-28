package com.nendo.argosy.data.local.migrations

import androidx.sqlite.db.SupportSQLiteDatabase
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class Migration201To202Test {

    private val statements = mutableListOf<String>()

    @Before
    fun run() {
        val db = mockk<SupportSQLiteDatabase>(relaxed = true)
        every { db.execSQL(any<String>()) } answers { statements += firstArg<String>() }
        Migration_201_202.migrate(db)
    }

    @Test
    fun `the color style is added as a nullable platform setting`() {
        assertEquals(
            listOf("ALTER TABLE `platform_libretro_settings` ADD COLUMN `gbColorStyle` TEXT DEFAULT NULL"),
            statements
        )
    }

    @Test
    fun `the migration is registered as the last step`() {
        assertEquals(Migration_201_202, MigrationRegistry.ALL.last())
        MigrationRegistry.assertContiguous(202)
    }
}
