package com.android.cookarchive.util

import android.util.Log
import com.android.cookarchive.data.entities.Ingredient
import com.android.cookarchive.data.entities.InstructionStep
import com.android.cookarchive.data.entities.Recipe
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener
import org.jsoup.Jsoup
import org.jsoup.nodes.Document

object RecipeScraper {
    data class ScrapedRecipe(
        val recipe: Recipe,
        val ingredients: List<Ingredient>,
        val steps: List<InstructionStep>
    )

    data class WebpageData(
        val text: String,
        val mainImageUrl: String?
    )

    private const val TAG = "RecipeScraper"
    private const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36"

    fun normalizeUrl(url: String): String {
        val trimmed = url.trim()
        return if (!trimmed.startsWith("http://", ignoreCase = true) && !trimmed.startsWith("https://", ignoreCase = true)) {
            "https://$trimmed"
        } else {
            trimmed
        }
    }

    private fun String.cleanSpaces(): String =
        Jsoup.parse(this).text()
            .replace("ß", "ss")
            .replace("ẞ", "SS")
            .replace("\u00A0", " ")
            .replace("\\s+".toRegex(), " ")
            .trim()

    fun scrapeFromUrl(rawUrl: String): ScrapedRecipe? {
        val url = normalizeUrl(rawUrl)
        return try {
            val doc = Jsoup.connect(url)
                .userAgent(USER_AGENT)
                .timeout(12000)
                .followRedirects(true)
                .get()
            
            // Try structured JSON-LD first
            val jsonLdTags = doc.select("script[type=application/ld+json]")
            for (tag in jsonLdTags) {
                var jsonString = tag.data().ifBlank { tag.html() }.trim()
                if (jsonString.startsWith("<!--")) {
                    jsonString = jsonString.removePrefix("<!--").removeSuffix("-->").trim()
                }
                if (jsonString.isBlank()) continue

                val jsonToken = try {
                    JSONTokener(jsonString).nextValue()
                } catch (_: Exception) {
                    null
                } ?: continue

                val found = findRecipeInJson(jsonToken)
                if (found != null) {
                    var scraped = parseRecipeJson(found)
                    if (scraped.ingredients.isNotEmpty() || scraped.steps.isNotEmpty()) {
                        if (scraped.recipe.imagePath.isNullOrBlank()) {
                            val fallbackImage = extractMainImageUrl(doc)
                            if (!fallbackImage.isNullOrBlank()) {
                                scraped = scraped.copy(recipe = scraped.recipe.copy(imagePath = fallbackImage))
                            }
                        }
                        return scraped
                    }
                }
            }

            null
        } catch (e: Exception) {
            Log.e(TAG, "Error scraping URL: $url", e)
            null
        }
    }

    fun fetchWebpageData(rawUrl: String): WebpageData? {
        val url = normalizeUrl(rawUrl)
        return try {
            val doc = Jsoup.connect(url)
                .userAgent(USER_AGENT)
                .timeout(12000)
                .followRedirects(true)
                .get()
            val fullText = doc.body().text()
            val text = if (fullText.length > 20000) fullText.substring(0, 20000) else fullText
            val mainImageUrl = extractMainImageUrl(doc)

            WebpageData(text = text, mainImageUrl = mainImageUrl)
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching webpage data: $url", e)
            null
        }
    }

    fun extractMainImageUrl(doc: Document): String? {
        val ogImage = doc.selectFirst("meta[property=og:image]")?.attr("content")
            ?: doc.selectFirst("meta[name=og:image]")?.attr("content")
        if (!ogImage.isNullOrBlank()) {
            val formatted = formatImageUrl(ogImage)
            if (formatted != null) return formatted
        }

        val twitterImage = doc.selectFirst("meta[name=twitter:image]")?.attr("content")
            ?: doc.selectFirst("meta[property=twitter:image]")?.attr("content")
        if (!twitterImage.isNullOrBlank()) {
            val formatted = formatImageUrl(twitterImage)
            if (formatted != null) return formatted
        }

        val linkImage = doc.selectFirst("link[rel=image_src]")?.attr("href")
        if (!linkImage.isNullOrBlank()) {
            val formatted = formatImageUrl(linkImage)
            if (formatted != null) return formatted
        }

        val schemaImage = doc.selectFirst("[itemprop=image]")?.let {
            it.attr("abs:src").ifBlank { it.attr("src") }.ifBlank { it.attr("content") }
        }
        if (!schemaImage.isNullOrBlank()) {
            val formatted = formatImageUrl(schemaImage)
            if (formatted != null) return formatted
        }

        val imgElement = doc.select("article img[src], main img[src], .recipe img[src], .entry-content img[src]").firstOrNull { img ->
            val src = img.attr("abs:src").ifBlank { img.attr("src") }
            src.isNotBlank() && !src.endsWith(".svg") && !src.contains("logo") && !src.contains("icon") && !src.contains("avatar")
        }
        if (imgElement != null) {
            val src = imgElement.attr("abs:src").ifBlank { imgElement.attr("src") }
            val formatted = formatImageUrl(src)
            if (formatted != null) return formatted
        }

        return null
    }

