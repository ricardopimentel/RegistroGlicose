package com.example.glicose.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "glucose_records")
data class GlucoseRecord(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val value: Float,
    val note: String,
    val timestamp: Long = System.currentTimeMillis()
)
