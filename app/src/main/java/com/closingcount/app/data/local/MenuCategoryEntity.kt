package com.closingcount.app.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "menu_categories",
    indices = [Index(value = ["name"], unique = true)],
)
data class MenuCategoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val sortOrder: Int,
    val isActive: Boolean = true,
)

