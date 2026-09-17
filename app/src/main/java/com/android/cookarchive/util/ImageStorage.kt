package com.android.cookarchive.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID

object ImageStorage {
    
    private const val TAG = "ImageStorage"
    private const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36"

    suspend fun saveImageFromUrl(context: Context, imageUrl: String): String? = withContext(Dispatchers.IO) {
        try {
            var currentUrl = imageUrl
            var redirects = 0
            
            while (redirects < 5) {
                val url = URL(currentUrl)
                val connection = (url.openConnection() as HttpURLConnection).apply {
                    doInput = true
                    instanceFollowRedirects = true
                    setRequestProperty("User-Agent", USER_AGENT)
                    setRequestProperty("Accept", "image/webp,image/apng,image/svg+xml,image/*,*/*;q=0.8")
                    connectTimeout = 12000
                    readTimeout = 12000
                }
                
                val code = connection.responseCode
                if (code == HttpURLConnection.HTTP_MOVED_PERM || 
                    code == HttpURLConnection.HTTP_MOVED_TEMP || 
                    code == HttpURLConnection.HTTP_SEE_OTHER || 
                    code == 307 || code == 308
                ) {
                    val location = connection.getHeaderField("Location")
                    if (!location.isNullOrBlank()) {
                        currentUrl = location
                        redirects++
                        continue
                    }
                }
                
                if (code != HttpURLConnection.HTTP_OK) {
                    Log.w(TAG, "HTTP error $code fetching image from: $currentUrl")
                    return@withContext null
                }

                val bitmap = connection.inputStream.use { input ->
                    BitmapFactory.decodeStream(input)
                } ?: return@withContext null

                return@withContext saveBitmap(context, bitmap)
            }
            null
        } catch (e: Exception) {
            Log.e(TAG, "Error saving image from URL: $imageUrl", e)
            null
        }
    }

    suspend fun saveBitmap(context: Context, bitmap: Bitmap): String? = withContext(Dispatchers.IO) {
        try {
            val fileName = "recipe_${UUID.randomUUID()}.jpg"
            val file = File(context.filesDir, fileName)
            
            FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
            }
            
            file.absolutePath
        } catch (e: Exception) {
            Log.e(TAG, "Error saving bitmap to file", e)
            null
        }
    }

    fun deleteImage(path: String?) {
        if (path == null) return
        try {
            val file = File(path)
            if (file.exists() && file.absolutePath.contains("/files/recipe_")) {
                file.delete()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting image file: $path", e)
        }
    }
}
