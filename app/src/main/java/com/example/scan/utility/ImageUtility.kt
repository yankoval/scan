package com.example.scan.utility

import android.content.ContentResolver
import android.content.ContentValues
import android.graphics.ImageFormat
import android.graphics.Rect
import android.graphics.YuvImage
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import java.io.OutputStream
import java.util.Arrays

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
        yBytes: ByteArray,
        width: Int,
        height: Int,
        rotationDegrees: Int,
        taskId: String?,
        firstCode: String,
        contentResolver: ContentResolver
    ) {
        try {
            val startTime = System.currentTimeMillis()

            // 1. Prepare NV21 for YuvImage (grayscale by setting U/V to 128)
            val nv21 = ByteArray(width * height * 3 / 2)
            System.arraycopy(yBytes, 0, nv21, 0, width * height)
            Arrays.fill(nv21, width * height, nv21.size, 128.toByte())

            val yuvImage = YuvImage(nv21, ImageFormat.NV21, width, height, null)

            // 2. Prepare filename
            val timestamp = System.currentTimeMillis()
            val cleanTaskId = taskId?.replace(Regex("[\\\\/:*?\"<>|]"), "_") ?: "no_task"
            val cleanCode = firstCode.replace(Regex("[\\\\/:*?\"<>|]"), "_")
            val fileName = "${cleanTaskId}_${cleanCode}_${timestamp}.jpg"

            // 3. Save to MediaStore
            val relativePath = Environment.DIRECTORY_PICTURES + "/ScanImages"
            val contentValues = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
                put(MediaStore.Images.Media.ORIENTATION, rotationDegrees)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath)
                    put(MediaStore.Images.Media.IS_PENDING, 1)
                }
            }

            val uri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
            if (uri != null) {
                contentResolver.openOutputStream(uri)?.use { outputStream ->
                    yuvImage.compressToJpeg(Rect(0, 0, width, height), 70, outputStream)
                }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    contentValues.clear()
                    contentValues.put(MediaStore.Images.Media.IS_PENDING, 0)
                    contentResolver.update(uri, contentValues, null, null)
                }
                Log.d(TAG, "Image saved: $fileName in ${System.currentTimeMillis() - startTime}ms")

                // 4. Apply retention policy
                applyRetentionPolicy(contentResolver)
            } else {
                Log.e(TAG, "Failed to create MediaStore entry for $fileName")
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error saving grayscale image", e)
        }
    }

    private fun applyRetentionPolicy(contentResolver: ContentResolver) {
        try {
            val projection = arrayOf(
                MediaStore.Images.Media._ID,
                MediaStore.Images.Media.SIZE,
                MediaStore.Images.Media.DATE_ADDED
            )

            // Filter for files in our specific folder
            val selection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                "${MediaStore.Images.Media.RELATIVE_PATH} LIKE ?"
            } else {
                "${MediaStore.Images.Media.DATA} LIKE ?"
            }
            val selectionArgs = arrayOf("%ScanImages%")

            val sortOrder = "${MediaStore.Images.Media.DATE_ADDED} ASC"

            val queryUri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI

            val files = mutableListOf<FileData>()
            var totalSize = 0L

            contentResolver.query(queryUri, projection, selection, selectionArgs, sortOrder)?.use { cursor ->
                val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                val sizeColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.SIZE)

                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idColumn)
                    val size = cursor.getLong(sizeColumn)
                    files.add(FileData(id, size))
                    totalSize += size
                }
            }

            if (totalSize > MAX_FOLDER_SIZE_BYTES) {
                Log.d(TAG, "Folder size ($totalSize) exceeds limit. Starting cleanup.")
                var currentSize = totalSize
                for (file in files) {
                    val deleteUri = android.content.ContentUris.withAppendedId(queryUri, file.id)
                    try {
                        contentResolver.delete(deleteUri, null, null)
                        currentSize -= file.size
                        Log.d(TAG, "Deleted old image: ${file.id}, remaining size: $currentSize")
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to delete image: ${file.id}", e)
                    }

                    if (currentSize <= TARGET_FOLDER_SIZE_BYTES) break
                }
                Log.d(TAG, "Cleanup finished. Final size: $currentSize")
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error applying retention policy", e)
        }
    }

    private data class FileData(val id: Long, val size: Long)
}
