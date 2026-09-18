package com.android.cookarchive.util

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import androidx.core.graphics.scale
import com.android.cookarchive.BuildConfig
import com.android.cookarchive.data.entities.MealPlanWithRecipe
import com.android.cookarchive.data.entities.RecipeWithDetails
import com.android.cookarchive.ui.screens.DaySlot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

object WebSyncUtil {

    private val JSONBIN_URL = "https://api.jsonbin.io/v3/b/${BuildConfig.JSONBIN_BIN_ID}"
    private val ACCESS_KEY = BuildConfig.JSONBIN_ACCESS_KEY

    data class MealWish(
        val id: String,
        val title: String,
        val requestedBy: String = "Familie",
        val timestamp: Long = System.currentTimeMillis()
    )

    suspend fun fetchWishesFromWeb(): List<MealWish> = withContext(Dispatchers.IO) {
        try {
            val targetUrl = URL("${JSONBIN_URL}/latest")
            val connection = (targetUrl.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                setRequestProperty("X-Access-Key", ACCESS_KEY)
                connectTimeout = 10000
                readTimeout = 10000
            }

            if (connection.responseCode in 200..299) {
                val responseText = connection.inputStream.bufferedReader().use { it.readText() }
                connection.disconnect()
                
                val root = JSONObject(responseText)
                val record = root.optJSONObject("record") ?: root
                val wishesArray = record.optJSONArray("wishes") ?: JSONArray()

                val wishesList = mutableListOf<MealWish>()
                for (i in 0 until wishesArray.length()) {
                    val w = wishesArray.optJSONObject(i) ?: continue
                    val title = w.optString("title", "").trim()
                    if (title.isNotBlank()) {
                        wishesList.add(
                            MealWish(
                                id = w.optString("id", System.currentTimeMillis().toString()),
                                title = title,
                                requestedBy = w.optString("requestedBy", "Familie"),
                                timestamp = w.optLong("timestamp", System.currentTimeMillis())
                            )
                        )
                    }
                }
                return@withContext wishesList
            }
            connection.disconnect()
            emptyList()
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    suspend fun syncMealPlanToWeb(
        daySlots: List<DaySlot>,
        mealPlansByDate: Map<String, List<MealPlanWithRecipe>>,
        allRecipes: List<RecipeWithDetails> = emptyList(),
        existingWishes: List<MealWish>? = null
    ) = withContext(Dispatchers.IO) {
        try {
            val currentWishes = existingWishes ?: fetchWishesFromWeb()

            val rootJson = JSONObject()
            rootJson.put("lastUpdated", System.currentTimeMillis())

            // 1. Days & Scheduled Meals
            val daysArray = JSONArray()
            daySlots.forEach { slot ->
                val dayJson = JSONObject()
                dayJson.put("isoDate", slot.isoDate)
                dayJson.put("displayTitle", slot.displayTitle)
                dayJson.put("formattedDateStr", slot.formattedDateStr)

                val mealsArray = JSONArray()
                val mealsForDay = mealPlansByDate[slot.isoDate] ?: emptyList()

                mealsForDay.forEach { item ->
                    val mealJson = JSONObject()
                    mealJson.put("id", item.mealPlan.id)
                    mealJson.put("title", item.displayTitle)
                    mealJson.put("category", item.displayCategory)

                    val rawImage = item.recipeWithDetails?.recipe?.imagePath
                    val imagePayload = encodeImageForSync(rawImage)
                    mealJson.put("image", imagePayload ?: "")

                    mealsArray.put(mealJson)
                }

                dayJson.put("meals", mealsArray)
                daysArray.put(dayJson)
            }
            rootJson.put("days", daysArray)

            // 2. All Recipe Catalog for Website Wish Dropdown
            val recipesArray = JSONArray()
            allRecipes.forEach { recipeDetails ->
                val rJson = JSONObject()
                rJson.put("id", recipeDetails.recipe.id)
                rJson.put("title", recipeDetails.recipe.title)
                rJson.put("category", recipeDetails.recipe.category)
                recipesArray.put(rJson)
            }
            rootJson.put("recipes", recipesArray)

            // 3. Active Wishes Array
            val wishesArray = JSONArray()
            currentWishes.forEach { wish ->
                val wJson = JSONObject()
                wJson.put("id", wish.id)
                wJson.put("title", wish.title)
                wJson.put("requestedBy", wish.requestedBy)
                wJson.put("timestamp", wish.timestamp)
                wishesArray.put(wJson)
            }
            rootJson.put("wishes", wishesArray)

            // Send PUT request to JSONBin.io to update the Bin
            val targetUrl = URL(JSONBIN_URL)
            val connection = (targetUrl.openConnection() as HttpURLConnection).apply {
                requestMethod = "PUT"
                doOutput = true
                setRequestProperty("Content-Type", "application/json; charset=UTF-8")
                setRequestProperty("X-Access-Key", ACCESS_KEY)
                connectTimeout = 15000
                readTimeout = 15000
            }

            connection.outputStream.use { out ->
                out.write(rootJson.toString().toByteArray(Charsets.UTF_8))
            }

            val responseCode = connection.responseCode
            connection.disconnect()
            responseCode in 200..299
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    private fun encodeImageForSync(path: String?): String? {
        if (path.isNullOrBlank()) return null

        if (path.startsWith("http://") || path.startsWith("https://")) {
            return path
        }

        return try {
            val file = File(path)
            if (!file.exists()) return null

            val originalBitmap = BitmapFactory.decodeFile(file.absolutePath) ?: return null

            val maxDimension = 200
            val width = originalBitmap.width
            val height = originalBitmap.height

            val scaledBitmap = if (width > maxDimension || height > maxDimension) {
                val ratio = minOf(maxDimension.toFloat() / width, maxDimension.toFloat() / height)
                val targetW = (width * ratio).toInt()
                val targetH = (height * ratio).toInt()
                originalBitmap.scale(targetW, targetH)
            } else {
                originalBitmap
            }

            val baos = ByteArrayOutputStream()
            scaledBitmap.compress(Bitmap.CompressFormat.JPEG, 60, baos)
            val bytes = baos.toByteArray()

            "data:image/jpeg;base64," + Base64.encodeToString(bytes, Base64.NO_WRAP)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}
