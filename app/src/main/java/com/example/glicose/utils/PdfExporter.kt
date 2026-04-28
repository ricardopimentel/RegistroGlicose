package com.example.glicose.utils

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import com.example.glicose.data.GlucoseRecord
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object PdfExporter {

    private val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
    private val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())

    fun exportAndShare(context: Context, records: List<GlucoseRecord>, targetMin: Float = 70f, targetMax: Float = 140f) {
        if (records.isEmpty()) {
            Toast.makeText(context, "Nenhum dado para exportar", Toast.LENGTH_SHORT).show()
            return
        }

        try {
            val pdf = buildPdf(records, targetMin, targetMax)
            val filename = "glicose_relatorio_${SimpleDateFormat("yyyy-MM-dd_HH-mm", Locale.getDefault()).format(Date())}.pdf"

            val contentValues = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
                put(MediaStore.MediaColumns.MIME_TYPE, "application/pdf")
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
            }

            val resolver = context.contentResolver
            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
            uri?.let {
                resolver.openOutputStream(it)?.use { out -> pdf.writeTo(out) }
                pdf.close()

                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "application/pdf"
                    putExtra(Intent.EXTRA_STREAM, it)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                context.startActivity(Intent.createChooser(shareIntent, "Exportar Relatório PDF"))
                Toast.makeText(context, "PDF salvo em Downloads", Toast.LENGTH_LONG).show()
            }
        } catch (e: Exception) {
            Toast.makeText(context, "Erro ao gerar PDF: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun buildPdf(records: List<GlucoseRecord>, targetMin: Float, targetMax: Float): PdfDocument {
        // ── Build pivot data ──────────────────────────────────────────────────
        val days = records
            .groupBy { dateFormat.format(Date(it.timestamp)) }
            .toSortedMap()
        val allTimes = records
            .map { timeFormat.format(Date(it.timestamp)) }
            .distinct()
            .sorted()

        // ── Page dimensions (A4 landscape-ish) ───────────────────────────────
        val colWidth = 90f
        val rowHeight = 28f
        val marginLeft = 20f
        val marginTop = 80f
        val dateColWidth = 110f

        val pageWidth = (dateColWidth + colWidth * allTimes.size + marginLeft * 2).toInt()
            .coerceAtLeast(595) // at least A4 width
        val pageHeight = (marginTop + rowHeight * (days.size + 2) + 60f).toInt()
            .coerceAtLeast(842)

        val pdf = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, 1).create()
        val page = pdf.startPage(pageInfo)
        val canvas: Canvas = page.canvas

        // ── Paints ────────────────────────────────────────────────────────────
        val titlePaint = Paint().apply {
            color = Color.parseColor("#6750A4") // Purple
            textSize = 18f
            isFakeBoldText = true
            isAntiAlias = true
        }
        val headerPaint = Paint().apply {
            color = Color.parseColor("#4F378B") // Darker Purple
            textSize = 11f
            isFakeBoldText = true
            isAntiAlias = true
        }
        val headerBgPaint = Paint().apply {
            color = Color.parseColor("#F3E5F5") // Light Purple background
            style = Paint.Style.FILL
        }
        val cellPaint = Paint().apply {
            color = Color.parseColor("#212121")
            textSize = 11f
            isAntiAlias = true
        }
        val altRowPaint = Paint().apply {
            color = Color.parseColor("#F5F5F5")
            style = Paint.Style.FILL
        }
        val linePaint = Paint().apply {
            color = Color.parseColor("#BDBDBD")
            strokeWidth = 0.5f
            style = Paint.Style.STROKE
        }
        val highPaint = Paint().apply {
            color = Color.parseColor("#C62828")
            textSize = 11f
            isFakeBoldText = true
            isAntiAlias = true
        }
        val lowPaint = Paint().apply {
            color = Color.parseColor("#1565C0")
            textSize = 11f
            isFakeBoldText = true
            isAntiAlias = true
        }

        // ── Title ─────────────────────────────────────────────────────────────
        canvas.drawText("Relatório de Glicemia", marginLeft, 40f, titlePaint)
        val subtitle = Paint().apply {
            color = Color.parseColor("#616161"); textSize = 10f; isAntiAlias = true
        }
        canvas.drawText(
            "Gerado em ${SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date())}  |  ${records.size} registros",
            marginLeft, 58f, subtitle
        )

        // ── Header row ────────────────────────────────────────────────────────
        val headerY = marginTop
        canvas.drawRect(marginLeft, headerY, pageWidth - marginLeft, headerY + rowHeight, headerBgPaint)
        canvas.drawText("Data", marginLeft + 4f, headerY + rowHeight - 8f, headerPaint)
        allTimes.forEachIndexed { i, time ->
            val x = marginLeft + dateColWidth + i * colWidth
            canvas.drawText(time, x + 4f, headerY + rowHeight - 8f, headerPaint)
        }

        // ── Data rows ─────────────────────────────────────────────────────────
        days.entries.forEachIndexed { rowIdx, (date, dayRecords) ->
            val y = headerY + rowHeight * (rowIdx + 1)
            val dayTimeMap = dayRecords.associate { timeFormat.format(Date(it.timestamp)) to it.value }

            // Alternate row background
            if (rowIdx % 2 == 1) {
                canvas.drawRect(marginLeft, y, pageWidth - marginLeft, y + rowHeight, altRowPaint)
            }

            // Date cell
            canvas.drawText(date, marginLeft + 4f, y + rowHeight - 8f, cellPaint)

            // Value cells
            allTimes.forEachIndexed { i, time ->
                val x = marginLeft + dateColWidth + i * colWidth
                val value = dayTimeMap[time]
                if (value != null) {
                    val paint = when {
                        value > targetMax -> highPaint
                        value < targetMin -> lowPaint
                        else -> cellPaint
                    }
                    canvas.drawText("${value.toInt()}", x + 4f, y + rowHeight - 8f, paint)
                }
            }

            // Row separator
            canvas.drawLine(marginLeft, y + rowHeight, pageWidth - marginLeft.toFloat(), y + rowHeight, linePaint)
        }

        // ── Column grid lines ─────────────────────────────────────────────────
        val tableBottom = headerY + rowHeight * (days.size + 1)
        // Date col separator
        canvas.drawLine(marginLeft + dateColWidth, headerY, marginLeft + dateColWidth, tableBottom, linePaint)
        allTimes.forEachIndexed { i, _ ->
            val x = marginLeft + dateColWidth + (i + 1) * colWidth
            canvas.drawLine(x, headerY, x, tableBottom, linePaint)
        }
        // Outer border
        canvas.drawRect(marginLeft, headerY, pageWidth - marginLeft, tableBottom, linePaint)

        // ── Legend ────────────────────────────────────────────────────────────
        val legendY = tableBottom + 20f
        val legendPaint = Paint().apply { color = Color.parseColor("#757575"); textSize = 9f; isAntiAlias = true }
        canvas.drawText("■ Vermelho: > ${targetMax.toInt()} mg/dL (Alto)    ■ Azul: < ${targetMin.toInt()} mg/dL (Baixo)    ■ Preto: ${targetMin.toInt()}-${targetMax.toInt()} mg/dL (Alvo)", marginLeft, legendY, legendPaint)

        pdf.finishPage(page)
        return pdf
    }
}
