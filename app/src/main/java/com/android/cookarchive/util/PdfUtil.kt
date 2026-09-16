package com.android.cookarchive.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri

object PdfUtil {
    fun pdfToBitmaps(context: Context, uri: Uri): List<Bitmap> {
        val bitmaps = mutableListOf<Bitmap>()
        try {
            val contentResolver = context.contentResolver
            val parcelFileDescriptor = contentResolver.openFileDescriptor(uri, "r") ?: return emptyList()
            val renderer = PdfRenderer(parcelFileDescriptor)
            
            // Render up to the first 5 pages to avoid memory issues while ensuring we capture text + images
            val pageCount = minOf(renderer.pageCount, 5)
            for (i in 0 until pageCount) {
                val page = renderer.openPage(i)
                val bitmap = Bitmap.createBitmap(page.width, page.height, Bitmap.Config.ARGB_8888)
                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                bitmaps.add(bitmap)
                page.close()
            }
            
            renderer.close()
            parcelFileDescriptor.close()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return bitmaps
    }
}
