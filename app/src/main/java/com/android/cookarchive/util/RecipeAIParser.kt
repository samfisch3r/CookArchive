package com.android.cookarchive.util

import android.graphics.Bitmap
import android.util.Log
import com.android.cookarchive.BuildConfig
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.content
import com.android.cookarchive.data.entities.Ingredient
import com.android.cookarchive.data.entities.InstructionStep
import com.android.cookarchive.data.entities.Recipe
import kotlinx.coroutines.delay
import org.json.JSONObject
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import kotlin.time.Duration.Companion.milliseconds

object RecipeAIParser {

    private const val TAG = "RecipeAIParser"

    var lastFailureReason: String? = null
        private set

    // Text-only models for webpage text extraction (includes high-quota Lite models)
    private val TEXT_MODEL_CANDIDATES = listOf(
        "gemini-3.5-flash-lite",
        "gemini-3.8-flash",
        "gemini-3.7-flash",
        "gemini-3.6-flash",
        "gemini-3.5-flash",
        "gemini-3.1-flash-lite"
    )

    // Multimodal models for photo/image scan (MUST support image inputs)
    private val MULTIMODAL_MODEL_CANDIDATES = listOf(
        "gemini-3.8-flash",
        "gemini-3.7-flash",
        "gemini-3.6-flash",
        "gemini-3.5-flash"
    )

    private fun getGenerativeModel(modelName: String): GenerativeModel {
        return GenerativeModel(
            modelName = modelName,
            apiKey = BuildConfig.GEMINI_API_KEY
        )
    }

    private fun isQuotaError(e: Exception): Boolean {
        val msg = e.message ?: ""
        return msg.contains("QuotaExceededException", ignoreCase = true) ||
               msg.contains("Quota exceeded", ignoreCase = true) ||
               msg.contains("limit: 20", ignoreCase = true) ||
               msg.contains("RESOURCE_EXHAUSTED", ignoreCase = true) ||
               msg.contains("429", ignoreCase = true)
    }

    private fun formatError(e: Exception): String {
        val msg = e.message ?: ""
        return when {
            isQuotaError(e) ->
                "Gemini AI rate limit reached (Quota Exceeded). Please wait ~1 minute or upgrade quota."

            msg.contains("API_KEY_INVALID", ignoreCase = true) ||
            msg.contains("API key", ignoreCase = true) ->
                "Gemini API key is invalid or not configured."

            msg.contains("NOT_FOUND", ignoreCase = true) ||
            msg.contains("404", ignoreCase = true) ->
                "Selected Gemini model is not supported for this API key."

            e is UnknownHostException || e is SocketTimeoutException || e is IOException ->
                "Network connection failed during AI scan."

            else -> "AI error: ${e.localizedMessage ?: msg}"
        }
    }

    private suspend fun <T> retryWithBackoff(
        maxAttempts: Int = 3,
        initialDelayMs: Long = 500L,
        factor: Double = 2.0,
        block: suspend () -> T
    ): T {
        var currentDelay = initialDelayMs
        repeat(maxAttempts - 1) { attempt ->
            try {
                return block()
            } catch (e: Exception) {
                val msg = e.message ?: ""
                if (msg.contains("404") || msg.contains("NOT_FOUND") || isQuotaError(e)) {
                    throw e
                }
                Log.w(TAG, "Attempt ${attempt + 1} failed (${e.javaClass.simpleName}: $msg). Retrying in ${currentDelay}ms...")
                delay(currentDelay.milliseconds)
                currentDelay = (currentDelay * factor).toLong()
            }
        }
        return block()
    }

    data class AIParseResult(
        val scrapedRecipe: RecipeScraper.ScrapedRecipe,
        val foodImageIndex: Int? = null
    )

    private fun String.cleanSpaces(): String =
        this.replace("ß", "ss")
            .replace("ẞ", "SS")
            .replace("\u00A0", " ")
            .replace("\\s+".toRegex(), " ")
            .trim()

    suspend fun parseFromImages(bitmaps: List<Bitmap>): AIParseResult? {
        if (bitmaps.isEmpty()) return null
        lastFailureReason = null

        var lastException: Exception? = null
        for (modelName in MULTIMODAL_MODEL_CANDIDATES) {
            try {
                val model = getGenerativeModel(modelName)
                val response = retryWithBackoff {
                    model.generateContent(
                        content {
                            bitmaps.forEach { image(it) }
                            text(
                                """
                                Analyze these images of a recipe or cookbook pages. I am providing ${bitmaps.size} images.
                                Extract all information and return a raw JSON object with the following format. 
                                Retain the exact original language of the text as found in the source documents. 
                                Ensure all text uses clean, single spacing between words and sentences without double spaces.
                                For unit, if there is no specific unit (e.g. items, pieces, or count), use an empty string "" instead of null or "null".
                                
                                Identify which image (by 0-based index) is the best photo of the finished dish/food.
                                
                                JSON template:
                                {
                                  "title": "Recipe Title",
                                  "description": "Short summary",
                                  "category": "Category",
                                  "defaultServings": 4,
                                  "foodImageIndex": 0,
                                  "ingredients": [
                                    { "name": "Ingredient name", "quantity": 250.0, "unit": "g" }
                                  ],
                                  "steps": [
                                    "Instruction step text 1",
                                    "Instruction step text 2"
                                  ]
                                }
                                Return ONLY the raw JSON content, no markdown blocks.
                                """.trimIndent()
                            )
                        }
                    )
                }

                val jsonText = response.text?.trim() ?: continue
                val result = parseJsonToResult(jsonText)
                if (result != null) {
                    Log.d(TAG, "Successfully parsed images using model: $modelName")
                    lastFailureReason = null
                    return result
                }
            } catch (e: Exception) {
                lastException = e
                if (lastFailureReason == null || isQuotaError(e)) {
                    lastFailureReason = formatError(e)
                }
                Log.w(TAG, "Model $modelName failed for image parsing: ${e.message}")
            }
        }

        if (lastException != null && lastFailureReason == null) {
            lastFailureReason = formatError(lastException)
        }
        return null
    }

