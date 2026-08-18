package com.closingcount.app

import com.closingcount.app.data.local.ClosingSourceRow
import com.closingcount.app.ui.closing.ClosingCalculator
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
