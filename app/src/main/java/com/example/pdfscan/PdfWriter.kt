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
        val regularTypeface = try { Typeface.createFromAsset(context.assets, "font.ttf") } catch (e: Exception) { Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL) }
        val boldTypeface = try { Typeface.createFromAsset(context.assets, "font_bold.ttf") } catch (e: Exception) { Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD) }
        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; color = Color.DKGRAY; strokeWidth = 1.2f }
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 11f }
        val maxRight = grid.cells.maxOfOrNull { it.boundingBox.right }?.coerceAtLeast(1) ?: pageWidth
        val maxBottom = grid.cells.maxOfOrNull { it.boundingBox.bottom }?.coerceAtLeast(1) ?: pageHeight
        val scaleX = (pageWidth - 40f) / maxRight.toFloat()
        val scaleY = (pageHeight - 40f) / maxBottom.toFloat()
        val offsetX = 20f; val offsetY = 20f
        for (cell in grid.cells) {
            val left = offsetX + cell.boundingBox.left * scaleX
            val top = offsetY + cell.boundingBox.top * scaleY
            val right = offsetX + cell.boundingBox.right * scaleX
            val bottom = offsetY + cell.boundingBox.bottom * scaleY
            bgPaint.color = cell.bgColor
            canvas.drawRect(left, top, right, bottom, bgPaint)
            if (!grid.isBorderless) canvas.drawRect(left, top, right, bottom, borderPaint)
            val padding = 5f
            var currentY = top + padding + textPaint.textSize
            val lineSpacing = textPaint.textSize + 4f
            for (line in cell.lines) {
                if (currentY > bottom - padding) break
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
