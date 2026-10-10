package com.android.cookarchive.data.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "meal_history")
data class MealHistoryItem(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(defaultValue = "NULL")
    val recipeId: Long? = null,
    val title: String,
    val date: String,
    val timestamp: Long = System.currentTimeMillis()
)
