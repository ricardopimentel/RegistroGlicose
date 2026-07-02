package com.example.glicose.utils

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import com.example.glicose.data.FoodItem
import com.example.glicose.data.GlucoseDatabase
import com.example.glicose.data.GlucoseRecord
import com.example.glicose.data.Reminder
import com.example.glicose.notifications.ReminderScheduler
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object JsonBackupManager {

    /**
     * Generates a complete JSON backup representing the entire app state for the user
     * and triggers the system share/save dialog.
     */
    suspend fun exportCompleteBackup(context: Context, userId: String) {
        try {
            val db = GlucoseDatabase.getDatabase(context)
            val dao = db.glucoseDao()

            // 1. Fetch Room data
            val records = dao.getAllSync(userId)
            val reminders = dao.getAllSyncReminders().filter { it.userId == userId }

            // 2. Fetch SharedPreferences data
            val prefs = context.getSharedPreferences("glucose_prefs", Context.MODE_PRIVATE)
            val targetMin = prefs.getFloat("target_min", 70f)
            val targetMax = prefs.getFloat("target_max", 140f)
            val carbRatio = prefs.getFloat("carb_ratio", 0f)
            val mealCarbRatiosJson = prefs.getString("meal_carb_ratios", "{}")
            val customFoodsJson = prefs.getString("custom_foods", "[]")

            // 3. Assemble JSON structure
            val backupJson = JSONObject().apply {
                put("backupVersion", 1)
                put("backupTimestamp", System.currentTimeMillis())
                put("userId", userId)

                // Settings
                put("settings", JSONObject().apply {
                    put("targetMin", targetMin.toDouble())
                    put("targetMax", targetMax.toDouble())
                    put("carbRatio", carbRatio.toDouble())
                    put("mealCarbRatios", JSONObject(mealCarbRatiosJson))
                })

                // Custom Foods
                put("customFoods", JSONArray(customFoodsJson))

                // Reminders
                val remindersArray = JSONArray()
                reminders.forEach { r ->
                    remindersArray.put(JSONObject().apply {
                        put("hour", r.hour)
                        put("minute", r.minute)
                        put("enabled", r.enabled)
                        put("frequency", r.frequency)
                        put("daysOfWeek", r.daysOfWeek)
                    })
                }
                put("reminders", remindersArray)

                // Glucose Records
                val recordsArray = JSONArray()
                records.forEach { rec ->
                    recordsArray.put(JSONObject().apply {
                        put("timestamp", rec.timestamp)
                        put("value", rec.value.toDouble())
                        put("note", rec.note)
                        put("carbs", rec.carbs?.toDouble() ?: JSONObject.NULL)
                        put("calories", rec.calories?.toDouble() ?: JSONObject.NULL)
                        put("mealDetails", rec.mealDetails ?: JSONObject.NULL)
                    })
                }
                put("glucoseRecords", recordsArray)
            }

            // 4. Save and share the JSON file
            saveAndShareBackupFile(context, backupJson.toString())
        } catch (e: Exception) {
            Toast.makeText(context, "Erro ao gerar backup: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    /**
     * Parses and restores app state from a complete backup JSON file.
     * Overwrites settings and custom foods, inserts glucose records, and updates reminders.
     */
    suspend fun importCompleteBackup(
        context: Context,
        userId: String,
        uri: Uri,
        onProgress: (String) -> Unit
    ): Boolean {
        return try {
            onProgress("Lendo arquivo de backup...")
            val jsonString = context.contentResolver.openInputStream(uri)?.use { 
                it.bufferedReader().readText() 
            } ?: return false

            val backup = JSONObject(jsonString)
            val version = backup.optInt("backupVersion", 1)
            if (version > 1) {
                Toast.makeText(context, "Versão do backup não suportada.", Toast.LENGTH_LONG).show()
                return false
            }

            val db = GlucoseDatabase.getDatabase(context)
            val dao = db.glucoseDao()

            // 1. Restore Settings (SharedPreferences)
            onProgress("Restaurando configurações...")
            val settings = backup.optJSONObject("settings")
            val prefs = context.getSharedPreferences("glucose_prefs", Context.MODE_PRIVATE).edit()
            if (settings != null) {
                prefs.putFloat("target_min", settings.optDouble("targetMin", 70.0).toFloat())
                prefs.putFloat("target_max", settings.optDouble("targetMax", 140.0).toFloat())
                prefs.putFloat("carb_ratio", settings.optDouble("carbRatio", 0.0).toFloat())
                
                val mealRatios = settings.optJSONObject("mealCarbRatios")
                if (mealRatios != null) {
                    prefs.putString("meal_carb_ratios", mealRatios.toString())
                }
            }

            // 2. Restore Custom Foods
            onProgress("Restaurando alimentos personalizados...")
            val customFoods = backup.optJSONArray("customFoods")
            if (customFoods != null) {
                prefs.putString("custom_foods", customFoods.toString())
            }
            prefs.apply()

            // 3. Restore Glucose/Meal Records (Room insertIgnore)
            onProgress("Restaurando registros de glicose...")
            val recordsArray = backup.optJSONArray("glucoseRecords")
            if (recordsArray != null && recordsArray.length() > 0) {
                val recordsList = mutableListOf<GlucoseRecord>()
                for (i in 0 until recordsArray.length()) {
                    val obj = recordsArray.getJSONObject(i)
                    recordsList.add(
                        GlucoseRecord(
                            timestamp = obj.getLong("timestamp"),
                            value = obj.getDouble("value").toFloat(),
                            note = obj.optString("note", ""),
                            userId = userId,
                            carbs = if (obj.isNull("carbs")) null else obj.getDouble("carbs").toFloat(),
                            calories = if (obj.isNull("calories")) null else obj.getDouble("calories").toFloat(),
                            mealDetails = if (obj.isNull("mealDetails")) null else obj.getString("mealDetails")
                        )
                    )
                }
                dao.insertIgnoreAll(recordsList)
            }

            // 4. Restore Reminders
            onProgress("Atualizando lembretes...")
            val remindersArray = backup.optJSONArray("reminders")
            if (remindersArray != null) {
                // Cancel existing alarms
                val oldReminders = dao.getAllSyncReminders().filter { it.userId == userId }
                oldReminders.forEach { ReminderScheduler.cancelNotification(context, it.id) }
                
                // Clear old reminders from Room database
                dao.deleteAllRemindersForUser(userId)

                // Insert and schedule new reminders
                for (i in 0 until remindersArray.length()) {
                    val obj = remindersArray.getJSONObject(i)
                    val reminder = Reminder(
                        hour = obj.getInt("hour"),
                        minute = obj.getInt("minute"),
                        enabled = obj.optBoolean("enabled", true),
                        userId = userId,
                        frequency = obj.optString("frequency", "DAILY"),
                        daysOfWeek = obj.optString("daysOfWeek", "0,1,2,3,4,5,6")
                    )
                    val newId = dao.insertReminder(reminder)
                    if (reminder.enabled) {
                        ReminderScheduler.scheduleNotification(context, reminder.copy(id = newId.toInt()))
                    }
                }
            }

            true
        } catch (e: Exception) {
            android.util.Log.e("JsonBackupManager", "Restore error", e)
            false
        }
    }

    private fun saveAndShareBackupFile(context: Context, jsonContent: String) {
        val dateStr = SimpleDateFormat("yyyy-MM-dd_HH-mm", Locale.getDefault()).format(Date())
        val filename = "glicose_backup_completo_$dateStr.json"
        try {
            val contentValues = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
                put(MediaStore.MediaColumns.MIME_TYPE, "application/json")
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
            }
            val resolver = context.contentResolver
            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
            uri?.let {
                resolver.openOutputStream(it)?.use { out -> out.write(jsonContent.toByteArray()) }

                // Share file sheet chooser
                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "application/json"
                    putExtra(Intent.EXTRA_STREAM, it)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                context.startActivity(Intent.createChooser(shareIntent, "Salvar/Compartilhar Backup"))

                Toast.makeText(context, "Backup salvo em Downloads", Toast.LENGTH_LONG).show()
            }
        } catch (e: Exception) {
            Toast.makeText(context, "Erro ao exportar arquivo: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
}
