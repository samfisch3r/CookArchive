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
    val recipeWithDetails: RecipeWithDetails
)