    suspend fun parseFromWebText(text: String): RecipeScraper.ScrapedRecipe? {
        if (text.isBlank()) return null
        lastFailureReason = null

        var lastException: Exception? = null
        for (modelName in TEXT_MODEL_CANDIDATES) {
            try {
                val model = getGenerativeModel(modelName)
                val response = retryWithBackoff {
                    model.generateContent(
                        content {
                            text(
                                """
                                Analyze the following text extracted from a recipe website.
                                Extract all recipe information and return a raw JSON object with the following format. 
                                Retain the exact original language of the text.
                                Ensure all text uses clean, single spacing between words and sentences without double spaces or HTML tags.
                                For unit, if there is no specific unit (e.g. items, pieces, or count), use an empty string "" instead of null or "null".
                                
                                JSON template:
                                {
                                  "title": "Recipe Title",
                                  "description": "Short summary",
                                  "category": "Category",
                                  "defaultServings": 4,
                                  "ingredients": [
                                    { "name": "Ingredient name", "quantity": 250.0, "unit": "g" }
                                  ],
                                  "steps": [
                                    "Instruction step text 1",
                                    "Instruction step text 2"
                                  ]
                                }
                                Return ONLY the raw JSON content, no markdown blocks.
                                
                                Website Text:
                                $text
                                """.trimIndent()
                            )
                        }
                    )
                }

                val jsonText = response.text?.trim() ?: continue
                val result = parseJsonToResult(jsonText)
                if (result?.scrapedRecipe != null) {
                    Log.d(TAG, "Successfully parsed web text using model: $modelName")
                    lastFailureReason = null
                    return result.scrapedRecipe
                }
            } catch (e: Exception) {
                lastException = e
                if (lastFailureReason == null || isQuotaError(e)) {
                    lastFailureReason = formatError(e)
                }
                Log.w(TAG, "Model $modelName failed for web text parsing: ${e.message}")
            }
        }

        if (lastException != null && lastFailureReason == null) {
            lastFailureReason = formatError(lastException)
        }
        return null
    }

    private fun parseJsonToResult(jsonText: String): AIParseResult? {
        return try {
            val startIndex = jsonText.indexOf('{')
            val endIndex = jsonText.lastIndexOf('}')
            if (startIndex == -1 || endIndex == -1 || startIndex >= endIndex) {
                Log.e(TAG, "No valid JSON object bounds found in response: $jsonText")
                return null
            }

            val cleanJson = jsonText.substring(startIndex, endIndex + 1).trim()
            val json = JSONObject(cleanJson)
            
            val title = json.optString("title", "AI Imported Recipe").cleanSpaces()
            val description = json.optString("description", "").cleanSpaces()
            val category = json.optString("category", "General").cleanSpaces()
            val servings = json.optInt("defaultServings", 4)
            val foodImageIndex = if (json.has("foodImageIndex") && !json.isNull("foodImageIndex")) json.getInt("foodImageIndex") else null

            val recipe = Recipe(
                title = title,
                description = description,
                category = category,
                defaultServings = servings,
                imagePath = null
            )

            val ingredientsList = mutableListOf<Ingredient>()
            val jsonIngredients = json.optJSONArray("ingredients")
            if (jsonIngredients != null) {
                for (i in 0 until jsonIngredients.length()) {
                    val item = jsonIngredients.optJSONObject(i) ?: continue
                    
                    val rawName = if (item.isNull("name")) "" else item.optString("name", "").cleanSpaces()
                    val cleanName = if (rawName.equals("null", ignoreCase = true)) "" else rawName

                    val rawUnit = if (item.isNull("unit")) "" else item.optString("unit", "").cleanSpaces()
                    val cleanUnit = if (
                        rawUnit.equals("null", ignoreCase = true) ||
                        rawUnit.equals("none", ignoreCase = true) ||
                        rawUnit.equals("n/a", ignoreCase = true)
                    ) "" else rawUnit

                    ingredientsList.add(
                        Ingredient(
                            recipeId = 0,
                            name = cleanName,
                            quantity = item.optDouble("quantity", 0.0),
                            unit = cleanUnit
                        )
                    )
                }
            }

            val stepsList = mutableListOf<InstructionStep>()
            val jsonSteps = json.optJSONArray("steps")
            if (jsonSteps != null) {
                for (i in 0 until jsonSteps.length()) {
                    val rawStepText = jsonSteps.optString(i, "").cleanSpaces()
                    if (rawStepText.isNotBlank()) {
                        stepsList.add(
                            InstructionStep(
                                recipeId = 0,
                                stepNumber = stepsList.size + 1,
                                instructionText = rawStepText
                            )
                        )
                    }
                }
            }

            AIParseResult(
                scrapedRecipe = RecipeScraper.ScrapedRecipe(recipe, ingredientsList, stepsList),
                foodImageIndex = foodImageIndex
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing JSON to result. Text was: $jsonText", e)
            null
        }
    }
}
