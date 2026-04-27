package com.example.glicose.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface GlucoseDao {
    @Query("SELECT * FROM glucose_records ORDER BY timestamp DESC")
    fun getAll(): Flow<List<GlucoseRecord>>

    @Insert
    suspend fun insert(record: GlucoseRecord)

    @Delete
    suspend fun delete(record: GlucoseRecord)

    @Query("SELECT * FROM glucose_records ORDER BY timestamp DESC LIMIT 1")
    fun getLatest(): Flow<GlucoseRecord?>

    // Reminders
    @Query("SELECT * FROM reminders ORDER BY hour, minute")
    fun getAllReminders(): Flow<List<Reminder>>

    @Insert
    suspend fun insertReminder(reminder: Reminder)

    @Delete
    suspend fun deleteReminder(reminder: Reminder)

    @Query("UPDATE reminders SET enabled = :enabled WHERE id = :id")
    suspend fun updateReminderStatus(id: Int, enabled: Boolean)
}
