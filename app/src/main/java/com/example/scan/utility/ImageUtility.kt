package com.example.scan.utility

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.os.Environment
import android.util.Log
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream

object ImageUtility {
    private const val TAG = "ImageUtility"
    private const val MAX_FOLDER_SIZE_BYTES = 100 * 1024 * 1024L // 100 MB
    private const val TARGET_FOLDER_SIZE_BYTES = 80 * 1024 * 1024L // 80 MB (to have some headroom)

    fun extractYPlane(image: androidx.camera.core.ImageProxy): ByteArray {
        val plane = image.planes[0]
        val buffer = plane.buffer
        val width = image.width
        val height = image.height
        val rowStride = plane.rowStride
        val pixelStride = plane.pixelStride

        val bytes = ByteArray(width * height)
        buffer.rewind()
        if (rowStride == width && pixelStride == 1) {
            buffer.get(bytes)
        } else {
            for (row in 0 until height) {
                for (col in 0 until width) {
                    bytes[row * width + col] = buffer.get(row * rowStride + col * pixelStride)
                }
            }
        }
        return bytes
    }

    fun saveGrayscaleImage(
        context: Context,
        yBytes: ByteArray,
        width: Int,
        height: Int,
        rotationDegrees: Int,
        taskId: String?,
        firstCode: String
    ) {
        try {
            val startTime = System.currentTimeMillis()

            // 1. Create Bitmap from Y plane
            val pixels = IntArray(width * height)
            for (i in 0 until width * height) {
                val y = yBytes[i].toInt() and 0xFF
                pixels[i] = (0xFF shl 24) or (y shl 16) or (y shl 8) or y
            }
            var bitmap = Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888)

            // 2. Rotate and downscale
            // Start with a reasonable resolution that can potentially fit in 15kB
            val maxDimension = 1024
            val scale = maxDimension.toFloat() / maxOf(width, height).coerceAtLeast(1)

            val matrix = Matrix()
            if (scale < 1.0f) {
                matrix.postScale(scale, scale)
            }
            if (rotationDegrees != 0) {
                matrix.postRotate(rotationDegrees.toFloat())
            }

            if (!matrix.isIdentity) {
                val scaledBitmap = Bitmap.createBitmap(bitmap, 0, 0, width, height, matrix, true)
                if (scaledBitmap != bitmap) {
                    bitmap.recycle()
                    bitmap = scaledBitmap
                }
            }

            // 3. Iteratively compress to reach < 15 kB
            val targetSize = 15 * 1024
            var quality = 80
            var bos = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, quality, bos)

            while (bos.size() > targetSize && (quality > 10 || bitmap.width > 320)) {
                if (quality > 10) {
                    quality -= 10
                } else {
                    // If even at low quality it's too big, downscale further
                    val furtherScale = 0.8f
                    val nextWidth = (bitmap.width * furtherScale).toInt()
                    val nextHeight = (bitmap.height * furtherScale).toInt()
                    if (nextWidth < 100 || nextHeight < 100) break // Don't go too small

                    val smallerBitmap = Bitmap.createScaledBitmap(bitmap, nextWidth, nextHeight, true)
                    bitmap.recycle()
                    bitmap = smallerBitmap
                    quality = 60 // Try with moderate quality on smaller image
                }
                bos = ByteArrayOutputStream()
                bitmap.compress(Bitmap.CompressFormat.JPEG, quality, bos)
            }

            val jpegBytes = bos.toByteArray()

            // 4. Prepare filename
            val timestamp = System.currentTimeMillis()
            val cleanTaskId = taskId?.replace(Regex("[\\\\/:*?\"<>|]"), "_") ?: "no_task"
            val cleanCode = firstCode.replace(Regex("[\\\\/:*?\"<>|]"), "_")
            val fileName = "${cleanTaskId}_${cleanCode}_${timestamp}.jpg"

            // 5. Save to Private External Storage
            val directory = File(context.getExternalFilesDir(Environment.DIRECTORY_PICTURES), "ScanImages")
            if (!directory.exists()) {
                directory.mkdirs()
            }
            val file = File(directory, fileName)
            FileOutputStream(file).use { outputStream ->
                outputStream.write(jpegBytes)
            }
            Log.d(TAG, "Image saved: ${file.absolutePath}, size: ${jpegBytes.size} bytes in ${System.currentTimeMillis() - startTime}ms")

            // 6. Apply retention policy
            applyRetentionPolicy(directory)

            bitmap.recycle()

        } catch (e: Exception) {
            Log.e(TAG, "Error saving grayscale image", e)
        }
    }

    private fun applyRetentionPolicy(directory: File) {
        try {
            val files = directory.listFiles() ?: return
            val sortedFiles = files.sortedBy { it.lastModified() }
            var totalSize = sortedFiles.sumOf { it.length() }

            if (totalSize > MAX_FOLDER_SIZE_BYTES) {
                Log.d(TAG, "Folder size ($totalSize) exceeds limit. Starting cleanup.")
                for (file in sortedFiles) {
                    val fileSize = file.length()
                    if (file.delete()) {
                        totalSize -= fileSize
                        Log.d(TAG, "Deleted old image: ${file.name}, remaining size: $totalSize")
                    } else {
                        Log.e(TAG, "Failed to delete image: ${file.name}")
                    }

                    if (totalSize <= TARGET_FOLDER_SIZE_BYTES) break
                }
                Log.d(TAG, "Cleanup finished. Final size: $totalSize")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error applying retention policy", e)
        }
    }
}
