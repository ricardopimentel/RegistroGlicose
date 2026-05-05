package com.example.glicose.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [GlucoseRecord::class, Reminder::class], version = 7, exportSchema = false)
abstract class GlucoseDatabase : RoomDatabase() {
    abstract fun glucoseDao(): GlucoseDao

    companion object {
        @Volatile
        private var INSTANCE: GlucoseDatabase? = null

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE glucose_records ADD COLUMN userId TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE reminders ADD COLUMN userId TEXT NOT NULL DEFAULT ''")
            }
        }

        fun getDatabase(context: Context): GlucoseDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    GlucoseDatabase::class.java,
                    "glucose_database"
                )
                .addMigrations(MIGRATION_2_3)
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
