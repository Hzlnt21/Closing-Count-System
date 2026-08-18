package com.closingcount.app.ui.closing

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.closingcount.app.ClosingCountApplication
import com.closingcount.app.data.local.ClosingIngredientResultEntity
import com.closingcount.app.data.local.ClosingMenuEntryEntity
import com.closingcount.app.data.local.ClosingMenuRecipeEntity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.time.LocalDate

class ClosingViewModel(application: Application) : AndroidViewModel(application) {
    private val dao = (application as ClosingCountApplication).database.closingDao()
    private val mutableUiState = MutableStateFlow(ClosingUiState())
    val uiState: StateFlow<ClosingUiState> = mutableUiState.asStateFlow()
    private var activeMenuGroups: List<ClosingMenuGroup> = emptyList()

    init {
        viewModelScope.launch {
            dao.observeClosingSourceRows().collectLatest { rows ->
                val groups = ClosingCalculator.buildMenuGroups(rows)
                activeMenuGroups = groups
                loadDate(mutableUiState.value.date)
            }
        }
    }

    fun selectDate(date: LocalDate) {
        if (date == mutableUiState.value.date) return
        mutableUiState.value = mutableUiState.value.copy(date = date, isLoading = true)
        viewModelScope.launch { loadDate(date) }
    }

    fun updateQuantity(menuId: Long, quantity: Int) {
        val state = mutableUiState.value
        val newQuantities = state.quantities.toMutableMap().apply {
            put(menuId, quantity.coerceIn(0, 9999))
        }
        mutableUiState.value = state.copy(
            quantities = newQuantities,
            resultGroups = ClosingCalculator.calculate(state.menuGroups, newQuantities),
            isSaved = false,
            hasUnsavedChanges = true,
        )
    }

    suspend fun save(): String? {
        val state = mutableUiState.value
        if (state.menuGroups.isEmpty()) return "Belum ada menu aktif untuk closing."
        val menus = state.menuGroups.flatMap { it.menus }
        val entries = menus.map { menu ->
            val quantity = state.quantities[menu.id] ?: 0
            ClosingMenuEntryEntity(
                closingId = 0,
                menuId = menu.id,
                menuName = menu.name,
                menuSortOrder = menu.sortOrder,
                menuCategoryId = menu.categoryId,
                menuCategoryName = menu.categoryName,
                menuCategorySortOrder = menu.categorySortOrder,
                quantity = quantity,
            )
        }
        val recipes = menus.flatMap { menu ->
            menu.ingredients.map { ingredient ->
                ClosingMenuRecipeEntity(
                    closingId = 0,
                    menuId = menu.id,
                    ingredientId = ingredient.id,
                    ingredientName = ingredient.name,
                    ingredientSortOrder = ingredient.sortOrder,
                    ingredientCategoryId = ingredient.categoryId,
                    ingredientCategoryName = ingredient.categoryName,
                    ingredientCategorySortOrder = ingredient.categorySortOrder,
                )
            }
        }
        val results = state.resultGroups.flatMap { group ->
            group.results.map { result ->
                ClosingIngredientResultEntity(
                    closingId = 0,
                    ingredientId = result.ingredient.id,
                    ingredientName = result.ingredient.name,
                    ingredientCategoryId = result.ingredient.categoryId,
                    ingredientCategoryName = result.ingredient.categoryName,
                    ingredientCategorySortOrder = result.ingredient.categorySortOrder,
                    ingredientSortOrder = result.ingredient.sortOrder,
                    total = result.total,
                )
            }
        }
        return runCatching {
            dao.replaceClosing(
                date = state.date.toString(),
                updatedAt = System.currentTimeMillis(),
                entries = entries,
                results = results,
                recipes = recipes,
            )
            mutableUiState.value = mutableUiState.value.copy(
                isSaved = true,
                closingExists = true,
                hasUnsavedChanges = false,
            )
        }.exceptionOrNull()?.let { "Closing gagal disimpan. Coba kembali." }
    }

    private suspend fun loadDate(date: LocalDate) {
        val closing = dao.getClosingByDate(date.toString())
        val savedEntries = if (closing == null) emptyList() else dao.getMenuEntries(closing.id)
        val savedRecipes = if (closing == null) emptyList() else dao.getMenuRecipes(closing.id)
        val snapshotGroups = ClosingCalculator.buildSnapshotMenuGroups(savedEntries, savedRecipes)
        val menuGroups = snapshotGroups.ifEmpty { activeMenuGroups }
        val savedQuantities = savedEntries.associate { it.menuId to it.quantity }
        val quantities = menuGroups.flatMap { it.menus }.associate { menu ->
            menu.id to (savedQuantities[menu.id] ?: 0)
        }
        val resultGroups = if (closing == null) {
            ClosingCalculator.calculate(menuGroups, quantities)
        } else {
            dao.getIngredientResults(closing.id)
                .groupBy { it.ingredientCategoryId }
                .values
                .map { rows ->
                    val first = rows.first()
                    ClosingResultGroup(
                        id = first.ingredientCategoryId,
                        name = first.ingredientCategoryName,
                        sortOrder = first.ingredientCategorySortOrder,
                        results = rows.map { row ->
                            ClosingIngredientResult(
                                ingredient = ClosingIngredient(
                                    id = row.ingredientId,
                                    name = row.ingredientName,
                                    sortOrder = row.ingredientSortOrder,
                                    categoryId = row.ingredientCategoryId,
                                    categoryName = row.ingredientCategoryName,
                                    categorySortOrder = row.ingredientCategorySortOrder,
                                ),
                                total = row.total,
                            )
                        },
                    )
                }.sortedWith(compareBy(ClosingResultGroup::sortOrder, ClosingResultGroup::name))
        }
        if (mutableUiState.value.date == date) {
            mutableUiState.value = mutableUiState.value.copy(
                menuGroups = menuGroups,
                quantities = quantities,
                resultGroups = resultGroups,
                isLoading = false,
                isSaved = closing != null,
                closingExists = closing != null,
                hasUnsavedChanges = false,
            )
        }
    }
}
