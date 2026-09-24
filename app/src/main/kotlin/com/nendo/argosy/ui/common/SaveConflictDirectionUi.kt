package com.nendo.argosy.ui.common

import androidx.annotation.StringRes
import com.nendo.argosy.R
import java.time.Instant

enum class SaveConflictDirection { SERVER_OLDER, SERVER_NEWER, DIVERGED }

/**
 * The copy a save conflict favours: [SaveConflictDirection.SERVER_OLDER] when the server holds
 * the save this device last synced, [SaveConflictDirection.SERVER_NEWER] when an unrelated server
 * copy has the later timestamp, and [SaveConflictDirection.DIVERGED] for a later local save.
 */
fun saveConflictDirection(
    serverMatchesLastSync: Boolean,
    localTimestamp: Instant,
    serverTimestamp: Instant
): SaveConflictDirection = when {
    serverMatchesLastSync -> SaveConflictDirection.SERVER_OLDER
    serverTimestamp.isAfter(localTimestamp) -> SaveConflictDirection.SERVER_NEWER
    else -> SaveConflictDirection.DIVERGED
}

val SaveConflictDirection.localIsNewer: Boolean
    get() = this != SaveConflictDirection.SERVER_NEWER

@get:StringRes
val SaveConflictDirection.modalMessageRes: Int
    get() = when (this) {
        SaveConflictDirection.SERVER_OLDER -> R.string.ui_save_conflict_message_server_older
        SaveConflictDirection.SERVER_NEWER -> R.string.ui_save_conflict_message
        SaveConflictDirection.DIVERGED -> R.string.ui_save_conflict_message_diverged
    }

@get:StringRes
val SaveConflictDirection.overlayMessageRes: Int
    get() = when (this) {
        SaveConflictDirection.SERVER_OLDER -> R.string.ui_sync_overlay_post_session_message_server_older
        SaveConflictDirection.SERVER_NEWER -> R.string.ui_sync_overlay_post_session_message
        SaveConflictDirection.DIVERGED -> R.string.ui_sync_overlay_post_session_message_diverged
    }
