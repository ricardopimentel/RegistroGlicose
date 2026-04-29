package com.example.glicose.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "glucose_records", primaryKeys = ["timestamp", "userId"])
data class GlucoseRecord(
    val timestamp: Long,
    val value: Float,
    val note: String,
    val userId: String
)
