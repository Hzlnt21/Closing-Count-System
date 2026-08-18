package com.closingcount.app.ui.closing

import com.closingcount.app.data.local.ClosingSourceRow
import com.closingcount.app.data.local.ClosingMenuEntryEntity
import com.closingcount.app.data.local.ClosingMenuRecipeEntity
import java.time.LocalDate

data class ClosingIngredient(
    val id: Long,
    val name: String,
    val sortOrder: Int,
    val categoryId: Long,
    val categoryName: String,
    val categorySortOrder: Int,
)

data class ClosingMenu(
    val id: Long,
    val name: String,
    val sortOrder: Int,
    val categoryId: Long,
    val categoryName: String,
    val categorySortOrder: Int,
    val ingredients: List<ClosingIngredient>,
)

data class ClosingMenuGroup(
    val id: Long,
    val name: String,
    val sortOrder: Int,
    val menus: List<ClosingMenu>,
)

data class ClosingIngredientResult(
    val ingredient: ClosingIngredient,
    val total: Int,
)

data class ClosingResultGroup(
    val id: Long,
    val name: String,
    val sortOrder: Int,
    val results: List<ClosingIngredientResult>,
)

data class ClosingUiState(
    val date: LocalDate = LocalDate.now(),
    val menuGroups: List<ClosingMenuGroup> = emptyList(),
    val quantities: Map<Long, Int> = emptyMap(),
    val resultGroups: List<ClosingResultGroup> = emptyList(),
    val isLoading: Boolean = true,
    val isSaved: Boolean = false,
    val closingExists: Boolean = false,
    val hasUnsavedChanges: Boolean = false,
)

object ClosingCalculator {
    fun buildMenuGroups(rows: List<ClosingSourceRow>): List<ClosingMenuGroup> = rows
        .groupBy { it.menuCategoryId }
        .values
        .map { categoryRows ->
            val first = categoryRows.first()
            ClosingMenuGroup(
                id = first.menuCategoryId,
                name = first.menuCategoryName,
                sortOrder = first.menuCategorySortOrder,
                menus = categoryRows.groupBy { it.menuId }.values.map { menuRows ->
                    val menu = menuRows.first()
                    ClosingMenu(
                        id = menu.menuId,
                        name = menu.menuName,
                        sortOrder = menu.menuSortOrder,
                        categoryId = menu.menuCategoryId,
                        categoryName = menu.menuCategoryName,
                        categorySortOrder = menu.menuCategorySortOrder,
                        ingredients = menuRows.map { row ->
                            ClosingIngredient(
                                id = row.ingredientId,
                                name = row.ingredientName,
                                sortOrder = row.ingredientSortOrder,
                                categoryId = row.ingredientCategoryId,
                                categoryName = row.ingredientCategoryName,
                                categorySortOrder = row.ingredientCategorySortOrder,
                            )
                        }.distinctBy { it.id },
                    )
                }.sortedWith(compareBy(ClosingMenu::sortOrder, ClosingMenu::name)),
            )
        }.sortedWith(compareBy(ClosingMenuGroup::sortOrder, ClosingMenuGroup::name))

    fun calculate(
        groups: List<ClosingMenuGroup>,
        quantities: Map<Long, Int>,
    ): List<ClosingResultGroup> {
        val ingredientById = mutableMapOf<Long, ClosingIngredient>()
        val totals = mutableMapOf<Long, Int>()
        groups.flatMap { it.menus }.forEach { menu ->
            val quantity = quantities[menu.id]?.coerceAtLeast(0) ?: 0
            if (quantity > 0) {
                menu.ingredients.forEach { ingredient ->
                    ingredientById[ingredient.id] = ingredient
                    totals[ingredient.id] = (totals[ingredient.id] ?: 0) + quantity
                }
            }
        }
        return totals.entries
            .mapNotNull { (id, total) ->
                ingredientById[id]?.let { ClosingIngredientResult(it, total) }
            }
            .groupBy { it.ingredient.categoryId }
            .values
            .map { results ->
                val first = results.first().ingredient
                ClosingResultGroup(
                    id = first.categoryId,
                    name = first.categoryName,
                    sortOrder = first.categorySortOrder,
                    results = results.sortedWith(
                        compareBy(
                            { it.ingredient.sortOrder },
                            { it.ingredient.name },
                        ),
                    ),
                )
            }.sortedWith(compareBy(ClosingResultGroup::sortOrder, ClosingResultGroup::name))
    }

    fun buildSnapshotMenuGroups(
        entries: List<ClosingMenuEntryEntity>,
        recipes: List<ClosingMenuRecipeEntity>,
    ): List<ClosingMenuGroup> {
        if (entries.isEmpty() || recipes.isEmpty()) return emptyList()
        val recipesByMenu = recipes.groupBy { it.menuId }
        return entries.groupBy { it.menuCategoryId }.values.map { categoryEntries ->
            val first = categoryEntries.first()
            ClosingMenuGroup(
                id = first.menuCategoryId,
                name = first.menuCategoryName,
                sortOrder = first.menuCategorySortOrder,
                menus = categoryEntries.mapNotNull { entry ->
                    val menuRecipes = recipesByMenu[entry.menuId].orEmpty()
                    if (menuRecipes.isEmpty()) return@mapNotNull null
                    ClosingMenu(
                        id = entry.menuId,
                        name = entry.menuName,
                        sortOrder = entry.menuSortOrder,
                        categoryId = entry.menuCategoryId,
                        categoryName = entry.menuCategoryName,
                        categorySortOrder = entry.menuCategorySortOrder,
                        ingredients = menuRecipes.map { recipe ->
                            ClosingIngredient(
                                id = recipe.ingredientId,
                                name = recipe.ingredientName,
                                sortOrder = recipe.ingredientSortOrder,
                                categoryId = recipe.ingredientCategoryId,
                                categoryName = recipe.ingredientCategoryName,
                                categorySortOrder = recipe.ingredientCategorySortOrder,
                            )
                        },
                    )
                }.sortedWith(compareBy(ClosingMenu::sortOrder, ClosingMenu::name)),
            )
        }.filter { it.menus.isNotEmpty() }
            .sortedWith(compareBy(ClosingMenuGroup::sortOrder, ClosingMenuGroup::name))
    }
}
