package com.example.glicose.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.OnConflictStrategy
import kotlinx.coroutines.flow.Flow

@Dao
interface GlucoseDao {
    @Query("SELECT * FROM glucose_records WHERE userId = :userId ORDER BY timestamp DESC")
    fun getAll(userId: String): Flow<List<GlucoseRecord>>

    @Query("SELECT * FROM glucose_records WHERE userId = :userId ORDER BY timestamp DESC")
    suspend fun getAllSync(userId: String): List<GlucoseRecord>

    @Insert
    suspend fun insert(record: GlucoseRecord)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIgnore(record: GlucoseRecord)

    @Delete
    suspend fun delete(record: GlucoseRecord)

    @Query("SELECT * FROM glucose_records WHERE userId = :userId ORDER BY timestamp DESC LIMIT 1")
    fun getLatest(userId: String): Flow<GlucoseRecord?>

    // Reminders
    @Query("SELECT * FROM reminders WHERE userId = :userId ORDER BY hour, minute")
    fun getAllReminders(userId: String): Flow<List<Reminder>>

    @Insert
    suspend fun insertReminder(reminder: Reminder)

    @Delete
    suspend fun deleteReminder(reminder: Reminder)

    @Query("UPDATE reminders SET enabled = :enabled WHERE id = :id")
    suspend fun updateReminderStatus(id: Int, enabled: Boolean)

    @Query("DELETE FROM glucose_records WHERE userId = :userId")
    suspend fun deleteAllForUser(userId: String)

    @Query("DELETE FROM reminders WHERE userId = :userId")
    suspend fun deleteAllRemindersForUser(userId: String)
}
