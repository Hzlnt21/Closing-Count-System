package com.closingcount.app.ui.closing

import com.closingcount.app.data.local.ClosingSourceRow
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
}
