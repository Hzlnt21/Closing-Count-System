package com.closingcount.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface IngredientDao {
    @Query(
        """
        SELECT
            category.id AS categoryId,
            category.name AS categoryName,
            category.sortOrder AS categorySortOrder,
            category.isActive AS categoryIsActive,
            ingredient.id AS ingredientId,
            ingredient.name AS ingredientName,
            ingredient.sortOrder AS ingredientSortOrder,
            ingredient.isActive AS ingredientIsActive
        FROM ingredient_categories AS category
        LEFT JOIN ingredients AS ingredient ON ingredient.categoryId = category.id
        ORDER BY category.sortOrder, category.name COLLATE NOCASE,
                 ingredient.sortOrder, ingredient.name COLLATE NOCASE
        """,
    )
    fun observeIngredientRows(): Flow<List<IngredientRow>>

    @Query("SELECT * FROM ingredient_categories ORDER BY sortOrder, name COLLATE NOCASE")
    fun observeCategories(): Flow<List<IngredientCategoryEntity>>

    @Query("SELECT COALESCE(MAX(sortOrder), 0) + 1 FROM ingredient_categories")
    suspend fun nextCategorySortOrder(): Int

    @Query("SELECT COALESCE(MAX(sortOrder), 0) + 1 FROM ingredients WHERE categoryId = :categoryId")
    suspend fun nextIngredientSortOrder(categoryId: Long): Int

    @Insert
    suspend fun insertCategory(category: IngredientCategoryEntity): Long

    @Update
    suspend fun updateCategory(category: IngredientCategoryEntity)

    @Insert
    suspend fun insertIngredient(ingredient: IngredientEntity): Long

    @Update
    suspend fun updateIngredient(ingredient: IngredientEntity)
}

