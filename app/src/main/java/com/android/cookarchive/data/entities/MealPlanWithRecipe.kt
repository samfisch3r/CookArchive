package com.android.cookarchive.data.entities

import androidx.room.Embedded
import androidx.room.Relation

data class MealPlanWithRecipe(
    @Embedded val mealPlan: MealPlan,
    @Relation(
        entity = Recipe::class,
        parentColumn = "recipeId",
        entityColumn = "id"
    )
    val recipeWithDetails: RecipeWithDetails? = null
) {
    val displayTitle: String
        get() = mealPlan.customTitle?.ifBlank { null }
            ?: recipeWithDetails?.recipe?.title
            ?: "Custom Meal"

    val displayCategory: String
        get() = if (!mealPlan.customTitle.isNullOrBlank()) "Note"
            else recipeWithDetails?.recipe?.category ?: "General"

    val isRecipe: Boolean
        get() = recipeWithDetails != null
}
