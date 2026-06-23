package com.livingroomhq.core.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [ChannelEntity::class, ProgramEntity::class, GuideChannelEntity::class],
    version = 4,
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

        fun build(context: Context): LrhqDatabase =
            Room.databaseBuilder(
                context.applicationContext,
                LrhqDatabase::class.java,
                "lrhq_launcher.db"
            )
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
                .build()
    }
}
