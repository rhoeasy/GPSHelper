package com.example.track

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        TrackSessionEntity::class,
        TrackPointEntity::class,
        TrackLapEntity::class,
        TrackAiReportEntity::class,
        AiAnalysisRecordEntity::class
    ],
    version = 5,
    exportSchema = false
)
abstract class TrackDatabase : RoomDatabase() {
    abstract fun trackDao(): TrackDao

    companion object {
        @Volatile
        private var INSTANCE: TrackDatabase? = null

        internal val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE track_sessions ADD COLUMN peakTotalG REAL NOT NULL DEFAULT 0.0")
                db.execSQL("ALTER TABLE track_sessions ADD COLUMN peakLateralG REAL NOT NULL DEFAULT 0.0")
                db.execSQL("ALTER TABLE track_points ADD COLUMN lateralG REAL NOT NULL DEFAULT 0.0")
                db.execSQL("ALTER TABLE track_points ADD COLUMN longitudinalG REAL NOT NULL DEFAULT 0.0")
                db.execSQL("ALTER TABLE track_points ADD COLUMN totalG REAL NOT NULL DEFAULT 0.0")
                db.execSQL("ALTER TABLE track_laps ADD COLUMN peakG REAL NOT NULL DEFAULT 0.0")
            }
        }

        internal val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `track_ai_reports` (
                        `sessionId` INTEGER NOT NULL,
                        `modelId` TEXT NOT NULL,
                        `reportText` TEXT NOT NULL,
                        `generatedAtMs` INTEGER NOT NULL,
                        PRIMARY KEY(`sessionId`),
                        FOREIGN KEY(`sessionId`) REFERENCES `track_sessions`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
            }
        }

        internal val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `ai_analysis_history` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `sessionId` INTEGER,
                        `sessionName` TEXT NOT NULL,
                        `activityMode` TEXT NOT NULL,
                        `modelId` TEXT NOT NULL,
                        `reportText` TEXT NOT NULL,
                        `summaryMetrics` TEXT NOT NULL,
                        `generatedAtMs` INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_ai_analysis_history_generatedAtMs` ON `ai_analysis_history` (`generatedAtMs`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_ai_analysis_history_sessionId` ON `ai_analysis_history` (`sessionId`)")
                // Migrate any existing cached per-track AI reports into the new history table
                db.execSQL(
                    """
                    INSERT INTO `ai_analysis_history` (`sessionId`, `sessionName`, `activityMode`, `modelId`, `reportText`, `summaryMetrics`, `generatedAtMs`)
                    SELECT r.`sessionId`, s.`name`, s.`mode`, r.`modelId`, r.`reportText`, '', r.`generatedAtMs`
                    FROM `track_ai_reports` r
                    INNER JOIN `track_sessions` s ON s.`id` = r.`sessionId`
                    """.trimIndent()
                )
            }
        }

        internal val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE track_points ADD COLUMN altitudeFromBarometer INTEGER")
                db.execSQL("ALTER TABLE track_points ADD COLUMN verticalVibrationG REAL")
                db.execSQL("ALTER TABLE track_sessions ADD COLUMN stepCount INTEGER")
            }
        }

        fun getInstance(context: Context): TrackDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    TrackDatabase::class.java,
                    "gps_hud_tracks.db"
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
