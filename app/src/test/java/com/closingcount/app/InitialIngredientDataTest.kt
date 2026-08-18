package com.closingcount.app

import com.closingcount.app.data.local.InitialIngredientData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class InitialIngredientDataTest {
    @Test
    fun seedContainsExpectedCategoriesAndIngredients() {
        val categories = InitialIngredientData.categories

        assertEquals(
            listOf("Bahan Baku", "Powder", "Syrup", "Buah", "Lain-lain"),
            categories.map { it.name },
        )
        assertEquals(40, categories.sumOf { it.ingredients.size })
    }

    @Test
    fun ingredientNamesAreUniqueInsideEachCategory() {
        InitialIngredientData.categories.forEach { category ->
            val normalizedNames = category.ingredients.map { it.trim().lowercase() }
            assertTrue(
                "Duplicate ingredient in ${category.name}",
                normalizedNames.size == normalizedNames.distinct().size,
            )
        }
    }
}
