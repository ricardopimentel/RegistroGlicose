package com.example.glicose.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.net.Uri
import com.example.glicose.data.GlucoseDatabase
import com.example.glicose.ui.MainActivity
import kotlinx.coroutines.*

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val reminderId = intent.getIntExtra("REMINDER_ID", -1)
        
        // Show notification immediately
        showNotification(context)

        if (reminderId != -1) {
            val pendingResult = goAsync()
            val database = GlucoseDatabase.getDatabase(context)
            
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val reminders = database.glucoseDao().getAllSyncReminders() // Need to add this method or similar
                    val reminder = reminders.find { it.id == reminderId }
                    
                    if (reminder != null && reminder.enabled) {
                        // Always reschedule for the next occurrence based on daysOfWeek
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

    private fun showNotification(context: Context) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channelId = "glucose_reminders_v2"

        val alarmSound: Uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "Lembretes Críticos", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Lembretes de glicose que funcionam como alarme"
                setSound(alarmSound, AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build())
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 1000, 500, 1000, 500, 1000)
            }
            notificationManager.createNotificationChannel(channel)
        }

        val activityIntent = Intent(context, MainActivity::class.java).apply {
            putExtra("OPEN_ADD_DIALOG", true)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            context, 0, activityIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("Hora da Medição")
            .setContentText("Está na hora de registrar sua glicose!")
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setSound(alarmSound)
            .setVibrate(longArrayOf(0, 1000, 500, 1000, 500, 1000))
            .setFullScreenIntent(pendingIntent, true)
            .setOngoing(true)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(System.currentTimeMillis().toInt(), notification)
    }
}
