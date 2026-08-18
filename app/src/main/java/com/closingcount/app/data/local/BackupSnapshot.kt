package com.closingcount.app.data.local

data class BackupSnapshot(
    val metadata: List<AppMetadataEntity>,
    val ingredientCategories: List<IngredientCategoryEntity>,
    val ingredients: List<IngredientEntity>,
    val menuCategories: List<MenuCategoryEntity>,
    val menus: List<MenuEntity>,
    val menuIngredients: List<MenuIngredientCrossRef>,
    val closings: List<ClosingEntity>,
    val closingMenuEntries: List<ClosingMenuEntryEntity>,
    val closingIngredientResults: List<ClosingIngredientResultEntity>,
    val closingMenuRecipes: List<ClosingMenuRecipeEntity>,
)
