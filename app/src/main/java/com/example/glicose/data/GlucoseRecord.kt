package com.example.glicose.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "glucose_records", primaryKeys = ["timestamp", "userId"])
data class GlucoseRecord(
    val timestamp: Long,
    val value: Float,
    val note: String,
    val userId: String,
    val carbs: Float? = null,
    val calories: Float? = null,
    val mealDetails: String? = null  // JSON array of meal items: [{"name":"...","multiplier":2.0},...]
)
