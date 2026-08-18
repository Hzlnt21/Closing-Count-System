package com.closingcount.app.ui.ingredients

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.closingcount.app.ClosingCountApplication
import com.closingcount.app.data.local.IngredientCategoryEntity
import com.closingcount.app.data.local.IngredientEntity
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

class IngredientsViewModel(application: Application) : AndroidViewModel(application) {
    private val ingredientDao =
        (application as ClosingCountApplication).database.ingredientDao()

    val categories = ingredientDao.observeCategories().stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = emptyList(),
    )

    val categoryGroups = ingredientDao.observeIngredientRows()
        .map { rows ->
            rows.groupBy { it.categoryId }.values.map { categoryRows ->
                val first = categoryRows.first()
                IngredientCategoryGroup(
                    category = IngredientCategoryEntity(
                        id = first.categoryId,
                        name = first.categoryName,
                        sortOrder = first.categorySortOrder,
                        isActive = first.categoryIsActive,
                    ),
                    ingredients = categoryRows.mapNotNull { row ->
                        val ingredientId = row.ingredientId ?: return@mapNotNull null
                        IngredientEntity(
                            id = ingredientId,
                            categoryId = row.categoryId,
                            name = row.ingredientName.orEmpty(),
                            sortOrder = row.ingredientSortOrder ?: 0,
                            isActive = row.ingredientIsActive ?: false,
                        )
                    },
                )
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList(),
        )

    suspend fun saveCategory(
        existing: IngredientCategoryEntity?,
        name: String,
        isActive: Boolean,
    ): String? {
        val cleanName = name.trim()
        if (cleanName.isEmpty()) return "Nama kategori tidak boleh kosong."
        if (
            categories.value.any {
                it.id != existing?.id && it.name.equals(cleanName, ignoreCase = true)
            }
        ) {
            return "Kategori dengan nama tersebut sudah ada."
        }

        return runCatching {
            if (existing == null) {
                ingredientDao.insertCategory(
                    IngredientCategoryEntity(
                        name = cleanName,
                        sortOrder = ingredientDao.nextCategorySortOrder(),
                        isActive = isActive,
                    ),
                )
            } else {
                ingredientDao.updateCategory(
                    existing.copy(
                        name = cleanName,
                        isActive = isActive,
                    ),
                )
            }
        }.exceptionOrNull()?.toUserMessage("Kategori dengan nama tersebut sudah ada.")
    }

    suspend fun saveIngredient(
        existing: IngredientEntity?,
        name: String,
        categoryId: Long?,
        isActive: Boolean,
    ): String? {
        val cleanName = name.trim()
        if (cleanName.isEmpty()) return "Nama bahan tidak boleh kosong."
        if (categoryId == null) return "Pilih kategori bahan terlebih dahulu."
        val duplicateExists = categoryGroups.value
            .firstOrNull { it.category.id == categoryId }
            ?.ingredients
            ?.any {
                it.id != existing?.id && it.name.equals(cleanName, ignoreCase = true)
            } == true
        if (duplicateExists) {
            return "Bahan dengan nama tersebut sudah ada di kategori ini."
        }

        return runCatching {
            if (existing == null) {
                ingredientDao.insertIngredient(
                    IngredientEntity(
                        categoryId = categoryId,
                        name = cleanName,
                        sortOrder = ingredientDao.nextIngredientSortOrder(categoryId),
                        isActive = isActive,
                    ),
                )
            } else {
                val sortOrder = if (existing.categoryId == categoryId) {
                    existing.sortOrder
                } else {
                    ingredientDao.nextIngredientSortOrder(categoryId)
                }
                ingredientDao.updateIngredient(
                    existing.copy(
                        categoryId = categoryId,
                        name = cleanName,
                        sortOrder = sortOrder,
                        isActive = isActive,
                    ),
                )
            }
        }.exceptionOrNull()?.toUserMessage("Bahan dengan nama tersebut sudah ada di kategori ini.")
    }

    suspend fun setCategoryActive(
        category: IngredientCategoryEntity,
        isActive: Boolean,
    ): String? = runCatching {
        ingredientDao.updateCategory(category.copy(isActive = isActive))
    }.exceptionOrNull()?.toUserMessage("Status kategori gagal diubah.")

    suspend fun setIngredientActive(
        ingredient: IngredientEntity,
        isActive: Boolean,
    ): String? = runCatching {
        ingredientDao.updateIngredient(ingredient.copy(isActive = isActive))
    }.exceptionOrNull()?.toUserMessage("Status bahan gagal diubah.")

    private fun Throwable.toUserMessage(fallback: String): String =
        if (message.orEmpty().contains("UNIQUE", ignoreCase = true)) fallback
        else "Terjadi kesalahan saat menyimpan data."
}
