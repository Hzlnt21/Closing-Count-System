package com.closingcount.app.data.local

data class ClosingSourceRow(
    val menuCategoryId: Long,
    val menuCategoryName: String,
    val menuCategorySortOrder: Int,
    val menuId: Long,
    val menuName: String,
    val menuSortOrder: Int,
    val ingredientCategoryId: Long,
    val ingredientCategoryName: String,
    val ingredientCategorySortOrder: Int,
    val ingredientId: Long,
    val ingredientName: String,
    val ingredientSortOrder: Int,
)
