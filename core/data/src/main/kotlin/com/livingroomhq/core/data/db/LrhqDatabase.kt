package com.livingroomhq.core.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [ChannelEntity::class, ProgramEntity::class, GuideChannelEntity::class],
    version = 7,
    exportSchema = false
)
abstract class LrhqDatabase : RoomDatabase() {

    abstract fun iptvDao(): IptvDao

    companion object {
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS guide_channels (
                        id TEXT NOT NULL PRIMARY KEY,
                        displayNames TEXT NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL("ALTER TABLE channels ADD COLUMN tvgName TEXT")
                db.execSQL("ALTER TABLE channels ADD COLUMN tvgChno TEXT")
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE channels ADD COLUMN tvgId TEXT")
            }
        }

        /** Backfill guide channel ids from persisted programmes after guide_channels shipped. */
        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    INSERT OR IGNORE INTO guide_channels (id, displayNames)
                    SELECT DISTINCT channelId, channelId FROM programs
                    WHERE channelId IS NOT NULL AND channelId != ''
                    """.trimIndent(),
                )
            }
        }

        /** Add composite indices for hot EPG window lookups. */
        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE INDEX IF NOT EXISTS index_programs_channelId_endMillis_startMillis
                    ON programs(channelId, endMillis, startMillis)
                    """.trimIndent(),
                )
                db.execSQL(
                    """
                    CREATE INDEX IF NOT EXISTS index_programs_endMillis_startMillis
                    ON programs(endMillis, startMillis)
                    """.trimIndent(),
                )
            }
        }

        /** Composite primary key on (channelId, startMillis) for keyed upsert. */
        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS programs_new (
                        channelId TEXT NOT NULL,
                        title TEXT NOT NULL,
                        description TEXT NOT NULL,
                        startMillis INTEGER NOT NULL,
                        endMillis INTEGER NOT NULL,
                        artworkUrl TEXT,
                        PRIMARY KEY(channelId, startMillis)
                    )
                    """.trimIndent(),
                )
                db.execSQL(
                    """
                    INSERT OR IGNORE INTO programs_new (channelId, title, description, startMillis, endMillis, artworkUrl)
                    SELECT channelId, title, description, startMillis, endMillis, artworkUrl FROM programs
                    """.trimIndent(),
                )
                db.execSQL("DROP TABLE programs")
                db.execSQL("ALTER TABLE programs_new RENAME TO programs")
                db.execSQL(
                    """
                    CREATE INDEX IF NOT EXISTS index_programs_channelId
                    ON programs(channelId)
                    """.trimIndent(),
                )
                db.execSQL(
                    """
                    CREATE INDEX IF NOT EXISTS index_programs_startMillis
                    ON programs(startMillis)
                    """.trimIndent(),
                )
                db.execSQL(
                    """
                    CREATE INDEX IF NOT EXISTS index_programs_endMillis
                    ON programs(endMillis)
                    """.trimIndent(),
                )
                db.execSQL(
                    """
                    CREATE INDEX IF NOT EXISTS index_programs_channelId_endMillis_startMillis
                    ON programs(channelId, endMillis, startMillis)
                    """.trimIndent(),
                )
                db.execSQL(
                    """
                    CREATE INDEX IF NOT EXISTS index_programs_endMillis_startMillis
                    ON programs(endMillis, startMillis)
                    """.trimIndent(),
                )
            }
        }

        /** Multi-playlist groundwork: scope channels/programs to a source id. */
        private val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    ALTER TABLE channels ADD COLUMN sourceId TEXT NOT NULL DEFAULT 'default'
                    """.trimIndent(),
                )
                db.execSQL(
                    """
                    ALTER TABLE programs ADD COLUMN sourceId TEXT NOT NULL DEFAULT 'default'
                    """.trimIndent(),
                )
                db.execSQL(
                    """
                    CREATE INDEX IF NOT EXISTS index_channels_sourceId ON channels(sourceId)
                    """.trimIndent(),
                )
                db.execSQL(
                    """
                    CREATE INDEX IF NOT EXISTS index_programs_sourceId ON programs(sourceId)
                    """.trimIndent(),
                )
            }
        }

        fun build(context: Context): LrhqDatabase =
            Room.databaseBuilder(
                context.applicationContext,
                LrhqDatabase::class.java,
                "lrhq_launcher.db"
            )
                .addMigrations(
                    MIGRATION_1_2,
                    MIGRATION_2_3,
                    MIGRATION_3_4,
                    MIGRATION_4_5,
                    MIGRATION_5_6,
                    MIGRATION_6_7,
                )
                .build()
    }
}
