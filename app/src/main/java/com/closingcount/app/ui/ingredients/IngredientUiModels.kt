package com.closingcount.app.ui.ingredients

import com.closingcount.app.data.local.IngredientCategoryEntity
import com.closingcount.app.data.local.IngredientEntity

data class IngredientCategoryGroup(
    val category: IngredientCategoryEntity,
    val ingredients: List<IngredientEntity>,
)

