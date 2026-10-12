package com.geotag.app.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import androidx.exifinterface.media.ExifInterface
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object PhotoUtils {
    fun createImageFile(context: Context): File {
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val dir = File(context.filesDir, "photos")
        if (!dir.exists()) dir.mkdirs()
        return File(dir, "GEOTAG_${timestamp}.jpg")
    }

    fun compressPhoto(context: Context, photoFile: File, maxDimension: Int = 1200, quality: Int = 80): File {
        val options = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }
        BitmapFactory.decodeFile(photoFile.absolutePath, options)

        val scale = maxOf(options.outWidth, options.outHeight).toFloat() / maxDimension
        val sampleSize = if (scale > 1) {
            var s = 1
            while (s * 2 <= scale) s *= 2
            s
        } else 1

        val decodeOptions = BitmapFactory.Options().apply {
            inSampleSize = sampleSize
        }
        var bitmap = BitmapFactory.decodeFile(photoFile.absolutePath, decodeOptions) ?: return photoFile

        // Handle EXIF rotation
        try {
            val exif = ExifInterface(photoFile.absolutePath)
            val orientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
            val matrix = Matrix()
            when (orientation) {
                ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
                ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
                ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            }
            bitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        } catch (_: Exception) {}

        // Scale if still too large
        if (bitmap.width > maxDimension || bitmap.height > maxDimension) {
            val ratio = minOf(maxDimension.toFloat() / bitmap.width, maxDimension.toFloat() / bitmap.height)
            bitmap = Bitmap.createScaledBitmap(bitmap, (bitmap.width * ratio).toInt(), (bitmap.height * ratio).toInt(), true)
        }

        val compressed = File(context.cacheDir, "compressed_${photoFile.name}")
        FileOutputStream(compressed).use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, quality, out)
        }
        return compressed
    }
}