    private fun formatImageUrl(rawUrl: String): String? {
        val trimmed = rawUrl.trim()
        return when {
            trimmed.startsWith("http://", ignoreCase = true) || trimmed.startsWith("https://", ignoreCase = true) -> trimmed
            trimmed.startsWith("//") -> "https:$trimmed"
            else -> null
        }
    }

    private fun isRecipeType(typeObj: Any?): Boolean {
        return when (typeObj) {
            is String -> typeObj.equals("Recipe", ignoreCase = true) ||
                    typeObj.endsWith("/Recipe", ignoreCase = true) ||
                    typeObj.endsWith("#Recipe", ignoreCase = true)
            is JSONArray -> {
                for (i in 0 until typeObj.length()) {
                    val str = typeObj.optString(i)
                    if (str.equals("Recipe", ignoreCase = true) ||
                        str.endsWith("/Recipe", ignoreCase = true) ||
                        str.endsWith("#Recipe", ignoreCase = true)
                    ) return true
                }
                false
            }
            else -> false
        }
    }

    private fun findRecipeInJson(json: Any?): JSONObject? {
        when (json) {
            is JSONObject -> {
                val typeObj = json.opt("@type") ?: json.opt("type")
                if (isRecipeType(typeObj)) return json

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
        val category = extractCategory(json)
        val servings = extractServings(json)
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
            ?: json.optJSONArray("ingredients")
        if (ingredientsSource != null) {
            for (i in 0 until ingredientsSource.length()) {
                val line = ingredientsSource.optString(i)
                if (line.isNotBlank()) {
                    ingredients.add(parseIngredientLine(line))
                }
            }
        }

        val steps = mutableListOf<InstructionStep>()
        val instructionsSource = json.opt("recipeInstructions")
        extractStepsOnlyText(instructionsSource, steps)

        return ScrapedRecipe(recipe, ingredients, steps)
    }

    private fun extractCategory(json: JSONObject): String {
        return when (val catObj = json.opt("recipeCategory")) {
            is String -> catObj.cleanSpaces()
            is JSONArray -> {
                val list = mutableListOf<String>()
                for (i in 0 until catObj.length()) {
                    val s = catObj.optString(i).cleanSpaces()
                    if (s.isNotBlank()) list.add(s)
                }
                if (list.isNotEmpty()) list.first() else "Allgemein"
            }
            else -> "Allgemein"
        }
    }

    private fun extractServings(json: JSONObject): Int {
        val raw = when (val yieldObj = json.opt("recipeYield")) {
            is JSONArray -> if (yieldObj.length() > 0) yieldObj.opt(0).toString() else "4"
            else -> yieldObj?.toString() ?: "4"
        }
        val digits = raw.filter { it.isDigit() }
        return digits.toIntOrNull() ?: 4
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
                } else {
                    val text = source.optString("text").ifBlank { source.optString("description") }.cleanSpaces()
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
            is String -> formatImageUrl(image)
            is JSONArray -> if (image.length() > 0) extractImageUrl(image.get(0)) else null
            is JSONObject -> {
                val url = image.optString("url", image.optString("contentUrl", ""))
                extractImageUrl(url)
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

        if (unit.equals("null", ignoreCase = true) || unit.equals("none", ignoreCase = true) || unit.equals("n/a", ignoreCase = true)) {
            unit = ""
        }

        return Ingredient(recipeId = 0, name = name, quantity = quantity, unit = unit)
    }
}
