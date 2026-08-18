package com.closingcount.app

import com.closingcount.app.ui.menus.MenuValidation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MenuValidationTest {
    @Test
    fun menuRequiresNameCategoryAndIngredient() {
        assertEquals(
            "Nama menu tidak boleh kosong.",
            MenuValidation.validate(" ", categoryId = 1, ingredientIds = setOf(1)),
        )
        assertEquals(
            "Pilih kategori menu terlebih dahulu.",
            MenuValidation.validate("Latte", categoryId = null, ingredientIds = setOf(1)),
        )
        assertEquals(
            "Pilih minimal satu bahan untuk menu.",
            MenuValidation.validate("Latte", categoryId = 1, ingredientIds = emptySet()),
        )
    }

    @Test
    fun validMenuPassesValidation() {
        assertNull(
            MenuValidation.validate(
                name = "Latte",
                categoryId = 1,
                ingredientIds = setOf(1, 2),
            ),
        )
    }
}

