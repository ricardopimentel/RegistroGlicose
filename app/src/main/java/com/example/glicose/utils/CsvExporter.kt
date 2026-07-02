package com.example.glicose.utils

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import com.example.glicose.data.FoodItem
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

    // ── Alimentos Personalizados — Exportação CSV ─────────────────────────────
    /**
     * Cabeçalho: id,nome,medida,gramas,carboidratos,calorias
     * Campos com vírgulas são envolvidos em aspas duplas (RFC 4180).
     */
    fun exportCustomFoods(context: Context, foods: List<FoodItem>) {
        if (foods.isEmpty()) {
            Toast.makeText(context, "Nenhum alimento personalizado para exportar.", Toast.LENGTH_SHORT).show()
            return
        }
        val sb = StringBuilder()
        sb.append("id,nome,medida,gramas,carboidratos,calorias\n")
        foods.forEach { f ->
            sb.append("${f.id},${f.name.csvEscape()},${f.measure.csvEscape()},${f.grams},${f.carbs},${f.calories}\n")
        }
        saveAndShare(context, sb.toString(), "alimentos_personalizados")
    }

    // ── Alimentos Personalizados — Importação CSV ─────────────────────────────
    data class CustomFoodCsvResult(
        val foods: List<FoodItem>,
        val invalidLines: Int,
        val totalDataLines: Int
    )

    fun parseCustomFoodsCsv(context: Context, uri: android.net.Uri): CustomFoodCsvResult {
        val foods = mutableListOf<FoodItem>()
        var invalidLines = 0
        var totalDataLines = 0

        context.contentResolver.openInputStream(uri)?.bufferedReader()?.useLines { lines ->
            lines.drop(1).forEach { line ->
                if (line.isBlank()) return@forEach
                totalDataLines++
                try {
                    val parts = parseCsvLine(line)
                    // Aceita 6 colunas (com id) ou 5 colunas (sem id)
                    val (name, measure, grams, carbs, calories) = when (parts.size) {
                        6 -> listOf(parts[1], parts[2], parts[3], parts[4], parts[5])
                        5 -> listOf(parts[0], parts[1], parts[2], parts[3], parts[4])
                        else -> { invalidLines++; return@forEach }
                    }
                    val gramsF = grams.trim().toFloatOrNull() ?: run { invalidLines++; return@forEach }
                    val carbsF = carbs.trim().toFloatOrNull() ?: run { invalidLines++; return@forEach }
                    val calF   = calories.trim().toFloatOrNull() ?: run { invalidLines++; return@forEach }
                    if (name.isBlank()) { invalidLines++; return@forEach }
                    foods.add(
                        FoodItem(
                            id = (100000 + (1..900000).random()),
                            name = name.trim(),
                            measure = measure.trim().ifEmpty { "1 porcao" },
                            grams = gramsF,
                            carbs = carbsF,
                            calories = calF
                        )
                    )
                } catch (e: Exception) {
                    invalidLines++
                }
            }
        }

        return CustomFoodCsvResult(
            foods = foods,
            invalidLines = invalidLines,
            totalDataLines = totalDataLines
        )
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /** Wraps a field in double quotes if it contains comma, quote or newline (RFC 4180). */
    private fun String.csvEscape(): String {
        return if (contains(',') || contains('"') || contains('\n')) {
            "\"${replace("\"", "\"\"")}\""
        } else this
    }

    /** Minimal RFC-4180 CSV line parser (handles quoted fields). */
    private fun parseCsvLine(line: String): List<String> {
        val result = mutableListOf<String>()
        val current = StringBuilder()
        var inQuotes = false
        var i = 0
        while (i < line.length) {
            val c = line[i]
            when {
                c == '"' && inQuotes && i + 1 < line.length && line[i + 1] == '"' -> {
                    current.append('"'); i++ // escaped quote
                }
                c == '"' -> inQuotes = !inQuotes
                c == ',' && !inQuotes -> { result.add(current.toString()); current.clear() }
                else -> current.append(c)
            }
            i++
        }
        result.add(current.toString())
        return result
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

                // Open the file immediately
                val viewIntent = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(it, "text/csv")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }

                try {
                    context.startActivity(viewIntent)
                } catch (e: Exception) {
                    // Fallback to share
                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                        type = "text/csv"
                        putExtra(Intent.EXTRA_STREAM, it)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    context.startActivity(Intent.createChooser(shareIntent, "Abrir Arquivo"))
                }

                Toast.makeText(context, "Arquivo salvo em Downloads", Toast.LENGTH_LONG).show()
            }
        } catch (e: Exception) {
            Toast.makeText(context, "Erro ao processar arquivo: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
}
