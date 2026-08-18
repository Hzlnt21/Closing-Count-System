package com.closingcount.app.ui.menus

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.closingcount.app.ClosingCountApplication
import com.closingcount.app.data.local.IngredientCategoryEntity
import com.closingcount.app.data.local.IngredientEntity
import com.closingcount.app.data.local.MenuCategoryEntity
import com.closingcount.app.data.local.MenuEntity
import com.closingcount.app.ui.ingredients.IngredientCategoryGroup
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

class MenusViewModel(application: Application) : AndroidViewModel(application) {
    private val database = (application as ClosingCountApplication).database
    private val menuDao = database.menuDao()
    private val ingredientDao = database.ingredientDao()

    val categories = menuDao.observeCategories().stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = emptyList(),
    )

    val menuGroups = menuDao.observeMenuRows()
        .map { rows ->
            rows.groupBy { it.categoryId }.values.map { categoryRows ->
                val first = categoryRows.first()
                MenuCategoryGroup(
                    category = MenuCategoryEntity(
                        id = first.categoryId,
                        name = first.categoryName,
                        sortOrder = first.categorySortOrder,
                        isActive = first.categoryIsActive,
                    ),
                    menus = categoryRows.mapNotNull { row ->
                        val menuId = row.menuId ?: return@mapNotNull null
                        MenuSummary(
                            menu = MenuEntity(
                                id = menuId,
                                categoryId = row.categoryId,
                                name = row.menuName.orEmpty(),
                                sortOrder = row.menuSortOrder ?: 0,
                                isActive = row.menuIsActive ?: false,
                            ),
                            ingredientCount = row.ingredientCount,
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

    val ingredientGroups = ingredientDao.observeIngredientRows()
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

    suspend fun getIngredientIds(menuId: Long): Set<Long> =
        menuDao.getIngredientIds(menuId).toSet()

    suspend fun saveCategory(
        existing: MenuCategoryEntity?,
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
            return "Kategori menu dengan nama tersebut sudah ada."
        }

        return runCatching {
            if (existing == null) {
                menuDao.insertCategory(
                    MenuCategoryEntity(
                        name = cleanName,
                        sortOrder = menuDao.nextCategorySortOrder(),
                        isActive = isActive,
                    ),
                )
            } else {
                menuDao.updateCategory(
                    existing.copy(name = cleanName, isActive = isActive),
                )
            }
        }.exceptionOrNull()?.toUserMessage("Kategori menu gagal disimpan.")
    }

    suspend fun saveMenu(
        existing: MenuEntity?,
        name: String,
        categoryId: Long?,
        ingredientIds: Set<Long>,
        isActive: Boolean,
    ): String? {
        MenuValidation.validate(name, categoryId, ingredientIds)?.let { return it }
        val validCategoryId = categoryId ?: return "Pilih kategori menu terlebih dahulu."
        val cleanName = name.trim()
        val duplicateExists = menuGroups.value
            .firstOrNull { it.category.id == validCategoryId }
            ?.menus
            ?.any {
                it.menu.id != existing?.id && it.menu.name.equals(cleanName, ignoreCase = true)
            } == true
        if (duplicateExists) return "Menu dengan nama tersebut sudah ada di kategori ini."

        return runCatching {
            if (existing == null) {
                menuDao.insertMenuWithRecipe(
                    menu = MenuEntity(
                        categoryId = validCategoryId,
                        name = cleanName,
                        sortOrder = menuDao.nextMenuSortOrder(validCategoryId),
                        isActive = isActive,
                    ),
                    ingredientIds = ingredientIds,
                )
            } else {
                val sortOrder = if (existing.categoryId == validCategoryId) {
                    existing.sortOrder
                } else {
                    menuDao.nextMenuSortOrder(validCategoryId)
                }
                menuDao.updateMenuWithRecipe(
                    menu = existing.copy(
                        categoryId = validCategoryId,
                        name = cleanName,
                        sortOrder = sortOrder,
                        isActive = isActive,
                    ),
                    ingredientIds = ingredientIds,
                )
            }
        }.exceptionOrNull()?.toUserMessage("Menu gagal disimpan.")
    }

    suspend fun setCategoryActive(
        category: MenuCategoryEntity,
        isActive: Boolean,
    ): String? = runCatching {
        menuDao.updateCategory(category.copy(isActive = isActive))
    }.exceptionOrNull()?.toUserMessage("Status kategori menu gagal diubah.")

    suspend fun setMenuActive(
        menu: MenuEntity,
        isActive: Boolean,
    ): String? = runCatching {
        menuDao.updateMenu(menu.copy(isActive = isActive))
    }.exceptionOrNull()?.toUserMessage("Status menu gagal diubah.")

    private fun Throwable.toUserMessage(fallback: String): String =
        if (message.orEmpty().contains("UNIQUE", ignoreCase = true)) fallback
        else "Terjadi kesalahan saat menyimpan data."
}
