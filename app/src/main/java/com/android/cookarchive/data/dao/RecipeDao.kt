package com.android.cookarchive.data.dao

import androidx.room.*
import com.android.cookarchive.data.entities.Ingredient
import com.android.cookarchive.data.entities.InstructionStep
import com.android.cookarchive.data.entities.MealPlan
import com.android.cookarchive.data.entities.MealPlanWithRecipe
import com.android.cookarchive.data.entities.Recipe
import com.android.cookarchive.data.entities.RecipeWithDetails
import com.android.cookarchive.data.entities.ShoppingListItem
import kotlinx.coroutines.flow.Flow

@Dao
interface RecipeDao {
    @Transaction
    @Query("SELECT * FROM recipes")
    fun getAllRecipes(): Flow<List<RecipeWithDetails>>

    @Transaction
    @Query("SELECT * FROM recipes")
    fun getAllRecipesListSync(): List<RecipeWithDetails>

    @Transaction
    @Query("SELECT * FROM recipes WHERE id = :id")
    fun getRecipeById(id: Long): Flow<RecipeWithDetails?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertRecipe(recipe: Recipe): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertIngredients(ingredients: List<Ingredient>): List<Long>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertSteps(steps: List<InstructionStep>): List<Long>

    @Query("DELETE FROM ingredients WHERE recipeId = :recipeId")
    fun deleteIngredientsByRecipeId(recipeId: Long)

    @Query("DELETE FROM instruction_steps WHERE recipeId = :recipeId")
    fun deleteStepsByRecipeId(recipeId: Long)

    @Delete
    fun deleteRecipe(recipe: Recipe): Int

    // Meal Plan queries
    @Transaction
    @Query("SELECT * FROM meal_plans ORDER BY date ASC")
    fun getAllMealPlans(): Flow<List<MealPlanWithRecipe>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertMealPlan(mealPlan: MealPlan): Long

    @Query("UPDATE meal_plans SET date = :newDate WHERE id = :id")
    fun updateMealPlanDate(id: Long, newDate: String)

    @Delete
    fun deleteMealPlan(mealPlan: MealPlan)

    @Transaction
    @Query("SELECT * FROM meal_plans WHERE date < :todayIso")
    fun getPastMealPlansSync(todayIso: String): List<MealPlanWithRecipe>

    @Query("DELETE FROM meal_plans WHERE date < :todayIso")
    fun deletePastMealPlans(todayIso: String)

    @Query("UPDATE recipes SET cookCount = cookCount + 1, lastCooked = :lastCookedMs WHERE id = :recipeId")
    fun updateRecipeCookStats(recipeId: Long, lastCookedMs: Long)

    // Shopping List queries
    @Query("SELECT * FROM shopping_list_items ORDER BY isBought ASC, name ASC")
    fun getAllShoppingItems(): Flow<List<ShoppingListItem>>

    @Query("SELECT * FROM shopping_list_items")
    fun getAllShoppingItemsListSync(): List<ShoppingListItem>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertShoppingItem(item: ShoppingListItem): Long

    @Update
    fun updateShoppingItem(item: ShoppingListItem)

    @Delete
    fun deleteShoppingItem(item: ShoppingListItem)

    @Query("DELETE FROM shopping_list_items WHERE isBought = 1")
    fun clearBoughtShoppingItems()
}
