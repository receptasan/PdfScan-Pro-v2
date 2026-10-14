package com.example.pdfscan

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.workDataOf
import java.io.File
import java.io.FileOutputStream

class MainActivity : AppCompatActivity() {
    private lateinit var statusText: TextView
    private lateinit var progressBar: ProgressBar
    private lateinit var startBtn: Button
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val layout = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(48, 48, 48, 48) }
        statusText = TextView(this).apply { text = "Hazır. Kalın/Renk Korumalı, Birleşik Hücreli ve Çizgisiz Tablo Destekli Tarama."; textSize = 16f }
        progressBar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply { max = 100; progress = 0 }
        startBtn = Button(this).apply { text = "Örnek Tablo Tara (WorkManager ile Arka Planda)"; setOnClickListener { startBackgroundScan() } }
        layout.addView(statusText); layout.addView(progressBar); layout.addView(startBtn)
        setContentView(layout)
    }
    private fun startBackgroundScan() {
        val sampleFile = createSampleTableImage()
        val workRequest = OneTimeWorkRequestBuilder<PdfScanWorker>()
            .setInputData(workDataOf(PdfScanWorker.KEY_INPUT_PATH to sampleFile.absolutePath))
            .build()
        val wm = WorkManager.getInstance(this)
        wm.enqueue(workRequest)
        wm.getWorkInfoByIdLiveData(workRequest.id).observe(this) { info ->
            if (info != null) {
                val progress = info.progress.getInt(PdfScanWorker.KEY_PROGRESS, 0)
                val status = info.progress.getString(PdfScanWorker.KEY_STATUS) ?: info.outputData.getString(PdfScanWorker.KEY_STATUS) ?: info.state.name
                progressBar.progress = progress
                statusText.text = "Durum: $status"
                if (info.state == WorkInfo.State.SUCCEEDED) {
                    val pdfPath = info.outputData.getString(PdfScanWorker.KEY_OUTPUT_PDF)
                    val htmlPath = info.outputData.getString(PdfScanWorker.KEY_OUTPUT_HTML)
                    statusText.text = "Başarılı!\nPDF: $pdfPath\nHTML/Excel: $htmlPath"
                }
            }
        }
    }
    private fun createSampleTableImage(): File {
        val bmp = Bitmap.createBitmap(800, 500, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp); canvas.drawColor(Color.WHITE)
        val bgPaint = Paint().apply { color = Color.rgb(220, 235, 252); style = Paint.Style.FILL }
        canvas.drawRect(50f, 50f, 750f, 130f, bgPaint)
        val linePaint = Paint().apply { color = Color.BLACK; strokeWidth = 3f; style = Paint.Style.STROKE }
        canvas.drawRect(50f, 50f, 750f, 350f, linePaint)
        canvas.drawLine(50f, 130f, 750f, 130f, linePaint)
        canvas.drawLine(50f, 240f, 750f, 240f, linePaint)
        canvas.drawLine(400f, 130f, 400f, 350f, linePaint)
        val file = File(cacheDir, "sample_input.png")
        FileOutputStream(file).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        return file
    }
}
