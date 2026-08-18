package com.closingcount.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ClosingDao {
    @Query(
        """
        SELECT
            menuCategory.id AS menuCategoryId,
            menuCategory.name AS menuCategoryName,
            menuCategory.sortOrder AS menuCategorySortOrder,
            menu.id AS menuId,
            menu.name AS menuName,
            menu.sortOrder AS menuSortOrder,
            ingredientCategory.id AS ingredientCategoryId,
            ingredientCategory.name AS ingredientCategoryName,
            ingredientCategory.sortOrder AS ingredientCategorySortOrder,
            ingredient.id AS ingredientId,
            ingredient.name AS ingredientName,
            ingredient.sortOrder AS ingredientSortOrder
        FROM menus AS menu
        INNER JOIN menu_categories AS menuCategory ON menuCategory.id = menu.categoryId
        INNER JOIN menu_ingredients AS recipe ON recipe.menuId = menu.id
        INNER JOIN ingredients AS ingredient ON ingredient.id = recipe.ingredientId
        INNER JOIN ingredient_categories AS ingredientCategory
            ON ingredientCategory.id = ingredient.categoryId
        WHERE menu.isActive = 1 AND menuCategory.isActive = 1
        ORDER BY menuCategory.sortOrder, menuCategory.name COLLATE NOCASE,
                 menu.sortOrder, menu.name COLLATE NOCASE,
                 ingredientCategory.sortOrder, ingredient.sortOrder
        """,
    )
    fun observeClosingSourceRows(): Flow<List<ClosingSourceRow>>

    @Query(
        """
        SELECT closing.id AS id, closing.date AS date, closing.updatedAt AS updatedAt,
               (SELECT COALESCE(SUM(entry.quantity), 0)
                FROM closing_menu_entries AS entry
                WHERE entry.closingId = closing.id) AS totalMenusSold,
               (SELECT COUNT(*) FROM closing_menu_entries AS entry
                WHERE entry.closingId = closing.id AND entry.quantity > 0) AS soldMenuTypes,
               (SELECT COUNT(*) FROM closing_ingredient_results AS result
                WHERE result.closingId = closing.id) AS ingredientCount
        FROM closings AS closing
        ORDER BY closing.date DESC
        """,
    )
    fun observeHistoryRows(): Flow<List<ClosingHistoryRow>>

    @Query("SELECT * FROM closings WHERE date = :date LIMIT 1")
    suspend fun getClosingByDate(date: String): ClosingEntity?

    @Query(
        """
        SELECT * FROM closing_menu_entries WHERE closingId = :closingId
        ORDER BY menuCategorySortOrder, menuCategoryName COLLATE NOCASE,
                 menuSortOrder, menuName COLLATE NOCASE
        """,
    )
    suspend fun getMenuEntries(closingId: Long): List<ClosingMenuEntryEntity>

    @Query(
        """
        SELECT * FROM closing_ingredient_results
        WHERE closingId = :closingId
        ORDER BY ingredientCategorySortOrder, ingredientSortOrder, ingredientName COLLATE NOCASE
        """,
    )
    suspend fun getIngredientResults(closingId: Long): List<ClosingIngredientResultEntity>

    @Query(
        """
        SELECT * FROM closing_menu_recipes
        WHERE closingId = :closingId
        ORDER BY menuId, ingredientCategorySortOrder, ingredientSortOrder, ingredientName COLLATE NOCASE
        """,
    )
    suspend fun getMenuRecipes(closingId: Long): List<ClosingMenuRecipeEntity>

    @Insert
    suspend fun insertClosing(closing: ClosingEntity): Long

    @Update
    suspend fun updateClosing(closing: ClosingEntity)

    @Insert
    suspend fun insertMenuEntries(entries: List<ClosingMenuEntryEntity>)

    @Insert
    suspend fun insertIngredientResults(results: List<ClosingIngredientResultEntity>)

    @Insert
    suspend fun insertMenuRecipes(recipes: List<ClosingMenuRecipeEntity>)

    @Query("DELETE FROM closing_menu_entries WHERE closingId = :closingId")
    suspend fun deleteMenuEntries(closingId: Long)

    @Query("DELETE FROM closing_ingredient_results WHERE closingId = :closingId")
    suspend fun deleteIngredientResults(closingId: Long)

    @Query("DELETE FROM closing_menu_recipes WHERE closingId = :closingId")
    suspend fun deleteMenuRecipes(closingId: Long)

    @Transaction
    suspend fun replaceClosing(
        date: String,
        updatedAt: Long,
        entries: List<ClosingMenuEntryEntity>,
        results: List<ClosingIngredientResultEntity>,
        recipes: List<ClosingMenuRecipeEntity>,
    ): Long {
        val current = getClosingByDate(date)
        val closingId = if (current == null) {
            insertClosing(ClosingEntity(date = date, updatedAt = updatedAt))
        } else {
            updateClosing(current.copy(updatedAt = updatedAt))
            current.id
        }
        deleteMenuEntries(closingId)
        deleteIngredientResults(closingId)
        deleteMenuRecipes(closingId)
        if (entries.isNotEmpty()) {
            insertMenuEntries(entries.map { it.copy(closingId = closingId) })
        }
        if (results.isNotEmpty()) {
            insertIngredientResults(results.map { it.copy(closingId = closingId) })
        }
        if (recipes.isNotEmpty()) {
            insertMenuRecipes(recipes.map { it.copy(closingId = closingId) })
        }
        return closingId
    }
}
