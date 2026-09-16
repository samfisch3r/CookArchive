package com.android.cookarchive.data.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "recipes")
data class Recipe(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val description: String,
    val category: String,
    val defaultServings: Int,
    val imagePath: String? = null,
    
    @ColumnInfo(defaultValue = "0")
    val lastCooked: Long = 0L,
    
    @ColumnInfo(defaultValue = "0")
    val cookCount: Int = 0
)
