package com.nendo.argosy.data.local.migrations

import androidx.sqlite.db.SupportSQLiteDatabase
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class Migration200To201Test {

    private val statements = mutableListOf<String>()

    @Before
    fun run() {
        val db = mockk<SupportSQLiteDatabase>(relaxed = true)
        every { db.execSQL(any<String>()) } answers { statements += firstArg<String>() }
        Migration_200_201.migrate(db)
    }

    @Test
    fun `the audio buffer override is added as a nullable platform setting`() {
        assertEquals(
            listOf("ALTER TABLE `platform_libretro_settings` ADD COLUMN `audioBufferFrames` INTEGER DEFAULT NULL"),
            statements
        )
    }

    @Test
    fun `the migration is registered as the last step`() {
        assertEquals(Migration_200_201, MigrationRegistry.ALL.last())
        MigrationRegistry.assertContiguous(201)
    }
}
