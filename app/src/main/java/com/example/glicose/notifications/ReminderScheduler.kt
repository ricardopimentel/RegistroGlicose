package com.example.glicose.notifications

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.example.glicose.data.Reminder
import java.util.Calendar

object ReminderScheduler {

    /**
     * Schedules the next alarm for the given reminder.
     *
     * - Finds the next day-of-week occurrence that is in the future.
     * - Uses setExactAndAllowWhileIdle() when the app holds the
     *   SCHEDULE_EXACT_ALARM / USE_EXACT_ALARM permission; falls back to
     *   setAndAllowWhileIdle() otherwise (fires within ~1 min of the target).
     */
    fun scheduleNotification(context: Context, reminder: Reminder) {
        if (!reminder.enabled) return

        val days = reminder.daysOfWeek
            .split(",")
            .filter { it.isNotEmpty() }
            .mapNotNull { it.trim().toIntOrNull() }
        if (days.isEmpty()) return

        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

        val intent = Intent(context, ReminderReceiver::class.java).apply {
            putExtra("REMINDER_ID", reminder.id)
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            reminder.id,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val now = Calendar.getInstance()

        // Build a base calendar set to today at the reminder's time
        val base = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, reminder.hour)
            set(Calendar.MINUTE, reminder.minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        // Search up to 8 days ahead to find the next valid weekday occurrence
        var triggerAt: Long? = null
        for (offset in 0..7) {
            val candidate = (base.clone() as Calendar).apply { add(Calendar.DATE, offset) }
            // Calendar.DAY_OF_WEEK: 1=Sun … 7=Sat  →  map to 0=Sun … 6=Sat
            val dow = candidate.get(Calendar.DAY_OF_WEEK) - 1
            if (days.contains(dow) && candidate.after(now)) {
                triggerAt = candidate.timeInMillis
                break
            }
        }

        triggerAt ?: return // no valid future day found

        val canScheduleExact = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            alarmManager.canScheduleExactAlarms()
        } else {
            true // permission is always granted below API 31
        }

        if (canScheduleExact) {
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                triggerAt,
                pendingIntent
            )
        } else {
            // Fallback: inexact alarm — fires within ~1 minute of the target
            alarmManager.setAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                triggerAt,
                pendingIntent
            )
        }
    }

    fun cancelNotification(context: Context, reminderId: Int) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, ReminderReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            reminderId,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_NO_CREATE
        )
        pendingIntent?.let { alarmManager.cancel(it) }
    }
}
