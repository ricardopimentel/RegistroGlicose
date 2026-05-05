package com.example.glicose.notifications

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.example.glicose.data.Reminder
import java.util.Calendar

object ReminderScheduler {
    fun scheduleNotification(context: Context, reminder: Reminder) {
        if (!reminder.enabled) return

        val days = reminder.daysOfWeek.split(",").filter { it.isNotEmpty() }.mapNotNull { it.toIntOrNull() }
        if (days.isEmpty()) return

        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, ReminderReceiver::class.java).apply {
            putExtra("REMINDER_ID", reminder.id)
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context, reminder.id, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val now = Calendar.getInstance()
        val target = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, reminder.hour)
            set(Calendar.MINUTE, reminder.minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        // Find the next occurrence
        var found = false
        // Check today and the next 7 days
        for (i in 0..7) {
            val testTarget = (target.clone() as Calendar).apply { add(Calendar.DATE, i) }
            val dayOfWeek = testTarget.get(Calendar.DAY_OF_WEEK) - 1 // 0=Sun, 6=Sat
            
            if (days.contains(dayOfWeek) && testTarget.after(now)) {
                target.timeInMillis = testTarget.timeInMillis
                found = true
                break
            }
        }

        if (found) {
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                target.timeInMillis,
                pendingIntent
            )
        }
    }

    fun cancelNotification(context: Context, reminderId: Int) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, ReminderReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context, reminderId, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_NO_CREATE
        )
        pendingIntent?.let { alarmManager.cancel(it) }
    }
}
