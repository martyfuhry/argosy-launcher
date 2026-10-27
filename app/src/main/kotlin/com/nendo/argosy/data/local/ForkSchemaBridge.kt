package com.nendo.argosy.data.local

import android.util.Log
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import com.nendo.argosy.data.local.migrations.Migration_214_215
import com.nendo.argosy.data.local.migrations.Migration_215_216

/**
 * Rebuilds a fork 215 or fork 216 database without `gbColorStyle` and adds what upstream added at
 * that version, so upstream's chain continues from it. A database without the column is untouched.
 */
object ForkSchemaBridge {

    private const val TAG = "ForkSchemaBridge"
    private const val FORK_MARKER = "gbColorStyle"
    private const val TABLE = "platform_libretro_settings"
    internal const val REBUILT = "platform_libretro_settings_bridge"

    internal val UPSTREAM_COLUMNS = listOf(
        "id", "platformId", "shader", "shaderChain", "filter", "aspectRatio", "portraitPosition",
        "rotation", "overscanCrop", "blackFrameInsertion", "frame", "fastForwardEnabled",
        "fastForwardSpeed", "rewindEnabled", "skipDuplicateFrames", "lowLatencyAudio", "audioVolume",
        "analogAsDpad", "dpadAsAnalog", "rumbleEnabled", "rewindSpeed", "rewindBufferDuration", "vsync",
        "savePath", "statePath", "autoSaveState", "autoRestoreState", "hwCoreSaveStates",
        "frameOffsetX", "frameOffsetY", "frameZoom", "preferNewerServerSave", "graphicsApi", "gpuDriver"
    )

    internal const val CREATE_REBUILT =
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
            "`graphicsApi` TEXT, `gpuDriver` TEXT, " +
            "FOREIGN KEY(`platformId`) REFERENCES `platforms`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )"

    internal const val CREATE_PLATFORM_INDEX =
        "CREATE UNIQUE INDEX IF NOT EXISTS `index_${TABLE}_platformId` ON `$TABLE` (`platformId`)"

    private enum class Shape { FORK_215, FORK_216 }

    fun openHelperFactory(): SupportSQLiteOpenHelper.Factory = BridgingFactory(FrameworkSQLiteOpenHelperFactory())

    fun repair(db: SupportSQLiteDatabase): Boolean {
        val settings = columnsOf(db, TABLE)
        if (FORK_MARKER !in settings) return false
        val shape = shapeOf(db, settings)
        if (shape == null) {
            Log.w(TAG, "Fork column $FORK_MARKER found at version ${db.version} in an unrecognised shape, leaving it")
            return false
        }
        Log.i(TAG, "Fork schema $shape detected, bridging to upstream ${db.version}")
        db.beginTransaction()
        try {
            rebuildPlatformSettings(db)
            when (shape) {
                Shape.FORK_215 -> Migration_214_215.migrate(db)
                Shape.FORK_216 -> Migration_215_216.migrate(db)
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        return true
    }

    private fun shapeOf(db: SupportSQLiteDatabase, settings: Set<String>): Shape? {
        if (settings != UPSTREAM_COLUMNS.toSet() + FORK_MARKER) return null
        val hasSkipFullSync = "skipFullSync" in columnsOf(db, "platforms")
        val hasDeletions = columnsOf(db, "play_record_deletions").isNotEmpty()
        return when {
            db.version == 215 && !hasSkipFullSync && !hasDeletions -> Shape.FORK_215
            db.version == 216 && hasSkipFullSync && !hasDeletions -> Shape.FORK_216
            else -> null
        }
    }

    private fun rebuildPlatformSettings(db: SupportSQLiteDatabase) {
        val columns = UPSTREAM_COLUMNS.joinToString(", ") { "`$it`" }
        db.execSQL(CREATE_REBUILT)
        db.execSQL("INSERT INTO `$REBUILT` ($columns) SELECT $columns FROM `$TABLE`")
        db.execSQL("DROP TABLE `$TABLE`")
        db.execSQL("ALTER TABLE `$REBUILT` RENAME TO `$TABLE`")
        db.execSQL(CREATE_PLATFORM_INDEX)
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
