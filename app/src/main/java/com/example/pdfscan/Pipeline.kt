package com.example.pdfscan

import android.content.Context
import java.io.File

object Pipeline {
    data class PipelineResult(val pdfFile: File, val htmlFile: File, val grid: TableGrid)
    suspend fun processDocument(context: Context, inputImagePath: String, onProgress: suspend (Int, String) -> Unit = { _, _ -> }): PipelineResult {
        onProgress(10, "Görüntü yükleniyor ve kalibre ediliyor...")
        val rawBitmap = Calib.decodeSampledBitmapFromFile(inputImagePath)
        val orientedBitmap = Orientation.autoRotateIfNeeded(rawBitmap, 0f)
        onProgress(35, "OCR ve Türkçe sözlük düzeltmesi çalıştırılıyor...")
        val corrector = Corrector.fromAssets(context)
        val ocrLines = OcrEngines.recognizeTextLines(orientedBitmap, corrector)
        onProgress(65, "Tablo yapısı, birleşik hücreler ve renk/font stilleri analiz ediliyor...")
        val tableGrid = TableExtractor.extractTable(orientedBitmap, ocrLines)
        onProgress(85, "Renkli ve kalın font destekli PDF / Excel çıktıları üretiliyor...")
        val outPdf = File(context.cacheDir, "scanned_output_${System.currentTimeMillis()}.pdf")
        val outHtml = File(context.cacheDir, "scanned_table_${System.currentTimeMillis()}.html")
        PdfWriter.writeTableToPdf(context, tableGrid, outputFile = outPdf)
        Exporter.exportToHtmlTable(tableGrid, outputFile = outHtml)
        if (orientedBitmap != rawBitmap && !rawBitmap.isRecycled) rawBitmap.recycle()
        onProgress(100, "İşlem tamamlandı!")
        return PipelineResult(pdfFile = outPdf, htmlFile = outHtml, grid = tableGrid)
    }
}
