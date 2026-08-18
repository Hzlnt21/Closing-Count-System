package com.closingcount.app

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.closingcount.app.data.local.ClosingCountDatabase
import com.closingcount.app.data.local.ClosingIngredientResultEntity
import com.closingcount.app.data.local.ClosingMenuEntryEntity
import com.closingcount.app.data.local.ClosingMenuRecipeEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ClosingDaoInstrumentedTest {
    private lateinit var database: ClosingCountDatabase

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(
            context,
            ClosingCountDatabase::class.java,
        ).allowMainThreadQueries().build()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun sameDateUpdatesOneClosingAndReplacesItsSnapshots() = runBlocking {
        val dao = database.closingDao()
        val firstId = dao.replaceClosing(
            date = "2026-08-18",
            updatedAt = 100,
            entries = listOf(menuEntry(quantity = 2)),
            results = listOf(ingredientResult(total = 2)),
            recipes = listOf(recipe()),
        )
        val secondId = dao.replaceClosing(
            date = "2026-08-18",
            updatedAt = 200,
            entries = listOf(menuEntry(quantity = 3)),
            results = listOf(ingredientResult(total = 3)),
            recipes = listOf(recipe()),
        )

        assertEquals(firstId, secondId)
        assertNotEquals(0L, firstId)
        assertEquals(200L, dao.getClosingByDate("2026-08-18")?.updatedAt)
        assertEquals(3, dao.getMenuEntries(firstId).single().quantity)
        assertEquals(3, dao.getIngredientResults(firstId).single().total)
        assertEquals("Fresh Milk", dao.getMenuRecipes(firstId).single().ingredientName)
        val history = dao.observeHistoryRows().first().single()
        assertEquals(3, history.totalMenusSold)
        assertEquals(1, history.soldMenuTypes)
        assertEquals(1, history.ingredientCount)
    }

    private fun menuEntry(quantity: Int) = ClosingMenuEntryEntity(
        closingId = 0,
        menuId = 1,
        menuName = "Latte",
        menuSortOrder = 1,
        menuCategoryId = 1,
        menuCategoryName = "Coffee",
        menuCategorySortOrder = 1,
        quantity = quantity,
    )

    private fun ingredientResult(total: Int) = ClosingIngredientResultEntity(
        closingId = 0,
        ingredientId = 1,
        ingredientName = "Fresh Milk",
        ingredientCategoryId = 1,
        ingredientCategoryName = "Bahan Baku",
        ingredientCategorySortOrder = 1,
        ingredientSortOrder = 1,
        total = total,
    )

    private fun recipe() = ClosingMenuRecipeEntity(
        closingId = 0,
        menuId = 1,
        ingredientId = 1,
        ingredientName = "Fresh Milk",
        ingredientSortOrder = 1,
        ingredientCategoryId = 1,
        ingredientCategoryName = "Bahan Baku",
        ingredientCategorySortOrder = 1,
    )
}
