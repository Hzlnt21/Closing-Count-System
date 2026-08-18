package com.closingcount.app.ui.menus

import com.closingcount.app.data.local.MenuCategoryEntity
import com.closingcount.app.data.local.MenuEntity

data class MenuSummary(
    val menu: MenuEntity,
    val ingredientCount: Int,
)

data class MenuCategoryGroup(
    val category: MenuCategoryEntity,
    val menus: List<MenuSummary>,
)

object MenuValidation {
    fun validate(
        name: String,
        categoryId: Long?,
        ingredientIds: Set<Long>,
    ): String? = when {
        name.isBlank() -> "Nama menu tidak boleh kosong."
        categoryId == null -> "Pilih kategori menu terlebih dahulu."
        ingredientIds.isEmpty() -> "Pilih minimal satu bahan untuk menu."
        else -> null
    }
}

