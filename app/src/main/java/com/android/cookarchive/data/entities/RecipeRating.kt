package com.android.cookarchive.data.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "recipe_ratings")
data class RecipeRating(
    @PrimaryKey val id: String,
    val recipeId: Long? = null,
    val title: String,
    val rating: Int,
    val date: String,
    val comment: String? = null,
    val timestamp: Long = System.currentTimeMillis()
)
