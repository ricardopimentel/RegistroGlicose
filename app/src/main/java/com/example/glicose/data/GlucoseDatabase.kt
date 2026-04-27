package com.example.glicose.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [GlucoseRecord::class, Reminder::class], version = 2, exportSchema = false)
abstract class GlucoseDatabase : RoomDatabase() {
    abstract fun glucoseDao(): GlucoseDao

    companion object {
        @Volatile
        private var INSTANCE: GlucoseDatabase? = null

        fun getDatabase(context: Context): GlucoseDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    GlucoseDatabase::class.java,
                    "glucose_database"
                )
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
