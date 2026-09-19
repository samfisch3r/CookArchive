package com.android.cookarchive.util

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.room.withTransaction
import com.android.cookarchive.data.RecipeDatabase
import com.android.cookarchive.data.entities.Ingredient
import com.android.cookarchive.data.entities.InstructionStep
import com.android.cookarchive.data.entities.Recipe
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

object BackupUtil {

    private const val TAG = "BackupUtil"

    suspend fun exportBackup(context: Context, destUri: Uri): Boolean = withContext(Dispatchers.IO) {
        try {
            val db = RecipeDatabase.getDatabase(context)
            val dao = db.recipeDao()

            val recipes = dao.getAllRecipes().firstOrNull() ?: emptyList()

            val rootJson = JSONObject()
            rootJson.put("version", 1)
            rootJson.put("exportedAt", System.currentTimeMillis())

            val recipesArray = JSONArray()
            val imagesToZip = mutableSetOf<File>()

            recipes.forEach { recipeWithDetails ->
                val r = recipeWithDetails.recipe
                val rJson = JSONObject()
                rJson.put("id", r.id)
                rJson.put("title", r.title)
                rJson.put("description", r.description)
                rJson.put("category", r.category)
                rJson.put("defaultServings", r.defaultServings)
                rJson.put("lastCooked", r.lastCooked)
                rJson.put("cookCount", r.cookCount)

                // Main Image
                if (!r.imagePath.isNullOrBlank()) {
                    val imgFile = File(r.imagePath)
                    if (imgFile.exists()) {
                        rJson.put("imageFileName", imgFile.name)
                        imagesToZip.add(imgFile)
                    }
                }

                // Ingredients
                val ingredientsArray = JSONArray()
                recipeWithDetails.ingredients.forEach { ing ->
                    val iJson = JSONObject()
                    iJson.put("name", ing.name)
                    iJson.put("quantity", ing.quantity)
                    iJson.put("unit", ing.unit)
                    ingredientsArray.put(iJson)
                }
                rJson.put("ingredients", ingredientsArray)

                // Steps
                val stepsArray = JSONArray()
                recipeWithDetails.steps.forEach { step ->
                    val sJson = JSONObject()
                    sJson.put("stepNumber", step.stepNumber)
                    sJson.put("instructionText", step.instructionText)
                    if (!step.stepImagePath.isNullOrBlank()) {
                        val sImgFile = File(step.stepImagePath)
                        if (sImgFile.exists()) {
                            sJson.put("stepImageFileName", sImgFile.name)
                            imagesToZip.add(sImgFile)
                        }
                    }
                    stepsArray.put(sJson)
                }
                rJson.put("steps", stepsArray)

                recipesArray.put(rJson)
            }
            rootJson.put("recipes", recipesArray)

            // Write ZIP stream to destUri
            context.contentResolver.openOutputStream(destUri)?.use { outStream ->
                ZipOutputStream(outStream).use { zipOut ->
                    // 1. Write data.json formatted with indentations for easy human editing
                    val jsonBytes = rootJson.toString(2).toByteArray(Charsets.UTF_8)
                    zipOut.putNextEntry(ZipEntry("data.json"))
                    zipOut.write(jsonBytes)
                    zipOut.closeEntry()

                    // 2. Write image files
                    imagesToZip.forEach { imgFile ->
                        zipOut.putNextEntry(ZipEntry("images/${imgFile.name}"))
                        FileInputStream(imgFile).use { fis ->
                            fis.copyTo(zipOut)
                        }
                        zipOut.closeEntry()
                    }
                }
            }
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to export backup", e)
            false
        }
    }

