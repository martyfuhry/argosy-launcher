package com.nendo.argosy.ui.common

import com.nendo.argosy.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class SaveConflictDirectionUiTest {

    private val earlier = Instant.parse("2026-09-20T10:00:00Z")
    private val later = Instant.parse("2026-09-20T12:00:00Z")

    @Test
    fun `server copy this device last synced reads as older even when its timestamp is later`() {
        val direction = saveConflictDirection(serverMatchesLastSync = true, localTimestamp = earlier, serverTimestamp = later)

        assertEquals(SaveConflictDirection.SERVER_OLDER, direction)
        assertTrue(direction.localIsNewer)
        assertEquals(R.string.ui_save_conflict_message_server_older, direction.modalMessageRes)
        assertEquals(R.string.ui_sync_overlay_post_session_message_server_older, direction.overlayMessageRes)
    }

    @Test
    fun `unrelated server copy with the later timestamp keeps the server-newer wording`() {
        val direction = saveConflictDirection(serverMatchesLastSync = false, localTimestamp = earlier, serverTimestamp = later)

        assertEquals(SaveConflictDirection.SERVER_NEWER, direction)
        assertFalse(direction.localIsNewer)
        assertEquals(R.string.ui_save_conflict_message, direction.modalMessageRes)
        assertEquals(R.string.ui_sync_overlay_post_session_message, direction.overlayMessageRes)
    }

    @Test
    fun `local save newer than an unrelated server copy reads as diverged`() {
        val direction = saveConflictDirection(serverMatchesLastSync = false, localTimestamp = later, serverTimestamp = earlier)

        assertEquals(SaveConflictDirection.DIVERGED, direction)
        assertTrue(direction.localIsNewer)
        assertEquals(R.string.ui_save_conflict_message_diverged, direction.modalMessageRes)
        assertEquals(R.string.ui_sync_overlay_post_session_message_diverged, direction.overlayMessageRes)
    }
}
