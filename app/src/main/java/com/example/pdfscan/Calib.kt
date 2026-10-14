package com.example.pdfscan

import android.graphics.Bitmap
import android.graphics.BitmapFactory

object Calib {
    fun decodeSampledBitmapFromFile(filePath: String, maxDimension: Int = 2048): Bitmap {
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(filePath, options)
        var inSampleSize = 1
        val (height, width) = options.outHeight to options.outWidth
        if (height > maxDimension || width > maxDimension) {
            val halfHeight = height / 2; val halfWidth = width / 2
            while ((halfHeight / inSampleSize) >= maxDimension || (halfWidth / inSampleSize) >= maxDimension) inSampleSize *= 2
        }
        val decodeOptions = BitmapFactory.Options().apply {
            this.inSampleSize = inSampleSize
            this.inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        return BitmapFactory.decodeFile(filePath, decodeOptions)
    }
}
