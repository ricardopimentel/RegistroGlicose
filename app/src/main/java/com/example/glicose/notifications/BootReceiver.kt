package com.example.glicose.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.glicose.data.GlucoseDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Receives BOOT_COMPLETED and LOCKED_BOOT_COMPLETED to reschedule all
 * enabled reminders after the device restarts.
 *
 * Android's AlarmManager clears all alarms on reboot, so this receiver
 * is the only way to restore them automatically.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action != Intent.ACTION_BOOT_COMPLETED &&
            action != "android.intent.action.LOCKED_BOOT_COMPLETED"
        ) return

        val pendingResult = goAsync()
        val database = GlucoseDatabase.getDatabase(context)

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val reminders = database.glucoseDao().getAllSyncReminders()
                reminders.filter { it.enabled }.forEach { reminder ->
                    ReminderScheduler.scheduleNotification(context, reminder)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                pendingResult.finish()
            }
        }
    }
}
