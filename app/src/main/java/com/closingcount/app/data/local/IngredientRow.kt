package com.closingcount.app.data.local

data class IngredientRow(
    val categoryId: Long,
    val categoryName: String,
    val categorySortOrder: Int,
    val categoryIsActive: Boolean,
    val ingredientId: Long?,
    val ingredientName: String?,
    val ingredientSortOrder: Int?,
    val ingredientIsActive: Boolean?,
)

