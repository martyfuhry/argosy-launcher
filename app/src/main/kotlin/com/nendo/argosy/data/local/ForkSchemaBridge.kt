package com.nendo.argosy.data.local

import android.util.Log
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory

/**
 * Brings a 2.19.4 fork database, upstream 203 content numbered 205 with two fork columns, to
 * upstream's real 205 shape before the registered chain runs, and leaves the version alone.
 */
object ForkSchemaBridge {

    private const val TAG = "ForkSchemaBridge"
    const val FORK_VERSION = 205
    private const val FORK_MARKER = "audioBufferFrames"
    private const val TABLE = "platform_libretro_settings"
    private const val REBUILT = "platform_libretro_settings_bridge"

    private val UPSTREAM_205_COLUMNS = listOf(
        "id", "platformId", "shader", "shaderChain", "filter", "aspectRatio", "portraitPosition",
        "rotation", "overscanCrop", "blackFrameInsertion", "frame", "fastForwardEnabled",
        "fastForwardSpeed", "rewindEnabled", "skipDuplicateFrames", "lowLatencyAudio", "audioVolume",
        "analogAsDpad", "dpadAsAnalog", "rumbleEnabled", "rewindSpeed", "rewindBufferDuration", "vsync",
        "savePath", "statePath", "autoSaveState", "autoRestoreState", "hwCoreSaveStates",
        "frameOffsetX", "frameOffsetY", "frameZoom", "preferNewerServerSave"
    )

    private const val CREATE_REBUILT =
        "CREATE TABLE IF NOT EXISTS `$REBUILT` (" +
            "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `platformId` INTEGER NOT NULL, " +
            "`shader` TEXT, `shaderChain` TEXT, `filter` TEXT, `aspectRatio` TEXT, `portraitPosition` TEXT, " +
            "`rotation` INTEGER, `overscanCrop` INTEGER, `blackFrameInsertion` INTEGER, `frame` TEXT, " +
            "`fastForwardEnabled` INTEGER, `fastForwardSpeed` INTEGER, `rewindEnabled` INTEGER, " +
            "`skipDuplicateFrames` INTEGER, `lowLatencyAudio` INTEGER, `audioVolume` INTEGER, " +
            "`analogAsDpad` INTEGER, `dpadAsAnalog` INTEGER, `rumbleEnabled` INTEGER, `rewindSpeed` INTEGER, " +
            "`rewindBufferDuration` INTEGER, `vsync` INTEGER, `savePath` TEXT, `statePath` TEXT, " +
            "`autoSaveState` INTEGER, `autoRestoreState` INTEGER, `hwCoreSaveStates` INTEGER, " +
            "`frameOffsetX` REAL, `frameOffsetY` REAL, `frameZoom` REAL, `preferNewerServerSave` INTEGER, " +
            "FOREIGN KEY(`platformId`) REFERENCES `platforms`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )"

    private const val CREATE_SIGIL_SYNC_STATE =
        "CREATE TABLE IF NOT EXISTS `sigil_sync_state` (" +
            "`ownerUserId` INTEGER NOT NULL, `platformSlug` TEXT NOT NULL, `layout` TEXT NOT NULL, " +
            "`root` TEXT NOT NULL, `state` BLOB NOT NULL, `unowned` TEXT NOT NULL, " +
            "`updatedAt` INTEGER NOT NULL, " +
            "PRIMARY KEY(`ownerUserId`, `platformSlug`, `layout`, `root`))"

    private const val CREATE_SNAPSHOT_CHANNELS =
        "CREATE TABLE IF NOT EXISTS `snapshot_channels` (" +
            "`ownerUserId` INTEGER NOT NULL, `gameId` INTEGER NOT NULL, `label` TEXT NOT NULL, " +
            "`channelId` TEXT NOT NULL, `romFileId` INTEGER NOT NULL, `heldSnapshotId` INTEGER, " +
            "`heldDigest` TEXT, `heldSaveHash` TEXT, `heldSaveIdentityHash` TEXT, `updatedAt` INTEGER NOT NULL, " +
            "PRIMARY KEY(`ownerUserId`, `gameId`, `label`))"

