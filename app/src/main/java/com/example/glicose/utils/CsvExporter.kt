package com.example.glicose.utils

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.core.content.FileProvider
import com.example.glicose.data.GlucoseRecord
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object CsvExporter {
    private fun generateCsvContent(records: List<GlucoseRecord>): String {
        val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
        val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())

        val days = records.groupBy { dateFormat.format(Date(it.timestamp)) }
        val allTimes = records.map { timeFormat.format(Date(it.timestamp)) }.distinct().sorted()

        val csvBuilder = StringBuilder()
        csvBuilder.append("Data")
        allTimes.forEach { csvBuilder.append(",$it") }
        csvBuilder.append("\n")

        days.toSortedMap().forEach { (date, dayRecords) ->
            csvBuilder.append(date)
            val dayTimeMap = dayRecords.associate { timeFormat.format(Date(it.timestamp)) to it.value }
            allTimes.forEach { time ->
                csvBuilder.append(",${dayTimeMap[time] ?: ""}")
            }
            csvBuilder.append("\n")
        }
        return csvBuilder.toString()
    }

    fun exportAndHandle(context: Context, records: List<GlucoseRecord>) {
        if (records.isEmpty()) return
        val csvContent = generateCsvContent(records)
        val filename = "glicose_${SimpleDateFormat("yyyy-MM-dd_HH-mm", Locale.getDefault()).format(Date())}.csv"

        try {
            // 1. Salvar permanentemente em Downloads
            val contentValues = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
                put(MediaStore.MediaColumns.MIME_TYPE, "text/csv")
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
            }
            val resolver = context.contentResolver
            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
            
            uri?.let {
                resolver.openOutputStream(it)?.use { outputStream ->
                    outputStream.write(csvContent.toByteArray())
                }
                
                // 2. Compartilhar o arquivo (que também permite abrir em outros apps)
                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/csv"
                    putExtra(Intent.EXTRA_STREAM, it)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                context.startActivity(Intent.createChooser(shareIntent, "Exportar e Compartilhar"))
                
                Toast.makeText(context, "Arquivo salvo em Downloads e pronto para compartilhar", Toast.LENGTH_LONG).show()
            }
        } catch (e: Exception) {
            Toast.makeText(context, "Erro ao processar arquivo: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
}
