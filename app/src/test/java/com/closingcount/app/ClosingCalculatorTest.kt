package com.closingcount.app

import com.closingcount.app.data.local.ClosingSourceRow
import com.closingcount.app.ui.closing.ClosingCalculator
import com.closingcount.app.data.local.ClosingMenuEntryEntity
import com.closingcount.app.data.local.ClosingMenuRecipeEntity
import org.junit.Assert.assertEquals
import org.junit.Test

class ClosingCalculatorTest {
    @Test
    fun twoCappuccinoAndOneLatteProduceCorrectIngredientTotals() {
        val rows = listOf(
            row(menuId = 1, menuName = "Cappuccino", ingredientId = 1, ingredientName = "Biji Kopi"),
            row(menuId = 1, menuName = "Cappuccino", ingredientId = 2, ingredientName = "Fresh Milk"),
            row(menuId = 1, menuName = "Cappuccino", ingredientId = 3, ingredientName = "Coklat"),
            row(menuId = 2, menuName = "Latte", ingredientId = 1, ingredientName = "Biji Kopi"),
            row(menuId = 2, menuName = "Latte", ingredientId = 2, ingredientName = "Fresh Milk"),
        )
        val groups = ClosingCalculator.buildMenuGroups(rows)

        val results = ClosingCalculator.calculate(groups, mapOf(1L to 2, 2L to 1))
            .flatMap { it.results }
            .associate { it.ingredient.name to it.total }

        assertEquals(mapOf("Biji Kopi" to 3, "Fresh Milk" to 3, "Coklat" to 2), results)
    }

    @Test
    fun zeroQuantityDoesNotProduceIngredientResult() {
        val groups = ClosingCalculator.buildMenuGroups(
            listOf(row(1, "Latte", 1, "Fresh Milk")),
        )
        assertEquals(emptyList<Any>(), ClosingCalculator.calculate(groups, mapOf(1L to 0)))
    }

    @Test
    fun snapshotRebuildsHistoricalMenuWithoutCurrentMasterData() {
        val entries = listOf(
            ClosingMenuEntryEntity(
                closingId = 10,
                menuId = 99,
                menuName = "Menu Lama",
                menuSortOrder = 2,
                menuCategoryId = 8,
                menuCategoryName = "Kategori Lama",
                menuCategorySortOrder = 3,
                quantity = 4,
            ),
        )
        val recipes = listOf(
            ClosingMenuRecipeEntity(
                closingId = 10,
                menuId = 99,
                ingredientId = 7,
                ingredientName = "Bahan Lama",
                ingredientSortOrder = 1,
                ingredientCategoryId = 4,
                ingredientCategoryName = "Bahan Baku",
                ingredientCategorySortOrder = 1,
            ),
        )

        val groups = ClosingCalculator.buildSnapshotMenuGroups(entries, recipes)
        val result = ClosingCalculator.calculate(groups, mapOf(99L to 5))

        assertEquals("Menu Lama", groups.single().menus.single().name)
        assertEquals("Bahan Lama", result.single().results.single().ingredient.name)
        assertEquals(5, result.single().results.single().total)
    }

    private fun row(
        menuId: Long,
        menuName: String,
        ingredientId: Long,
        ingredientName: String,
    ) = ClosingSourceRow(
        menuCategoryId = 1,
        menuCategoryName = "Coffee",
        menuCategorySortOrder = 1,
        menuId = menuId,
        menuName = menuName,
        menuSortOrder = menuId.toInt(),
        ingredientCategoryId = if (ingredientName == "Coklat") 2 else 1,
        ingredientCategoryName = if (ingredientName == "Coklat") "Powder" else "Bahan Baku",
        ingredientCategorySortOrder = if (ingredientName == "Coklat") 2 else 1,
        ingredientId = ingredientId,
        ingredientName = ingredientName,
        ingredientSortOrder = ingredientId.toInt(),
    )
}
