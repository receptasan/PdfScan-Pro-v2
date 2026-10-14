package com.example.pdfscan

import android.graphics.Bitmap
import android.graphics.Rect
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine

object OcrEngines {
    suspend fun recognizeTextLines(bitmap: Bitmap, corrector: Corrector): List<TextLine> =
        suspendCancellableCoroutine { cont ->
            val image = InputImage.fromBitmap(bitmap, 0)
            val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
            recognizer.process(image)
                .addOnSuccessListener { visionText ->
                    val extractedLines = mutableListOf<TextLine>()
                    for (block in visionText.textBlocks) {
                        for (line in block.lines) {
                            val rect = line.boundingBox ?: Rect(0, 0, 10, 10)
                            val correctedText = corrector.correctLine(line.text)
                            extractedLines.add(TextLine(text = correctedText, boundingBox = rect))
                        }
                    }
                    if (cont.isActive) cont.resume(extractedLines)
                }
                .addOnFailureListener { e -> if (cont.isActive) cont.resumeWithException(e) }
        }
}
