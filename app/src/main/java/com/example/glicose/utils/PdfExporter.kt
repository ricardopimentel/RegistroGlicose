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

                // Open the file immediately
                val viewIntent = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(it, "application/pdf")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                
                try {
                    context.startActivity(viewIntent)
                } catch (e: Exception) {
                    // Fallback to share if no PDF viewer
                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                        type = "application/pdf"
                        putExtra(Intent.EXTRA_STREAM, it)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    context.startActivity(Intent.createChooser(shareIntent, "Abrir Relatório"))
                }
                
                Toast.makeText(context, "PDF salvo em Downloads", Toast.LENGTH_LONG).show()
            }
        } catch (e: Exception) {
            Toast.makeText(context, "Erro ao gerar PDF: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun buildPdf(records: List<GlucoseRecord>, targetMin: Float, targetMax: Float): PdfDocument {
        val sortedRecords = records.sortedByDescending { it.timestamp }
        val days = sortedRecords.groupBy { dateFormat.format(Date(it.timestamp)) }
        
        // Stats
        val avg = records.map { it.value }.average()
        val eA1c = (avg + 46.7) / 28.7
        val maxVal = records.maxOf { it.value }
        val minVal = records.minOf { it.value }

        val pdf = PdfDocument()
        val pageWidth = 595 // A4 width in points
        val pageHeight = 842 // A4 height in points
        
        var pageNumber = 1
        var pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create()
        var page = pdf.startPage(pageInfo)
        var canvas = page.canvas

        // ── Paints ────────────────────────────────────────────────────────────
        val titlePaint = Paint().apply { color = Color.parseColor("#6750A4"); textSize = 22f; isFakeBoldText = true; isAntiAlias = true }
        val subTitlePaint = Paint().apply { color = Color.parseColor("#757575"); textSize = 10f; isAntiAlias = true }
        val cardPaint = Paint().apply { color = Color.parseColor("#F3E5F5"); style = Paint.Style.FILL }
        val cardTextPaint = Paint().apply { color = Color.parseColor("#4F378B"); textSize = 10f; isFakeBoldText = true; isAntiAlias = true }
        val cardValuePaint = Paint().apply { color = Color.parseColor("#4F378B"); textSize = 16f; isFakeBoldText = true; isAntiAlias = true }
        val sectionPaint = Paint().apply { color = Color.parseColor("#212121"); textSize = 12f; isFakeBoldText = true; isAntiAlias = true }
        val cellPaint = Paint().apply { color = Color.parseColor("#424242"); textSize = 11f; isAntiAlias = true }
        val timePaint = Paint().apply { color = Color.parseColor("#757575"); textSize = 10f; isAntiAlias = true }
        val linePaint = Paint().apply { color = Color.parseColor("#EEEEEE"); strokeWidth = 1f }
        
        val highColor = Color.parseColor("#EF5350")
        val lowColor = Color.parseColor("#42A5F5")
        val normalColor = Color.parseColor("#66BB6A")

        var y = 50f
        
        // ── Header ────────────────────────────────────────────────────────────
        canvas.drawText("Relatório de Controle Glicêmico", 40f, y, titlePaint)
        y += 20f
        canvas.drawText("Gerado em ${SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date())}", 40f, y, subTitlePaint)
        
        y += 40f
        
        // ── Stats Cards ───────────────────────────────────────────────────────
        val cardW = 120f
        val cardH = 50f
        val cardGap = 12f
        
        fun drawStatCard(x: Float, label: String, value: String, unit: String) {
            canvas.drawRoundRect(x, y, x + cardW, y + cardH, 10f, 10f, cardPaint)
            canvas.drawText(label, x + 10f, y + 18f, cardTextPaint)
            canvas.drawText(value, x + 10f, y + 40f, cardValuePaint)
            val vWidth = cardValuePaint.measureText(value)
            canvas.drawText(unit, x + 10f + vWidth + 4f, y + 40f, subTitlePaint)
        }
        
        drawStatCard(40f, "Média", "${avg.toInt()}", "mg/dL")
        drawStatCard(40f + cardW + cardGap, "eA1c", String.format("%.1f", eA1c), "%")
        drawStatCard(40f + (cardW + cardGap) * 2, "Máxima", "${maxVal.toInt()}", "mg/dL")
        drawStatCard(40f + (cardW + cardGap) * 3, "Mínima", "${minVal.toInt()}", "mg/dL")
        
        y += cardH + 40f
        
        // ── Log ───────────────────────────────────────────────────────────────
        days.forEach { (date, dayRecords) ->
            // Check if we need a new page
            if (y > pageHeight - 100f) {
                pdf.finishPage(page)
                pageNumber++
                pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create()
                page = pdf.startPage(pageInfo)
                canvas = page.canvas
                y = 50f
            }
            
            canvas.drawText(date, 40f, y, sectionPaint)
            y += 10f
            canvas.drawLine(40f, y, pageWidth - 40f, y, linePaint)
            y += 25f
            
            dayRecords.forEach { record ->
                if (y > pageHeight - 50f) {
                    pdf.finishPage(page)
                    pageNumber++
                    pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create()
                    page = pdf.startPage(pageInfo)
                    canvas = page.canvas
                    y = 50f
                }
                
                val timeStr = timeFormat.format(Date(record.timestamp))
                canvas.drawText(timeStr, 40f, y, timePaint)
                
                val statusColor = when {
                    record.value > targetMax -> highColor
                    record.value < targetMin -> lowColor
                    else -> normalColor
                }
                
                val statusPaint = Paint().apply { color = statusColor; style = Paint.Style.FILL; isAntiAlias = true }
                canvas.drawCircle(95f, y - 4f, 4f, statusPaint)
                
                val valuePaint = Paint().apply { 
                    color = statusColor
                    textSize = 12f
                    isFakeBoldText = true
                    isAntiAlias = true
                }
                canvas.drawText("${record.value.toInt()} mg/dL", 110f, y, valuePaint)
                
                if (record.note.isNotEmpty()) {
                    canvas.drawText(record.note, 220f, y, cellPaint)
                }
                
                y += 25f
            }
            y += 15f
        }

        pdf.finishPage(page)
        return pdf
    }
}
