package com.closingcount.app.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(
    tableName = "closing_ingredient_results",
    primaryKeys = ["closingId", "ingredientId"],
    foreignKeys = [
        ForeignKey(
            entity = ClosingEntity::class,
            parentColumns = ["id"],
            childColumns = ["closingId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["ingredientId"])],
)
data class ClosingIngredientResultEntity(
    val closingId: Long,
    val ingredientId: Long,
    val ingredientName: String,
    val ingredientCategoryId: Long,
    val ingredientCategoryName: String,
    val ingredientCategorySortOrder: Int,
    val ingredientSortOrder: Int,
    val total: Int,
)
