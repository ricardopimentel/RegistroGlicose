package com.example.glicose.widgets

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.example.glicose.data.GlucoseDatabase
import com.example.glicose.ui.MainActivity
import com.ricardo.glicose.R
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.*
import android.content.res.Configuration

class GlucoseWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        for (appWidgetId in appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == "com.example.glicose.UPDATE_WIDGET") {
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val ids = appWidgetManager.getAppWidgetIds(ComponentName(context, GlucoseWidgetProvider::class.java))
            onUpdate(context, appWidgetManager, ids)
        }
    }

    companion object {
        fun updateAppWidget(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int) {
            val views = RemoteViews(context.packageName, R.layout.glucose_widget)

            // Intent to open App
            val openAppIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
            val openAppPendingIntent = PendingIntent.getActivity(
                context, 1, openAppIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.main_circle_container, openAppPendingIntent)

            // Intent to open "Add Record" dialog
            val intent = Intent(context, MainActivity::class.java).apply {
                putExtra("OPEN_ADD_DIALOG", true)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
            val pendingIntent = PendingIntent.getActivity(
                context, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_btn_add, pendingIntent)

            // Fetch data from Room
            val auth = FirebaseAuth.getInstance()
            val userId = auth.currentUser?.uid ?: ""

            if (userId.isNotEmpty()) {
                CoroutineScope(Dispatchers.IO).launch {
                    val db = GlucoseDatabase.getDatabase(context)
                    val records = db.glucoseDao().getAllSync(userId)
                    
                    val prefs = context.getSharedPreferences("glucose_prefs", Context.MODE_PRIVATE)
                    val targetMin = prefs.getFloat("target_min", 70f)
                    val targetMax = prefs.getFloat("target_max", 140f)
                    
                    val appTheme = prefs.getInt("app_theme", 0)
                    // Apply Theme Colors to Widget
                    val isDark = when (appTheme) {
                        1 -> false
                        2 -> true
                        else -> (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
                    }

                    val textColorPrimary = if (isDark) android.graphics.Color.parseColor("#E6E1E5") else android.graphics.Color.parseColor("#49454F")
                    val textColorSecondary = if (isDark) android.graphics.Color.parseColor("#938F99") else android.graphics.Color.parseColor("#757575")
                    val iconTint = if (isDark) android.graphics.Color.parseColor("#D0BCFF") else android.graphics.Color.parseColor("#6750A4")
                    
                    // Swap backgrounds
                    views.setInt(R.id.main_circle_container, "setBackgroundResource", 
                        if (isDark) R.drawable.circular_widget_background_dark else R.drawable.circular_widget_background)
                    
                    views.setInt(R.id.widget_btn_add, "setBackgroundResource", 
                        if (isDark) R.drawable.rounded_add_button_dark else R.drawable.rounded_add_button)
                    
                    // Tint the Add icon
                    views.setInt(R.id.widget_btn_add_icon, "setColorFilter", iconTint)
                    
                    // Update all text colors based on theme
                    views.setTextColor(R.id.widget_avg, textColorPrimary)
                    views.setTextColor(R.id.widget_a1c, textColorPrimary)
                    views.setTextColor(R.id.widget_last_time, textColorPrimary)
                    views.setTextColor(R.id.widget_range, textColorPrimary)
                    
                    views.setTextColor(R.id.widget_label_avg, textColorSecondary)
                    views.setTextColor(R.id.widget_label_a1c, textColorSecondary)
                    views.setTextColor(R.id.widget_label_range, textColorSecondary)

                    if (records.isNotEmpty()) {
                        val avg = records.map { it.value }.average()
                        val a1c = (avg + 46.7) / 28.7
                        val max = records.maxOf { it.value }
                        val min = records.minOf { it.value }
                        val latest = records.first()

                        val timeFormat = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault())
                        
                        // Color coding
                        val color = when {
                            latest.value < targetMin -> android.graphics.Color.parseColor("#0077C2") // Low - Blue
                            latest.value > targetMax -> android.graphics.Color.parseColor("#B00020") // High - Red
                            else -> android.graphics.Color.parseColor("#6750A4") // Normal - Purple
                        }

                        views.setTextViewText(R.id.widget_last_value, String.format("%.0f", latest.value))
                        views.setTextColor(R.id.widget_last_value, color)
                        views.setTextViewText(R.id.widget_last_time, "Hoje, ${timeFormat.format(java.util.Date(latest.timestamp))}")
                        
                        views.setTextViewText(R.id.widget_avg, String.format("%.0f", avg))
                        views.setTextViewText(R.id.widget_a1c, String.format("%.1f%%", a1c))
                        views.setTextViewText(R.id.widget_range, String.format("%.0f/%.0f", min, max))
                    } else {
                        views.setTextViewText(R.id.widget_last_value, "--")
                        views.setTextViewText(R.id.widget_last_time, "Sem dados")
                        views.setTextViewText(R.id.widget_avg, "--")
                        views.setTextViewText(R.id.widget_a1c, "--")
                        views.setTextViewText(R.id.widget_range, "--/--")
                    }
                    appWidgetManager.updateAppWidget(appWidgetId, views)
                }
            } else {
                views.setTextViewText(R.id.widget_last_value, "?")
                views.setTextViewText(R.id.widget_last_time, "Fazer Login")
                views.setTextViewText(R.id.widget_avg, "--")
                views.setTextViewText(R.id.widget_a1c, "--")
                views.setTextViewText(R.id.widget_range, "--/--")
                appWidgetManager.updateAppWidget(appWidgetId, views)
            }
        }
    }
}
