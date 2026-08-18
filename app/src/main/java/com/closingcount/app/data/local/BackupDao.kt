package com.closingcount.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction

@Dao
interface BackupDao {
    @Query("SELECT * FROM app_metadata ORDER BY `key`")
    suspend fun getMetadata(): List<AppMetadataEntity>

    @Query("SELECT * FROM ingredient_categories ORDER BY sortOrder, id")
    suspend fun getIngredientCategories(): List<IngredientCategoryEntity>

    @Query("SELECT * FROM ingredients ORDER BY categoryId, sortOrder, id")
    suspend fun getIngredients(): List<IngredientEntity>

    @Query("SELECT * FROM menu_categories ORDER BY sortOrder, id")
    suspend fun getMenuCategories(): List<MenuCategoryEntity>

    @Query("SELECT * FROM menus ORDER BY categoryId, sortOrder, id")
    suspend fun getMenus(): List<MenuEntity>

    @Query("SELECT * FROM menu_ingredients ORDER BY menuId, ingredientId")
    suspend fun getMenuIngredients(): List<MenuIngredientCrossRef>

    @Query("SELECT * FROM closings ORDER BY date")
    suspend fun getClosings(): List<ClosingEntity>

    @Query("SELECT * FROM closing_menu_entries ORDER BY closingId, menuId")
    suspend fun getClosingMenuEntries(): List<ClosingMenuEntryEntity>

    @Query("SELECT * FROM closing_ingredient_results ORDER BY closingId, ingredientId")
    suspend fun getClosingIngredientResults(): List<ClosingIngredientResultEntity>

    @Query("SELECT * FROM closing_menu_recipes ORDER BY closingId, menuId, ingredientId")
    suspend fun getClosingMenuRecipes(): List<ClosingMenuRecipeEntity>

    @Transaction
    suspend fun createSnapshot(): BackupSnapshot = BackupSnapshot(
        metadata = getMetadata(),
        ingredientCategories = getIngredientCategories(),
        ingredients = getIngredients(),
        menuCategories = getMenuCategories(),
        menus = getMenus(),
        menuIngredients = getMenuIngredients(),
        closings = getClosings(),
        closingMenuEntries = getClosingMenuEntries(),
        closingIngredientResults = getClosingIngredientResults(),
        closingMenuRecipes = getClosingMenuRecipes(),
    )

    @Query("DELETE FROM closing_menu_recipes")
    suspend fun clearClosingMenuRecipes()

    @Query("DELETE FROM closing_ingredient_results")
    suspend fun clearClosingIngredientResults()

    @Query("DELETE FROM closing_menu_entries")
    suspend fun clearClosingMenuEntries()

    @Query("DELETE FROM closings")
    suspend fun clearClosings()

    @Query("DELETE FROM menu_ingredients")
    suspend fun clearMenuIngredients()

    @Query("DELETE FROM menus")
    suspend fun clearMenus()

    @Query("DELETE FROM menu_categories")
    suspend fun clearMenuCategories()

    @Query("DELETE FROM ingredients")
    suspend fun clearIngredients()

    @Query("DELETE FROM ingredient_categories")
    suspend fun clearIngredientCategories()

    @Query("DELETE FROM app_metadata")
    suspend fun clearMetadata()

    @Insert
    suspend fun insertMetadata(items: List<AppMetadataEntity>)

    @Insert
    suspend fun insertIngredientCategories(items: List<IngredientCategoryEntity>)

    @Insert
    suspend fun insertIngredients(items: List<IngredientEntity>)

    @Insert
    suspend fun insertMenuCategories(items: List<MenuCategoryEntity>)

    @Insert
    suspend fun insertMenus(items: List<MenuEntity>)

    @Insert
    suspend fun insertMenuIngredients(items: List<MenuIngredientCrossRef>)

    @Insert
    suspend fun insertClosings(items: List<ClosingEntity>)

    @Insert
    suspend fun insertClosingMenuEntries(items: List<ClosingMenuEntryEntity>)

    @Insert
    suspend fun insertClosingIngredientResults(items: List<ClosingIngredientResultEntity>)

    @Insert
    suspend fun insertClosingMenuRecipes(items: List<ClosingMenuRecipeEntity>)

    @Transaction
    suspend fun restore(snapshot: BackupSnapshot) {
        clearClosingMenuRecipes()
        clearClosingIngredientResults()
        clearClosingMenuEntries()
        clearClosings()
        clearMenuIngredients()
        clearMenus()
        clearMenuCategories()
        clearIngredients()
        clearIngredientCategories()
        clearMetadata()

        if (snapshot.metadata.isNotEmpty()) insertMetadata(snapshot.metadata)
        if (snapshot.ingredientCategories.isNotEmpty()) {
            insertIngredientCategories(snapshot.ingredientCategories)
        }
        if (snapshot.ingredients.isNotEmpty()) insertIngredients(snapshot.ingredients)
        if (snapshot.menuCategories.isNotEmpty()) insertMenuCategories(snapshot.menuCategories)
        if (snapshot.menus.isNotEmpty()) insertMenus(snapshot.menus)
        if (snapshot.menuIngredients.isNotEmpty()) insertMenuIngredients(snapshot.menuIngredients)
        if (snapshot.closings.isNotEmpty()) insertClosings(snapshot.closings)
        if (snapshot.closingMenuEntries.isNotEmpty()) {
            insertClosingMenuEntries(snapshot.closingMenuEntries)
        }
        if (snapshot.closingIngredientResults.isNotEmpty()) {
            insertClosingIngredientResults(snapshot.closingIngredientResults)
        }
        if (snapshot.closingMenuRecipes.isNotEmpty()) {
            insertClosingMenuRecipes(snapshot.closingMenuRecipes)
        }
    }
}
