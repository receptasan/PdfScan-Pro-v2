package com.example.pdfscan

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.workDataOf
import java.io.File
import java.io.FileOutputStream

class MainActivity : AppCompatActivity() {
    private lateinit var statusText: TextView
    private lateinit var summaryText: TextView
    private lateinit var progressBar: ProgressBar
    private lateinit var previewImage: ImageView
    private lateinit var openPdfBtn: Button
    private lateinit var openHtmlBtn: Button
    private var lastPdfFile: File? = null
    private var lastHtmlFile: File? = null

    private val takePictureLauncher = registerForActivityResult(ActivityResultContracts.TakePicturePreview()) { bitmap ->
        if (bitmap != null) {
            val file = saveBitmapToCache(bitmap, "camera_capture.png")
            previewImage.setImageBitmap(bitmap)
            startScanWork(file)
        }
    }

    private val requestCameraPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            takePictureLauncher.launch(null)
        } else {
            Toast.makeText(this, "Kamera izni gerekiyor", Toast.LENGTH_SHORT).show()
        }
    }

    private val pickGalleryLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            val file = copyUriToCache(uri)
            if (file != null) {
                val bmp = BitmapFactory.decodeFile(file.absolutePath)
                previewImage.setImageBitmap(bmp)
                startScanWork(file)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val scroll = ScrollView(this).apply { isFillViewport = true }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(40, 40, 40, 40)
            setBackgroundColor(Color.rgb(245, 247, 250))
        }

        val title = TextView(this).apply {
            text = "PDFScan Pro v2.1"
            textSize = 22f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(Color.rgb(15, 23, 42))
        }
        val subtitle = TextView(this).apply {
            text = "Kamera veya Galeriden Fatura/Tablo Tarama • Kalın & Renk Korumalı PDF ve Excel Üretici"
            textSize = 13f
            setTextColor(Color.rgb(71, 85, 105))
            setPadding(0, 6, 0, 24)
        }

        val btnRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            weightSum = 2f
        }
        val cameraBtn = Button(this).apply {
            text = "📸 Kamera ile Çek"
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { rightMargin = 8 }
            setOnClickListener {
                if (ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                    takePictureLauncher.launch(null)
                } else {
                    requestCameraPermission.launch(Manifest.permission.CAMERA)
                }
            }
        }
        val galleryBtn = Button(this).apply {
            text = "🖼️ Galeriden Seç"
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { leftMargin = 8 }
            setOnClickListener { pickGalleryLauncher.launch("image/*") }
        }
        btnRow.addView(cameraBtn)
        btnRow.addView(galleryBtn)

        val sampleBtn = Button(this).apply {
            text = "🧪 Gerçekçi Fatura Şablonu Üret ve Test Et"
            setOnClickListener {
                val sampleFile = createRealisticInvoiceBitmap()
                val bmp = BitmapFactory.decodeFile(sampleFile.absolutePath)
                previewImage.setImageBitmap(bmp)
                startScanWork(sampleFile)
            }
        }

        previewImage = ImageView(this).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 460).apply {
                topMargin = 20; bottomMargin = 20
            }
            scaleType = ImageView.ScaleType.FIT_CENTER
            setBackgroundColor(Color.WHITE)
        }

        progressBar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 100
            progress = 0
            setPadding(0, 8, 0, 8)
        }

        statusText = TextView(this).apply {
            text = "Durum: Lütfen kameradan bir belge çekin, galeriden resim seçin veya örnek faturayı test edin."
            textSize = 14f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(Color.rgb(30, 41, 59))
            setPadding(0, 8, 0, 12)
        }

        val actionRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            weightSum = 2f
        }
        openPdfBtn = Button(this).apply {
            text = "📄 PDF'i Aç / Paylaş"
            isEnabled = false
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { rightMargin = 8 }
            setOnClickListener { lastPdfFile?.let { shareOrOpenFile(it, "application/pdf") } }
        }
        openHtmlBtn = Button(this).apply {
            text = "📊 Excel/HTML Aç"
            isEnabled = false
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { leftMargin = 8 }
            setOnClickListener { lastHtmlFile?.let { shareOrOpenFile(it, "text/html") } }
        }
        actionRow.addView(openPdfBtn)
        actionRow.addView(openHtmlBtn)

        summaryText = TextView(this).apply {
            text = "Çıkarılan tablo ve OCR sonuçları burada listelenecektir..."
            textSize = 13f
            setTextColor(Color.rgb(51, 65, 85))
            setBackgroundColor(Color.WHITE)
            setPadding(24, 24, 24, 24)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                topMargin = 20
            }
        }

        root.addView(title)
        root.addView(subtitle)
        root.addView(btnRow)
        root.addView(sampleBtn)
        root.addView(previewImage)
        root.addView(progressBar)
        root.addView(statusText)
        root.addView(actionRow)
        root.addView(summaryText)
        scroll.addView(root)
        setContentView(scroll)
    }

    private fun startScanWork(imageFile: File) {
        openPdfBtn.isEnabled = false
        openHtmlBtn.isEnabled = false
        progressBar.progress = 5
        statusText.text = "Durum: WorkManager ile arka planda taranıyor..."

        val workRequest = OneTimeWorkRequestBuilder<PdfScanWorker>()
            .setInputData(workDataOf(PdfScanWorker.KEY_INPUT_PATH to imageFile.absolutePath))
            .build()

        val wm = WorkManager.getInstance(this)
        wm.enqueue(workRequest)
        wm.getWorkInfoByIdLiveData(workRequest.id).observe(this) { info ->
            if (info != null) {
                val progress = info.progress.getInt(PdfScanWorker.KEY_PROGRESS, progressBar.progress)
                val status = info.progress.getString(PdfScanWorker.KEY_STATUS)
                    ?: info.outputData.getString(PdfScanWorker.KEY_STATUS)
                    ?: info.state.name
                progressBar.progress = progress
                statusText.text = "Durum: $status"

                if (info.state == WorkInfo.State.SUCCEEDED) {
                    progressBar.progress = 100
                    val pdfPath = info.outputData.getString(PdfScanWorker.KEY_OUTPUT_PDF)
                    val htmlPath = info.outputData.getString(PdfScanWorker.KEY_OUTPUT_HTML)
                    val summary = info.outputData.getString(PdfScanWorker.KEY_SUMMARY) ?: ""
                    if (pdfPath != null) { lastPdfFile = File(pdfPath); openPdfBtn.isEnabled = true }
                    if (htmlPath != null) { lastHtmlFile = File(htmlPath); openHtmlBtn.isEnabled = true }
                    summaryText.text = summary
                    Toast.makeText(this, "PDF ve Excel tablosu hazır!", Toast.LENGTH_LONG).show()
                } else if (info.state == WorkInfo.State.FAILED) {
                    summaryText.text = "İşlem başarısız oldu: $status"
                }
            }
        }
    }

    private fun shareOrOpenFile(file: File, mimeType: String) {
        try {
            val uri = FileProvider.getUriForFile(this, "${packageName}.provider", file)
            val viewIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, mimeType)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            val chooser = Intent.createChooser(viewIntent, "Dosyayı Aç veya Paylaş")
            startActivity(chooser)
        } catch (e: Exception) {
            Toast.makeText(this, "Paylaşım hatası: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
        }
    }

    private fun saveBitmapToCache(bitmap: Bitmap, name: String): File {
        val file = File(cacheDir, name)
        FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        return file
    }

    private fun copyUriToCache(uri: Uri): File? {
        return try {
            val file = File(cacheDir, "gallery_picked_${System.currentTimeMillis()}.png")
            contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(file).use { output -> input.copyTo(output) }
            }
            file
        } catch (e: Exception) { null }
    }

    private fun createRealisticInvoiceBitmap(): File {
        val bmp = Bitmap.createBitmap(1000, 720, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        canvas.drawColor(Color.WHITE)

        val headerBg = Paint().apply { color = Color.rgb(219, 234, 254); style = Paint.Style.FILL }
        val rowBg = Paint().apply { color = Color.rgb(241, 245, 249); style = Paint.Style.FILL }
        canvas.drawRect(60f, 60f, 940f, 160f, headerBg)
        canvas.drawRect(60f, 160f, 940f, 250f, rowBg)

        val linePaint = Paint().apply { color = Color.BLACK; strokeWidth = 4f; style = Paint.Style.STROKE }
        canvas.drawRect(60f, 60f, 940f, 620f, linePaint)
        canvas.drawLine(60f, 160f, 940f, 160f, linePaint)
        canvas.drawLine(60f, 250f, 940f, 250f, linePaint)
        canvas.drawLine(60f, 380f, 940f, 380f, linePaint)
        canvas.drawLine(60f, 500f, 940f, 500f, linePaint)
        canvas.drawLine(500f, 160f, 500f, 620f, linePaint)
        canvas.drawLine(720f, 160f, 720f, 620f, linePaint)

        val boldBlue = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(30, 58, 138)
            textSize = 34f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        }
        val boldBlack = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            textSize = 28f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        }
        val regularText = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(30, 41, 59)
            textSize = 26f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
        }

        canvas.drawText("KURUMSAL FATURA OZETI (BIRLESIK BASLIK)", 140f, 122f, boldBlue)
        canvas.drawText("ACIKLAMA / URUN", 85f, 215f, boldBlack)
        canvas.drawText("MIKTAR", 540f, 215f, boldBlack)
        canvas.drawText("TUTAR", 760f, 215f, boldBlack)

        canvas.drawText("Yazilim Gelistirme", 85f, 305f, regularText)
        canvas.drawText("Bakim Hizmeti", 85f, 345f, regularText)
        canvas.drawText("2 Adet", 540f, 325f, regularText)
        canvas.drawText("14500 TL", 750f, 325f, boldBlack)

        canvas.drawText("Sunucu Barindirma", 85f, 445f, regularText)
        canvas.drawText("1 Yil", 540f, 445f, regularText)
        canvas.drawText("5500 TL", 750f, 445f, regularText)

        canvas.drawText("GENEL TOPLAM", 85f, 570f, boldBlue)
        canvas.drawText("3 Kalem", 540f, 570f, boldBlack)
        canvas.drawText("20000 TL", 750f, 570f, boldBlue)

        return saveBitmapToCache(bmp, "sample_realistic_invoice.png")
    }
}
