package com.closingcount.app.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(
    tableName = "closing_menu_recipes",
    primaryKeys = ["closingId", "menuId", "ingredientId"],
    foreignKeys = [
        ForeignKey(
            entity = ClosingEntity::class,
            parentColumns = ["id"],
            childColumns = ["closingId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["menuId"]), Index(value = ["ingredientId"])],
)
data class ClosingMenuRecipeEntity(
    val closingId: Long,
    val menuId: Long,
    val ingredientId: Long,
    val ingredientName: String,
    val ingredientSortOrder: Int,
    val ingredientCategoryId: Long,
    val ingredientCategoryName: String,
    val ingredientCategorySortOrder: Int,
)
