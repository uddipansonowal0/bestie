package com.example.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.util.Base64
import java.io.ByteArrayOutputStream
import java.io.InputStream

object ScrapUtils {

    /**
     * Converts a Uri pointing to an image into a Bitmap.
     */
    fun uriToBitmap(context: Context, uri: Uri): Bitmap? {
        return try {
            val inputStream: InputStream? = context.contentResolver.openInputStream(uri)
            BitmapFactory.decodeStream(inputStream)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Converts a Bitmap to a Base64 string for Gemini API.
     */
    fun bitmapToBase64(bitmap: Bitmap, quality: Int = 80): String? {
        return try {
            val outputStream = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, quality, outputStream)
            val bytes = outputStream.toByteArray()
            Base64.encodeToString(bytes, Base64.NO_WRAP)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Renders the first page of a PDF into a Bitmap.
     */
    fun renderPdfFirstPage(context: Context, uri: Uri, maxWidth: Int = 1024): Bitmap? {
        return try {
            context.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                PdfRenderer(pfd).use { renderer ->
                    if (renderer.pageCount > 0) {
                        renderer.openPage(0).use { page ->
                            val scale = maxWidth.toFloat() / page.width.toFloat()
                            val width = if (scale < 1.0f) maxWidth else page.width
                            val height = if (scale < 1.0f) (page.height * scale).toInt() else page.height
                            
                            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                            val canvas = android.graphics.Canvas(bitmap)
                            canvas.drawColor(android.graphics.Color.WHITE)
                            
                            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                            bitmap
                        }
                    } else {
                        null
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Reads a plain text file completely from a Uri.
     */
    fun readTextFromUri(context: Context, uri: Uri): String {
        return try {
            val inputStream = context.contentResolver.openInputStream(uri)
            inputStream?.bufferedReader()?.use { it.readText() } ?: ""
        } catch (e: Exception) {
            "Error rendering/reading: ${e.message}"
        }
    }

    /**
     * Helper to get file size from a Uri.
     */
    fun getFileSize(context: Context, uri: Uri): Long {
        return try {
            context.contentResolver.openAssetFileDescriptor(uri, "r")?.use {
                it.length
            } ?: 0L
        } catch (e: Exception) {
            0L
        }
    }

    /**
     * Formats bytes to a readable string.
     */
    fun formatBytes(bytes: Long): String {
        if (bytes <= 0) return "0 B"
        val units = arrayOf("B", "KB", "MB", "GB")
        val digitGroups = (Math.log10(bytes.toDouble()) / Math.log10(1024.0)).toInt()
        return String.format("%.2f %s", bytes / Math.pow(1024.0, digitGroups.toDouble()), units[digitGroups])
    }
}
