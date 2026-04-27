package com.example.glicose.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.glicose.data.GlucoseDatabase
import com.example.glicose.data.GlucoseRecord
import com.example.glicose.data.Reminder
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class GlucoseViewModel(application: Application) : AndroidViewModel(application) {
    private val dao = GlucoseDatabase.getDatabase(application).glucoseDao()

    val allRecords: StateFlow<List<GlucoseRecord>> = dao.getAll().stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val latestRecord: StateFlow<GlucoseRecord?> = dao.getLatest().stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = null
    )

    val allReminders: StateFlow<List<Reminder>> = dao.getAllReminders().stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    fun addRecord(value: Float, note: String) {
        viewModelScope.launch {
            dao.insert(GlucoseRecord(value = value, note = note))
        }
    }

    fun deleteRecord(record: GlucoseRecord) {
        viewModelScope.launch {
            dao.delete(record)
        }
    }

    fun updateRecord(record: GlucoseRecord, value: Float, note: String) {
        viewModelScope.launch {
            // Room update
            dao.delete(record)
            dao.insert(GlucoseRecord(value = value, note = note, timestamp = record.timestamp))
        }
    }

    fun addReminder(hour: Int, minute: Int) {
        viewModelScope.launch {
            dao.insertReminder(Reminder(hour = hour, minute = minute))
        }
    }

    fun deleteReminder(reminder: Reminder) {
        viewModelScope.launch {
            dao.deleteReminder(reminder)
        }
    }

    fun toggleReminder(reminder: Reminder) {
        viewModelScope.launch {
            dao.updateReminderStatus(reminder.id, !reminder.enabled)
        }
    }

    fun updateReminder(reminder: Reminder, hour: Int, minute: Int) {
        viewModelScope.launch {
            // Room update or just delete and insert
            dao.deleteReminder(reminder)
            dao.insertReminder(Reminder(hour = hour, minute = minute, enabled = reminder.enabled))
        }
    }
}
