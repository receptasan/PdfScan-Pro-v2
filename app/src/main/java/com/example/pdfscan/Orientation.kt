package com.example.pdfscan

import android.graphics.Bitmap
import android.graphics.Matrix

object Orientation {
    fun autoRotateIfNeeded(bitmap: Bitmap, rotationDegrees: Float = 0f): Bitmap {
        if (rotationDegrees == 0f) return bitmap
        val matrix = Matrix().apply { postRotate(rotationDegrees) }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }
}
