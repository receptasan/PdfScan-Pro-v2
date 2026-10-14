package com.example.pdfscan

import android.content.Context
import java.io.File

object Pipeline {
    data class PipelineResult(val pdfFile: File, val htmlFile: File, val grid: TableGrid, val summaryText: String)
    suspend fun processDocument(context: Context, inputImagePath: String, onProgress: suspend (Int, String) -> Unit = { _, _ -> }): PipelineResult {
        onProgress(10, "Görüntü yükleniyor ve kalibre ediliyor...")
        val rawBitmap = Calib.decodeSampledBitmapFromFile(inputImagePath)
        val orientedBitmap = Orientation.autoRotateIfNeeded(rawBitmap, 0f)
        onProgress(35, "ML Kit OCR ve Türkçe sözlük düzeltmesi çalıştırılıyor...")
        val corrector = Corrector.fromAssets(context)
        val ocrLines = OcrEngines.recognizeTextLines(orientedBitmap, corrector)
        onProgress(65, "Tablo yapısı, birleşik hücreler ve renk/kalınlık analiz ediliyor...")
        val tableGrid = TableExtractor.extractTable(orientedBitmap, ocrLines)
        onProgress(85, "Renkli ve kalın font destekli PDF / Excel çıktıları üretiliyor...")
        val outPdf = File(context.cacheDir, "PDFScan_Tablo_${System.currentTimeMillis()}.pdf")
        val outHtml = File(context.cacheDir, "PDFScan_Tablo_${System.currentTimeMillis()}.html")
        PdfWriter.writeTableToPdf(context, tableGrid, outputFile = outPdf)
        Exporter.exportToHtmlTable(tableGrid, outputFile = outHtml)
        val nonEmptyCells = tableGrid.cells.filter { it.fullText.isNotBlank() }
        val previewLines = nonEmptyCells.take(8).joinToString("\n") { cell ->
            val spanInfo = if (cell.rowSpan > 1 || cell.colSpan > 1) " [Birleşik ${cell.rowSpan}x${cell.colSpan}]" else ""
            val boldInfo = if (cell.lines.any { it.isBold }) " [KALIN]" else ""
            "• [Satır ${cell.row + 1}, Sütun ${cell.col + 1}]$spanInfo$boldInfo: ${cell.fullText.replace("\n", " / ")}"
        }
        val modeStr = if (tableGrid.isBorderless) "Çizgisiz (Borderless) Tablo" else "Çizgili & Birleşik Hücreli Tablo"
        val summary = "Algılanan Mod: $modeStr\nBoyut: ${tableGrid.rowCount} Satır x ${tableGrid.colCount} Sütun (${tableGrid.cells.size} Hücre)\nOkunan Metin Satırı: ${ocrLines.size}\n\nÖrnek Hücre İçerikleri:\n" + (if (previewLines.isNotEmpty()) previewLines else "(Metin bulunamadı)")
        if (orientedBitmap != rawBitmap && !rawBitmap.isRecycled) rawBitmap.recycle()
        onProgress(100, "İşlem tamamlandı!")
        return PipelineResult(pdfFile = outPdf, htmlFile = outHtml, grid = tableGrid, summaryText = summary)
    }
}
