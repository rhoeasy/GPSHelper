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
        TrackLapEntity::class
    ],
    version = 2,
    exportSchema = false
)
abstract class TrackDatabase : RoomDatabase() {
    abstract fun trackDao(): TrackDao

    companion object {
        @Volatile
        private var INSTANCE: TrackDatabase? = null

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE track_sessions ADD COLUMN peakTotalG REAL NOT NULL DEFAULT 0.0")
                db.execSQL("ALTER TABLE track_sessions ADD COLUMN peakLateralG REAL NOT NULL DEFAULT 0.0")
                db.execSQL("ALTER TABLE track_points ADD COLUMN lateralG REAL NOT NULL DEFAULT 0.0")
                db.execSQL("ALTER TABLE track_points ADD COLUMN longitudinalG REAL NOT NULL DEFAULT 0.0")
                db.execSQL("ALTER TABLE track_points ADD COLUMN totalG REAL NOT NULL DEFAULT 0.0")
                db.execSQL("ALTER TABLE track_laps ADD COLUMN peakG REAL NOT NULL DEFAULT 0.0")
            }
        }

        fun getInstance(context: Context): TrackDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    TrackDatabase::class.java,
                    "gps_hud_tracks.db"
                )
                    .addMigrations(MIGRATION_1_2)
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
