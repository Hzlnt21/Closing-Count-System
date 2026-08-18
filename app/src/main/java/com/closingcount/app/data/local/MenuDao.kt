package com.closingcount.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface MenuDao {
    @Query(
        """
        SELECT
            category.id AS categoryId,
            category.name AS categoryName,
            category.sortOrder AS categorySortOrder,
            category.isActive AS categoryIsActive,
            menu.id AS menuId,
            menu.name AS menuName,
            menu.sortOrder AS menuSortOrder,
            menu.isActive AS menuIsActive,
            COUNT(recipe.ingredientId) AS ingredientCount
        FROM menu_categories AS category
        LEFT JOIN menus AS menu ON menu.categoryId = category.id
        LEFT JOIN menu_ingredients AS recipe ON recipe.menuId = menu.id
        GROUP BY category.id, menu.id
        ORDER BY category.sortOrder, category.name COLLATE NOCASE,
                 menu.sortOrder, menu.name COLLATE NOCASE
        """,
    )
    fun observeMenuRows(): Flow<List<MenuRow>>

    @Query("SELECT * FROM menu_categories ORDER BY sortOrder, name COLLATE NOCASE")
    fun observeCategories(): Flow<List<MenuCategoryEntity>>

    @Query("SELECT ingredientId FROM menu_ingredients WHERE menuId = :menuId ORDER BY ingredientId")
    suspend fun getIngredientIds(menuId: Long): List<Long>

    @Query("SELECT COALESCE(MAX(sortOrder), 0) + 1 FROM menu_categories")
    suspend fun nextCategorySortOrder(): Int

    @Query("SELECT COALESCE(MAX(sortOrder), 0) + 1 FROM menus WHERE categoryId = :categoryId")
    suspend fun nextMenuSortOrder(categoryId: Long): Int

    @Insert
    suspend fun insertCategory(category: MenuCategoryEntity): Long

    @Update
    suspend fun updateCategory(category: MenuCategoryEntity)

    @Insert
    suspend fun insertMenu(menu: MenuEntity): Long

    @Update
    suspend fun updateMenu(menu: MenuEntity)

    @Insert
    suspend fun insertRecipe(recipe: List<MenuIngredientCrossRef>)

    @Query("DELETE FROM menu_ingredients WHERE menuId = :menuId")
    suspend fun deleteRecipe(menuId: Long)

    @Transaction
    suspend fun insertMenuWithRecipe(
        menu: MenuEntity,
        ingredientIds: Set<Long>,
    ): Long {
        val menuId = insertMenu(menu)
        val recipe = ingredientIds.map { ingredientId ->
                MenuIngredientCrossRef(menuId = menuId, ingredientId = ingredientId)
            }
        if (recipe.isNotEmpty()) insertRecipe(recipe)
        return menuId
    }

    @Transaction
    suspend fun updateMenuWithRecipe(
        menu: MenuEntity,
        ingredientIds: Set<Long>,
    ) {
        updateMenu(menu)
        deleteRecipe(menu.id)
        val recipe = ingredientIds.map { ingredientId ->
                MenuIngredientCrossRef(menuId = menu.id, ingredientId = ingredientId)
            }
        if (recipe.isNotEmpty()) insertRecipe(recipe)
    }
}
