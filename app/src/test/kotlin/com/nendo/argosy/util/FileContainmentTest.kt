package com.nendo.argosy.util

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * Pins the containment rule every archive extraction leans on. A prefix comparison also
 * accepts a sibling directory whose name starts with the target's, which is how an entry
 * escapes the folder it was meant to land in.
 */
class FileContainmentTest {

    @get:Rule
    val temp = TemporaryFolder()

    @Test
    fun `accepts a child and a nested descendant`() {
        val dir = temp.newFolder("saves")
        assertTrue(File(dir, "game.srm").isInside(dir))
        assertTrue(File(dir, "gba/game.srm").isInside(dir))
    }

    @Test
    fun `rejects a sibling sharing the name as a prefix`() {
        val dir = temp.newFolder("saves")
        val sibling = temp.newFolder("saves_backup")
        assertFalse(sibling.isInside(dir))
        assertFalse(File(sibling, "game.srm").isInside(dir))
    }

    @Test
    fun `accepts the directory itself`() {
        val dir = temp.newFolder("saves")
        assertTrue(dir.isInside(dir))
    }

    @Test
    fun `rejects an entry climbing out with dot segments`() {
        val dir = temp.newFolder("saves")
        assertFalse(File(dir, "../saves_backup/game.srm").isInside(dir))
        assertFalse(File(dir, "../../etc/passwd").isInside(dir))
    }
}
