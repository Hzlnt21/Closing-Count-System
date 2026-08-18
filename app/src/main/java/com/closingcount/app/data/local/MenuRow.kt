package com.closingcount.app.data.local

data class MenuRow(
    val categoryId: Long,
    val categoryName: String,
    val categorySortOrder: Int,
    val categoryIsActive: Boolean,
    val menuId: Long?,
    val menuName: String?,
    val menuSortOrder: Int?,
    val menuIsActive: Boolean?,
    val ingredientCount: Int,
)