    private const val ADD_HARDCORE_FLAG =
        "ALTER TABLE `pending_conflicts` ADD COLUMN `isHardcoreDowngrade` INTEGER NOT NULL DEFAULT 0"

    private const val CLEAR_GB_SAVE_FEATURES =
        "UPDATE `games` SET `saveFeatures` = NULL WHERE `platformSlug` IN " +
            "('gb', 'gbc', 'gameboy', 'game_boy', 'game-boy', 'gameboycolor', 'game_boy_color', 'game-boy-color')"

    fun openHelperFactory(): SupportSQLiteOpenHelper.Factory = BridgingFactory(FrameworkSQLiteOpenHelperFactory())

    fun repair(db: SupportSQLiteDatabase): Boolean {
        if (db.version != FORK_VERSION) return false
        if (FORK_MARKER !in columnsOf(db, TABLE)) return false
        Log.i(TAG, "Fork schema at $FORK_VERSION detected, bridging to upstream $FORK_VERSION")
        db.beginTransaction()
        try {
            db.execSQL(CREATE_SIGIL_SYNC_STATE)
            db.execSQL(CREATE_SNAPSHOT_CHANNELS)
            if ("isHardcoreDowngrade" !in columnsOf(db, "pending_conflicts")) {
                db.execSQL(ADD_HARDCORE_FLAG)
            }
            db.execSQL(CLEAR_GB_SAVE_FEATURES)
            rebuildPlatformSettings(db)
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        return true
    }

    private fun rebuildPlatformSettings(db: SupportSQLiteDatabase) {
        val columns = UPSTREAM_205_COLUMNS.joinToString(", ") { "`$it`" }
        db.execSQL(CREATE_REBUILT)
        db.execSQL("INSERT INTO `$REBUILT` ($columns) SELECT $columns FROM `$TABLE`")
        db.execSQL("DROP TABLE `$TABLE`")
        db.execSQL("ALTER TABLE `$REBUILT` RENAME TO `$TABLE`")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_${TABLE}_platformId` ON `$TABLE` (`platformId`)")
    }

    private fun columnsOf(db: SupportSQLiteDatabase, table: String): Set<String> =
        db.query("PRAGMA table_info(`$table`)").use { cursor ->
            val name = cursor.getColumnIndex("name")
            generateSequence { if (cursor.moveToNext()) cursor.getString(name) else null }.toSet()
        }

    private class BridgingFactory(
        private val delegate: SupportSQLiteOpenHelper.Factory
    ) : SupportSQLiteOpenHelper.Factory {
        override fun create(configuration: SupportSQLiteOpenHelper.Configuration): SupportSQLiteOpenHelper {
            val bridged = SupportSQLiteOpenHelper.Configuration.builder(configuration.context)
                .name(configuration.name)
                .callback(BridgingCallback(configuration.callback))
                .noBackupDirectory(configuration.useNoBackupDirectory)
                .allowDataLossOnRecovery(configuration.allowDataLossOnRecovery)
                .build()
            return delegate.create(bridged)
        }
    }

    private class BridgingCallback(
        private val delegate: SupportSQLiteOpenHelper.Callback
    ) : SupportSQLiteOpenHelper.Callback(delegate.version) {
        override fun onConfigure(db: SupportSQLiteDatabase) {
            repair(db)
            delegate.onConfigure(db)
        }

        override fun onCreate(db: SupportSQLiteDatabase) = delegate.onCreate(db)

        override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) =
            delegate.onUpgrade(db, oldVersion, newVersion)

        override fun onDowngrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) =
            delegate.onDowngrade(db, oldVersion, newVersion)

        override fun onOpen(db: SupportSQLiteDatabase) = delegate.onOpen(db)

        override fun onCorruption(db: SupportSQLiteDatabase) = delegate.onCorruption(db)
    }
}
