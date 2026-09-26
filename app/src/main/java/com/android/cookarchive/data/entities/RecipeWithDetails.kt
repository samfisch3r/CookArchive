package com.android.cookarchive.data.entities

import androidx.room.Embedded
import androidx.room.Relation

data class RecipeWithDetails(
    @Embedded val recipe: Recipe,
    @Relation(
        parentColumn = "id",
        entityColumn = "recipeId"
    )
    val ingredients: List<Ingredient>,
    @Relation(
        parentColumn = "id",
        entityColumn = "recipeId"
    )
    val steps: List<InstructionStep>
) {
    val currentOrBaseServings: Int
        get() = if (recipe.currentServings > 0) recipe.currentServings else recipe.defaultServings.coerceAtLeast(1)

    val scaleFactor: Double
        get() = currentOrBaseServings.toDouble() / recipe.defaultServings.coerceAtLeast(1)
}
