package com.example.pdfscan

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import java.io.File
import java.io.FileOutputStream

object PdfWriter {
    fun writeTableToPdf(context: Context, grid: TableGrid, pageWidth: Int = 595, pageHeight: Int = 842, outputFile: File): File {
        val document = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, 1).create()
        val page = document.startPage(pageInfo)
        val canvas: Canvas = page.canvas
        canvas.drawColor(Color.WHITE)
        val regularTypeface = try { Typeface.createFromAsset(context.assets, "font.ttf") } catch (e: Exception) { Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL) }
        val boldTypeface = try { Typeface.createFromAsset(context.assets, "font_bold.ttf") } catch (e: Exception) { Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD) }
        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; color = Color.DKGRAY; strokeWidth = 1.2f }
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 10.5f }
        val minLeft = grid.cells.minOfOrNull { it.boundingBox.left }?.coerceAtLeast(0) ?: 0
        val minTop = grid.cells.minOfOrNull { it.boundingBox.top }?.coerceAtLeast(0) ?: 0
        val maxRight = grid.cells.maxOfOrNull { it.boundingBox.right }?.coerceAtLeast(minLeft + 10) ?: pageWidth
        val maxBottom = grid.cells.maxOfOrNull { it.boundingBox.bottom }?.coerceAtLeast(minTop + 10) ?: pageHeight
        val contentW = (maxRight - minLeft).coerceAtLeast(1)
        val contentH = (maxBottom - minTop).coerceAtLeast(1)
        val scaleX = (pageWidth - 48f) / contentW.toFloat()
        val scaleY = ((pageHeight - 80f) / contentH.toFloat()).coerceAtMost(scaleX * 1.4f)
        val offsetX = 24f; val offsetY = 32f
        for (cell in grid.cells) {
            val left = offsetX + (cell.boundingBox.left - minLeft) * scaleX
            val top = offsetY + (cell.boundingBox.top - minTop) * scaleY
            val right = offsetX + (cell.boundingBox.right - minLeft) * scaleX
            val bottom = offsetY + (cell.boundingBox.bottom - minTop) * scaleY
            bgPaint.color = cell.bgColor
            canvas.drawRect(left, top, right, bottom, bgPaint)
            if (!grid.isBorderless) {
                canvas.drawRect(left, top, right, bottom, borderPaint)
            } else {
                borderPaint.color = Color.LTGRAY
                canvas.drawLine(left, bottom, right, bottom, borderPaint)
            }
            val padding = 5f
            var currentY = top + padding + textPaint.textSize
            val lineSpacing = textPaint.textSize + 4f
            for (line in cell.lines) {
                if (currentY > bottom + 2f) break
                textPaint.color = line.textColor
                textPaint.typeface = if (line.isBold) boldTypeface else regularTypeface
                textPaint.isFakeBoldText = line.isBold
                canvas.drawText(line.text, left + padding, currentY, textPaint)
                currentY += lineSpacing
            }
        }
        document.finishPage(page)
        FileOutputStream(outputFile).use { out -> document.writeTo(out) }
        document.close()
        return outputFile
    }
}
