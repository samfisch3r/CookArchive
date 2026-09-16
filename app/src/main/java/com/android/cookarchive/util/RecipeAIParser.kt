package com.android.cookarchive.util

import android.graphics.Bitmap
import com.android.cookarchive.BuildConfig
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.content
import com.android.cookarchive.data.entities.Ingredient
import com.android.cookarchive.data.entities.InstructionStep
import com.android.cookarchive.data.entities.Recipe
import org.json.JSONObject

object RecipeAIParser {

    private val generativeModel: GenerativeModel by lazy {
        GenerativeModel(
            modelName = "gemini-1.5-flash",
            apiKey = BuildConfig.GEMINI_API_KEY
        )
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
        
        return try {
            val response = generativeModel.generateContent(
                content {
                    bitmaps.forEach { image(it) }
                    text(
                        """
                        Analyze these images of a recipe or cookbook pages. I am providing ${bitmaps.size} images.
                        Extract all information and return a raw JSON object with the following format. 
                        Retain the exact original language of the text as found in the source documents. 
                        Ensure all text uses clean, single spacing between words and sentences without double spaces.
                        
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

            val jsonText = response.text?.trim() ?: return null
            val cleanJson = jsonText.removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
            
            val json = JSONObject(cleanJson)
            val title = json.optString("title", "AI Imported Recipe").cleanSpaces()
            val description = json.optString("description", "").cleanSpaces()
            val category = json.optString("category", "General").cleanSpaces()
            val servings = json.optInt("defaultServings", 4)
            val foodImageIndex = if (json.has("foodImageIndex")) json.getInt("foodImageIndex") else null

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
                    val item = jsonIngredients.getJSONObject(i)
                    ingredientsList.add(
                        Ingredient(
                            recipeId = 0,
                            name = item.optString("name", "").cleanSpaces(),
                            quantity = item.optDouble("quantity", 0.0),
                            unit = item.optString("unit", "").cleanSpaces()
                        )
                    )
                }
            }

            val stepsList = mutableListOf<InstructionStep>()
            val jsonSteps = json.optJSONArray("steps")
            if (jsonSteps != null) {
                for (i in 0 until jsonSteps.length()) {
                    val rawStepText = jsonSteps.getString(i).cleanSpaces()
                    stepsList.add(
                        InstructionStep(
                            recipeId = 0,
                            stepNumber = i + 1,
                            instructionText = rawStepText
                        )
                    )
                }
            }

            AIParseResult(
                scrapedRecipe = RecipeScraper.ScrapedRecipe(recipe, ingredientsList, stepsList),
                foodImageIndex = foodImageIndex
            )
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}
