package com.example.glicose.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "reminders")
data class Reminder(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val hour: Int,
    val minute: Int,
    val enabled: Boolean = true,
    val userId: String = "",
    val frequency: String = "DAILY",
    val daysOfWeek: String = "0,1,2,3,4,5,6" // Default to all days (Sun-Sat)
)