    suspend fun importBackup(context: Context, srcUri: Uri): Boolean = withContext(Dispatchers.IO) {
        try {
            val db = RecipeDatabase.getDatabase(context)
            val dao = db.recipeDao()
            val imagesDir = File(context.filesDir, "recipe_images").apply { if (!exists()) mkdirs() }

            var jsonText: String? = null
            val extractedImages = mutableMapOf<String, File>()

            // 1. Try reading as a ZIP archive (handling folder subdirectories)
            try {
                context.contentResolver.openInputStream(srcUri)?.use { inputStream ->
                    ZipInputStream(inputStream).use { zipIn ->
                        var entry = zipIn.nextEntry
                        while (entry != null) {
                            val entryName = entry.name
                            if (entryName == "data.json" || entryName.endsWith("/data.json")) {
                                jsonText = zipIn.bufferedReader().use { it.readText() }
                            } else if ((entryName.startsWith("images/") || entryName.contains("/images/")) && !entry.isDirectory) {
                                val fileName = File(entryName).name
                                val destFile = File(imagesDir, fileName)
                                FileOutputStream(destFile).use { fos ->
                                    zipIn.copyTo(fos)
                                }
                                extractedImages[fileName] = destFile
                            }
                            zipIn.closeEntry()
                            entry = zipIn.nextEntry
                        }
                    }
                }
            } catch (e: Exception) {
                Log.d(TAG, "Not a zip file or zip read failed: ${e.message}")
            }

            // 2. Fallback: If zip stream produced no JSON, try reading srcUri directly as a raw JSON file
            if (jsonText.isNullOrBlank()) {
                try {
                    context.contentResolver.openInputStream(srcUri)?.use { inputStream ->
                        val text = inputStream.bufferedReader().use { it.readText() }.trim()
                        if (text.startsWith("{") && text.endsWith("}")) {
                            jsonText = text
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to read direct JSON", e)
                }
            }

            val validJsonText = jsonText
            if (validJsonText.isNullOrBlank()) {
                Log.e(TAG, "No valid JSON content found in backup file")
                return@withContext false
            }

            val rootJson = JSONObject(validJsonText)
            val recipesArray = rootJson.optJSONArray("recipes") ?: JSONArray()

            // Map existing recipes by ID and Title (case-insensitive) for smart in-place updates
            val existingRecipes = dao.getAllRecipesListSync()
            val existingIdMap = existingRecipes.associateBy { it.recipe.id }
            val existingTitleMap = existingRecipes.associateBy { it.recipe.title.trim().lowercase() }

            for (i in 0 until recipesArray.length()) {
                val rJson = recipesArray.getJSONObject(i)
                val inputId = if (rJson.has("id") && !rJson.isNull("id")) rJson.getLong("id") else 0L
                val title = rJson.optString("title", "Imported Recipe").trim()
                val cleanTitleKey = title.lowercase()

                // Match by ID first (allows title editing), fall back to matching by Title
                val existingRecipeDetails = existingIdMap[inputId] ?: existingTitleMap[cleanTitleKey]

                val imageFileName = if (rJson.has("imageFileName") && !rJson.isNull("imageFileName")) rJson.getString("imageFileName") else null
                val localImagePath = if (!imageFileName.isNullOrBlank() && extractedImages.containsKey(imageFileName)) {
                    extractedImages[imageFileName]?.absolutePath
                } else existingRecipeDetails?.recipe?.imagePath

                val targetId = existingRecipeDetails?.recipe?.id ?: 0L

                val recipe = Recipe(
                    id = targetId,
                    title = title,
                    description = rJson.optString("description", "").trim(),
                    category = rJson.optString("category", "General").trim(),
                    defaultServings = rJson.optInt("defaultServings", 4),
                    lastCooked = if (rJson.has("lastCooked") && !rJson.isNull("lastCooked")) rJson.getLong("lastCooked") else (existingRecipeDetails?.recipe?.lastCooked ?: 0L),
                    cookCount = rJson.optInt("cookCount", existingRecipeDetails?.recipe?.cookCount ?: 0),
                    imagePath = localImagePath
                )

                val ingredientsList = mutableListOf<Ingredient>()
                val jsonIngredients = rJson.optJSONArray("ingredients") ?: JSONArray()
                for (j in 0 until jsonIngredients.length()) {
                    val iObj = jsonIngredients.getJSONObject(j)
                    ingredientsList.add(
                        Ingredient(
                            recipeId = 0,
                            name = iObj.optString("name", "").trim(),
                            quantity = iObj.optDouble("quantity", 0.0),
                            unit = iObj.optString("unit", "").trim()
                        )
                    )
                }

                val stepsList = mutableListOf<InstructionStep>()
                val jsonSteps = rJson.optJSONArray("steps") ?: JSONArray()
                for (k in 0 until jsonSteps.length()) {
                    val sObj = jsonSteps.getJSONObject(k)
                    val stepImageFileName = if (sObj.has("stepImageFileName") && !sObj.isNull("stepImageFileName")) sObj.getString("stepImageFileName") else null
                    val stepImagePath = if (!stepImageFileName.isNullOrBlank() && extractedImages.containsKey(stepImageFileName)) {
                        extractedImages[stepImageFileName]?.absolutePath
                    } else null

                    stepsList.add(
                        InstructionStep(
                            recipeId = 0,
                            stepNumber = sObj.optInt("stepNumber", k + 1),
                            instructionText = sObj.optString("instructionText", "").trim(),
                            stepImagePath = stepImagePath
                        )
                    )
                }

                // Update existing recipe in place OR insert new recipe inside transaction
                db.withTransaction {
                    val newRecipeId = dao.insertRecipe(recipe)
                    val finalRecipeId = if (targetId != 0L) targetId else newRecipeId

                    dao.deleteIngredientsByRecipeId(finalRecipeId)
                    dao.insertIngredients(ingredientsList.map { it.copy(recipeId = finalRecipeId) })

                    dao.deleteStepsByRecipeId(finalRecipeId)
                    dao.insertSteps(stepsList.map { it.copy(recipeId = finalRecipeId) })
                }
            }

            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to import backup", e)
            false
        }
    }
}
