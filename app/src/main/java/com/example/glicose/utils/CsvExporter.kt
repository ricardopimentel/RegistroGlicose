package com.example.glicose.utils

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import com.example.glicose.data.GlucoseRecord
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object CsvExporter {

    // ── Formato legado (tabela pivotada por horário, para médicos) ────────────
    private fun generatePivotCsvContent(records: List<GlucoseRecord>): String {
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

    // ── Formato para backup/importação (uma linha por registro) ───────────────
    /**
     * Cabeçalho: valor_mgdl,nota,timestamp_ms,data_hora_legivel
     * Exemplo de linha: 120,Em jejum,1714320000000,28/04/2025 08:00
     *
     * Colunas obrigatórias para importação: valor_mgdl e timestamp_ms
     * A coluna data_hora_legivel é apenas informativa e é ignorada na importação.
     */
    fun generateBackupCsvContent(records: List<GlucoseRecord>): String {
        val dtFormat = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
        val sb = StringBuilder()
        sb.append("valor_mgdl,nota,timestamp_ms,data_hora_legivel\n")
        records.sortedBy { it.timestamp }.forEach { r ->
            val note = r.note.replace(",", ";") // Escapar vírgulas nas notas
            sb.append("${r.value.toInt()},${note},${r.timestamp},${dtFormat.format(Date(r.timestamp))}\n")
        }
        return sb.toString()
    }

    // ── Validador e parser para importação ────────────────────────────────────
    data class CsvParseResult(
        val validRecords: List<Triple<Float, String, Long>>, // (valor, nota, timestamp)
        val invalidLines: Int,
        val totalDataLines: Int
    )

    fun parseAndValidateBackupCsv(context: Context, uri: android.net.Uri): CsvParseResult {
        val valid = mutableListOf<Triple<Float, String, Long>>()
        var invalidLines = 0
        var totalDataLines = 0

        context.contentResolver.openInputStream(uri)?.bufferedReader()?.useLines { lines ->
            lines.drop(1).forEach { line ->
                if (line.isBlank()) return@forEach
                totalDataLines++
                val parts = line.split(",")
                val value = parts.getOrNull(0)?.trim()?.toFloatOrNull()
                val note = parts.getOrNull(1)?.trim() ?: ""
                val timestamp = parts.getOrNull(2)?.trim()?.toLongOrNull()
                if (value != null && timestamp != null && value > 0f) {
                    valid.add(Triple(value, note, timestamp))
                } else {
                    invalidLines++
                }
            }
        }

        return CsvParseResult(
            validRecords = valid,
            invalidLines = invalidLines,
            totalDataLines = totalDataLines
        )
    }

    // ── Exportação para compartilhar com médico (formato legado) ──────────────
    fun exportAndHandle(context: Context, records: List<GlucoseRecord>) {
        if (records.isEmpty()) return
        saveAndShare(context, generatePivotCsvContent(records), "glicose_relatorio")
    }

    // ── Exportação de backup (formato importável) ─────────────────────────────
    fun exportBackup(context: Context, records: List<GlucoseRecord>) {
        if (records.isEmpty()) return
        saveAndShare(context, generateBackupCsvContent(records), "glicose_backup")
    }

    private fun saveAndShare(context: Context, csvContent: String, prefix: String) {
        val filename = "${prefix}_${SimpleDateFormat("yyyy-MM-dd_HH-mm", Locale.getDefault()).format(Date())}.csv"
        try {
            val contentValues = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
                put(MediaStore.MediaColumns.MIME_TYPE, "text/csv")
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
            }
            val resolver = context.contentResolver
            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
            uri?.let {
                resolver.openOutputStream(it)?.use { out -> out.write(csvContent.toByteArray()) }
                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/csv"
                    putExtra(Intent.EXTRA_STREAM, it)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                context.startActivity(Intent.createChooser(shareIntent, "Exportar e Compartilhar"))
                Toast.makeText(context, "Arquivo salvo em Downloads", Toast.LENGTH_LONG).show()
            }
        } catch (e: Exception) {
            Toast.makeText(context, "Erro ao processar arquivo: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
}
