package com.android.cookarchive.util

import com.android.cookarchive.data.entities.Ingredient
import com.android.cookarchive.data.entities.InstructionStep
import com.android.cookarchive.data.entities.Recipe
import org.json.JSONArray
import org.json.JSONObject
import org.jsoup.Jsoup

object RecipeScraper {
    data class ScrapedRecipe(
        val recipe: Recipe,
        val ingredients: List<Ingredient>,
        val steps: List<InstructionStep>
    )

    private const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36"

    private fun String.cleanSpaces(): String =
        this.replace("ß", "ss")
            .replace("ẞ", "SS")
            .replace("\u00A0", " ")
            .replace("\\s+".toRegex(), " ")
            .trim()

    fun scrapeFromUrl(url: String): ScrapedRecipe? {
        return try {
            val doc = Jsoup.connect(url)
                .userAgent(USER_AGENT)
                .timeout(10000)
                .get()
            
            val jsonLdTags = doc.select("script[type=application/ld+json]")
            for (tag in jsonLdTags) {
                val jsonString = tag.html()
                val json = try { JSONObject(jsonString) } catch (e: Exception) { null } ?: continue
                val found = findRecipeInJson(json)
                if (found != null) return parseRecipeJson(found)
            }
            
            null
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun findRecipeInJson(json: Any?): JSONObject? {
        when (json) {
            is JSONObject -> {
                if (json.optString("@type") == "Recipe" || json.optString("type") == "Recipe") return json
                val keys = json.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    val result = findRecipeInJson(json.opt(key))
                    if (result != null) return result
                }
            }
            is JSONArray -> {
                for (i in 0 until json.length()) {
                    val result = findRecipeInJson(json.opt(i))
                    if (result != null) return result
                }
            }
        }
        return null
    }

    private fun parseRecipeJson(json: JSONObject): ScrapedRecipe {
        val title = json.optString("name", "Unbekanntes Rezept").cleanSpaces()
        val description = json.optString("description", "").cleanSpaces()
        val category = json.optString("recipeCategory", "Allgemein").cleanSpaces()
        
        val servingsStr = json.optString("recipeYield", "4")
        val servings = servingsStr.filter { it.isDigit() }.toIntOrNull() ?: 4
        
        val imageUrl = extractImageUrl(json.opt("image"))

        val recipe = Recipe(
            title = title,
            description = description,
            category = category,
            defaultServings = servings,
            imagePath = imageUrl
        )

        val ingredients = mutableListOf<Ingredient>()
        val ingredientsSource = json.optJSONArray("recipeIngredient")
        if (ingredientsSource != null) {
            for (i in 0 until ingredientsSource.length()) {
                val line = ingredientsSource.getString(i)
                ingredients.add(parseIngredientLine(line))
            }
        }

        val steps = mutableListOf<InstructionStep>()
        val instructionsSource = json.opt("recipeInstructions")
        extractStepsOnlyText(instructionsSource, steps)

        return ScrapedRecipe(recipe, ingredients, steps)
    }

    private fun extractStepsOnlyText(source: Any?, result: MutableList<InstructionStep>) {
        when (source) {
            is JSONArray -> {
                for (i in 0 until source.length()) {
                    extractStepsOnlyText(source.get(i), result)
                }
            }
            is JSONObject -> {
                if (source.has("itemListElement")) {
                    extractStepsOnlyText(source.opt("itemListElement"), result)
                } else if (source.has("text")) {
                    val text = source.optString("text").cleanSpaces()
                    if (text.isNotBlank()) {
                        result.add(InstructionStep(
                            recipeId = 0,
                            stepNumber = result.size + 1,
                            instructionText = text
                        ))
                    }
                }
            }
            is String -> {
                val text = source.cleanSpaces()
                if (text.isNotBlank()) {
                    result.add(InstructionStep(
                        recipeId = 0,
                        stepNumber = result.size + 1,
                        instructionText = text
                    ))
                }
            }
        }
    }

    private fun extractImageUrl(image: Any?): String? {
        return when (image) {
            is String -> if (image.startsWith("http")) image else null
            is JSONArray -> if (image.length() > 0) extractImageUrl(image.get(0)) else null
            is JSONObject -> {
                val url = image.optString("url", image.optString("contentUrl", ""))
                if (url.startsWith("http")) url else null
            }
            else -> null
        }
    }

    private val COMMON_UNITS = setOf(
        "g", "kg", "ml", "l", "tsp", "tbsp", "cup", "cups", "oz", "lb", "lbs",
        "pinch", "pinches", "clove", "cloves", "slice", "slices", "can", "cans",
        "gram", "grams", "liter", "liters", "tl", "el", "el.", "tl.", "stk", "stk.",
        "packung", "pck.", "prise", "prisen", "dose", "dosen"
    )

    private fun parseIngredientLine(line: String): Ingredient {
        val cleanedLine = line.cleanSpaces()
        val parts = cleanedLine.split("\\s+".toRegex(), limit = 3)
        var quantity = 0.0
        var unit = ""
        var name = cleanedLine

        if (parts.isNotEmpty()) {
            val q = parts[0].replace(",", ".").toDoubleOrNull()
            if (q != null) {
                quantity = q
                if (parts.size == 2) {
                    val candidateUnit = parts[1].lowercase()
                    if (COMMON_UNITS.contains(candidateUnit)) {
                        unit = parts[1].cleanSpaces()
                        name = ""
                    } else {
                        unit = ""
                        name = parts[1].cleanSpaces()
                    }
                } else if (parts.size > 2) {
                    unit = parts[1].cleanSpaces()
                    name = parts[2].cleanSpaces()
                }
            }
        }

        return Ingredient(recipeId = 0, name = name, quantity = quantity, unit = unit)
    }
}
