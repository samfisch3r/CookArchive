package com.android.cookarchive.ui

import android.app.Application
import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.room.withTransaction
import com.android.cookarchive.data.RecipeDatabase
import com.android.cookarchive.data.entities.Ingredient
import com.android.cookarchive.data.entities.InstructionStep
import com.android.cookarchive.data.entities.MealPlan
import com.android.cookarchive.data.entities.Recipe
import com.android.cookarchive.data.entities.RecipeWithDetails
import com.android.cookarchive.data.entities.ShoppingListItem
import com.android.cookarchive.ui.screens.getNext5Days
import com.android.cookarchive.util.BackupUtil
import com.android.cookarchive.util.ImageStorage
import com.android.cookarchive.util.RecipeAIParser
import com.android.cookarchive.util.RecipeScraper
import com.android.cookarchive.util.WebSyncUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class RecipeViewModel(application: Application) : AndroidViewModel(application) {

    private val db = RecipeDatabase.getDatabase(application)
    private val dao = db.recipeDao()

    val allRecipes = dao.getAllRecipes()
    val allMealPlans = dao.getAllMealPlans()
    val allShoppingItems = dao.getAllShoppingItems()

    private val _currentRecipe = MutableStateFlow<RecipeWithDetails?>(null)
    val currentRecipe: StateFlow<RecipeWithDetails?> = _currentRecipe.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _recipeWishes = MutableStateFlow<List<WebSyncUtil.MealWish>>(emptyList())
    val recipeWishes: StateFlow<List<WebSyncUtil.MealWish>> = _recipeWishes.asStateFlow()

    private val _errorEvents = MutableSharedFlow<String>()
    val errorEvents: SharedFlow<String> = _errorEvents.asSharedFlow()

    private var loadRecipeJob: Job? = null

    init {
        processPastMealPlans()
        fetchRecipeWishes()
    }

    fun fetchRecipeWishes() {
        viewModelScope.launch {
            val wishes = withContext(Dispatchers.IO) {
                WebSyncUtil.fetchWishesFromWeb()
            }
            _recipeWishes.value = wishes
        }
    }

    fun dismissWish(wishId: String) {
        viewModelScope.launch {
            val updatedWishes = _recipeWishes.value.filter { it.id != wishId }
            _recipeWishes.value = updatedWishes
            withContext(Dispatchers.IO) {
                val days = getNext5Days()
                val allPlans = dao.getAllMealPlans().firstOrNull() ?: emptyList()
                val mealPlansByDate = allPlans.groupBy { it.mealPlan.date }
                val recipesList = dao.getAllRecipes().firstOrNull() ?: emptyList()
                WebSyncUtil.syncMealPlanToWeb(days, mealPlansByDate, recipesList, updatedWishes)
            }
        }
    }

    private fun processPastMealPlans() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                val todayIso = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)
                
                db.withTransaction {
                    val pastPlans = dao.getPastMealPlansSync(todayIso)
                    if (pastPlans.isNotEmpty()) {
                        pastPlans.forEach { pastPlan ->
                            try {
                                val pastDate = LocalDate.parse(pastPlan.mealPlan.date, DateTimeFormatter.ISO_LOCAL_DATE)
                                val lastCookedMs = pastDate.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
                                pastPlan.mealPlan.recipeId?.let { recipeId ->
                                    dao.updateRecipeCookStats(recipeId, lastCookedMs)
                                }
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        }
                        dao.deletePastMealPlans(todayIso)
                    }
                }
                syncMealPlanToWeb()
            }
        }
    }

    fun loadRecipe(id: Long) {
        loadRecipeJob?.cancel()
        loadRecipeJob = viewModelScope.launch {
            dao.getRecipeById(id).collect { recipe ->
                _currentRecipe.value = recipe
            }
        }
    }

    fun scrapeAndSaveRecipe(url: String) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val normalizedUrl = RecipeScraper.normalizeUrl(url)

                var scraped = withContext(Dispatchers.IO) {
                    RecipeScraper.scrapeFromUrl(normalizedUrl)
                }

                if (scraped == null || (scraped.ingredients.isEmpty() && scraped.steps.isEmpty())) {
                    val webpageData = withContext(Dispatchers.IO) {
                        RecipeScraper.fetchWebpageData(normalizedUrl)
                    }
                    if (webpageData != null && webpageData.text.isNotBlank()) {
                        val aiScraped = withContext(Dispatchers.IO) {
                            RecipeAIParser.parseFromWebText(webpageData.text)
                        }
                        if (aiScraped != null) {
                            scraped = aiScraped
                            if (scraped.recipe.imagePath.isNullOrBlank() && !webpageData.mainImageUrl.isNullOrBlank()) {
                                scraped = scraped.copy(recipe = scraped.recipe.copy(imagePath = webpageData.mainImageUrl))
                            }
                        }
                    }
                }

                if (scraped != null && (scraped.ingredients.isNotEmpty() || scraped.steps.isNotEmpty())) {
                    var finalRecipe = scraped.recipe
                    
                    val imageUrl = finalRecipe.imagePath
                    if (!imageUrl.isNullOrBlank()) {
                        val localPath = withContext(Dispatchers.IO) {
                            ImageStorage.saveImageFromUrl(getApplication(), imageUrl)
                        }
                        if (localPath != null) {
                            finalRecipe = finalRecipe.copy(imagePath = localPath)
                        }
                    }
                    
                    withContext(Dispatchers.IO) {
                        db.withTransaction {
                            val recipeId = dao.insertRecipe(finalRecipe)
                            dao.insertIngredients(scraped.ingredients.map { it.copy(recipeId = recipeId) })
                            dao.insertSteps(scraped.steps.map { it.copy(recipeId = recipeId) })
                        }
                    }
                } else {
                    val reason = RecipeAIParser.lastFailureReason
                    val errorText = if (!reason.isNullOrBlank()) {
                        "Could not parse recipe: $reason"
                    } else {
                        "Could not parse recipe from URL. No structured data found and AI fallback failed."
                    }
                    _errorEvents.emit(errorText)
                }
            } catch (e: Exception) {
                _errorEvents.emit("Failed to import recipe: ${e.message}")
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun importRecipeFromImages(bitmaps: List<Bitmap>) {
        if (bitmaps.isEmpty()) return
        
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val result = withContext(Dispatchers.IO) {
                    RecipeAIParser.parseFromImages(bitmaps)
                }
                
                if (result != null) {
                    val scraped = result.scrapedRecipe
                    
                    val heroIndex = result.foodImageIndex?.coerceIn(0, bitmaps.size - 1) ?: 0
                    val heroBitmap = bitmaps[heroIndex]
                    val localImagePath = ImageStorage.saveBitmap(getApplication(), heroBitmap)
                    
                    val finalRecipe = scraped.recipe.copy(imagePath = localImagePath)

                    withContext(Dispatchers.IO) {
                        db.withTransaction {
                            val recipeId = dao.insertRecipe(finalRecipe)
                            dao.insertIngredients(scraped.ingredients.map { it.copy(recipeId = recipeId) })
                            dao.insertSteps(scraped.steps.map { it.copy(recipeId = recipeId) })
                        }
                    }
                } else {
                    val reason = RecipeAIParser.lastFailureReason
                    val errorText = if (!reason.isNullOrBlank()) {
                        "AI Scan failed: $reason"
                    } else {
                        "AI Scan failed. Please check your API key and image clarity."
                    }
                    _errorEvents.emit(errorText)
                }
            } catch (e: Exception) {
                _errorEvents.emit("AI Import error: ${e.message}")
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun updateRecipe(recipe: Recipe, ingredients: List<Ingredient>, steps: List<InstructionStep>) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                db.withTransaction {
                    dao.insertRecipe(recipe)
                    dao.deleteIngredientsByRecipeId(recipe.id)
                    dao.insertIngredients(ingredients.map { it.copy(recipeId = recipe.id) })
                    dao.deleteStepsByRecipeId(recipe.id)
                    dao.insertSteps(steps.map { it.copy(recipeId = recipe.id) })
                }
            }
            loadRecipe(recipe.id)
            syncMealPlanToWeb()
        }
    }

    fun deleteRecipe(recipe: Recipe) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                ImageStorage.deleteImage(recipe.imagePath)
                
                val details = dao.getRecipeById(recipe.id).firstOrNull()
                details?.steps?.forEach { step ->
                    ImageStorage.deleteImage(step.stepImagePath)
                }
                
                dao.deleteRecipe(recipe)
            }
            syncMealPlanToWeb()
        }
    }

    fun addCustomMealPlan(customTitle: String, date: String) {
        if (customTitle.isBlank()) return
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                dao.insertMealPlan(
                    MealPlan(
                        recipeId = null,
                        customTitle = customTitle.trim(),
                        date = date,
                        mealType = "Custom"
                    )
                )
            }
            syncMealPlanToWeb()
        }
    }

    fun addMealPlan(
        recipe: Recipe,
        date: String,
        selectedIngredients: List<Ingredient>,
        scaleFactor: Double = 1.0
    ) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                db.withTransaction {
                    dao.insertMealPlan(
                        MealPlan(
                            recipeId = recipe.id,
                            date = date,
                            mealType = "General"
                        )
                    )

                    val existingItems = dao.getAllShoppingItemsListSync()

                    selectedIngredients.forEach { selected ->
                        val scaledQty = selected.quantity * scaleFactor
                        val cleanName = selected.name.trim()
                        val cleanUnit = selected.unit.trim()

                        val match = existingItems.find { item ->
                            !item.isBought &&
                                item.name.equals(cleanName, ignoreCase = true) &&
                                item.unit.equals(cleanUnit, ignoreCase = true)
                        }

                        if (match != null) {
                            val updatedItem = match.copy(quantity = match.quantity + scaledQty)
                            dao.updateShoppingItem(updatedItem)
                        } else {
                            dao.insertShoppingItem(
                                ShoppingListItem(
                                    name = cleanName,
                                    quantity = scaledQty,
                                    unit = cleanUnit,
                                    originRecipe = recipe.title
                                )
                            )
                        }
                    }
                }
            }
            syncMealPlanToWeb()
        }
    }

    fun moveMealPlan(mealPlanId: Long, newDate: String) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                dao.updateMealPlanDate(mealPlanId, newDate)
            }
            syncMealPlanToWeb()
        }
    }

    fun deleteMealPlan(mealPlan: MealPlan) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                dao.deleteMealPlan(mealPlan)
            }
            syncMealPlanToWeb()
        }
    }

    fun toggleShoppingItemBought(item: ShoppingListItem) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                dao.updateShoppingItem(item.copy(isBought = !item.isBought))
            }
        }
    }

    fun addCustomShoppingItem(name: String, quantity: Double, unit: String) {
        if (name.isBlank()) return
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                dao.insertShoppingItem(
                    ShoppingListItem(
                        name = name.trim(),
                        quantity = quantity,
                        unit = unit.trim(),
                        originRecipe = null
                    )
                )
            }
        }
    }

    fun updateShoppingItem(item: ShoppingListItem) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                dao.updateShoppingItem(item)
            }
        }
    }

    fun deleteShoppingItem(item: ShoppingListItem) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                dao.deleteShoppingItem(item)
            }
        }
    }

    fun clearBoughtShoppingItems() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                dao.clearBoughtShoppingItems()
            }
        }
    }

    fun exportBackup(destUri: Uri, onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            val success = BackupUtil.exportBackup(getApplication(), destUri)
            onResult(success)
        }
    }

    fun importBackup(srcUri: Uri, onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            _isLoading.value = true
            val success = BackupUtil.importBackup(getApplication(), srcUri)
            _isLoading.value = false
            if (success) {
                syncMealPlanToWeb()
            }
            onResult(success)
        }
    }

    private suspend fun syncMealPlanToWeb() {
        try {
            val days = getNext5Days()
            val allPlans = dao.getAllMealPlans().firstOrNull() ?: emptyList()
            val mealPlansByDate = allPlans.groupBy { it.mealPlan.date }
            val recipesList = dao.getAllRecipes().firstOrNull() ?: emptyList()
            WebSyncUtil.syncMealPlanToWeb(days, mealPlansByDate, recipesList, _recipeWishes.value)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
