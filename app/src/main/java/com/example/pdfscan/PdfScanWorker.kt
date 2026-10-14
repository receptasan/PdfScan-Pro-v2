package com.example.pdfscan

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import androidx.work.workDataOf

class PdfScanWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {
    companion object {
        const val KEY_INPUT_PATH = "input_image_path"
        const val KEY_OUTPUT_PDF = "output_pdf_path"
        const val KEY_OUTPUT_HTML = "output_html_path"
        const val KEY_SUMMARY = "output_summary"
        const val KEY_PROGRESS = "progress"
        const val KEY_STATUS = "status"
        private const val CHANNEL_ID = "pdfscan_worker_channel"
        private const val NOTIFICATION_ID = 1001
    }
    override suspend fun doWork(): Result {
        val inputPath = inputData.getString(KEY_INPUT_PATH) ?: return Result.failure()
        createNotificationChannel()
        try { setForeground(createForegroundInfo(0, "Belge tarama başlatılıyor...")) } catch (_: Exception) {}
        return try {
            val result = Pipeline.processDocument(applicationContext, inputPath) { percent, message ->
                setProgress(workDataOf(KEY_PROGRESS to percent, KEY_STATUS to message))
                try { setForeground(createForegroundInfo(percent, message)) } catch (_: Exception) {}
            }
            Result.success(workDataOf(
                KEY_OUTPUT_PDF to result.pdfFile.absolutePath,
                KEY_OUTPUT_HTML to result.htmlFile.absolutePath,
                KEY_SUMMARY to result.summaryText,
                KEY_STATUS to "Tamamlandı (${result.grid.cells.size} hücre çıkarıldı)"
            ))
        } catch (e: Exception) {
            Result.failure(workDataOf(KEY_STATUS to "Hata: ${e.localizedMessage}"))
        }
    }
    private fun createForegroundInfo(progress: Int, message: String): ForegroundInfo {
        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setContentTitle("PDFScan Pro Belge İşleme")
            .setContentText(message)
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .setOngoing(true)
            .setProgress(100, progress, false)
            .build()
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(NOTIFICATION_ID, notification)
        }
    }
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, "PDFScan Belge İşleme", NotificationManager.IMPORTANCE_LOW)
            applicationContext.getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
        }
    }
}
