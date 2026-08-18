package com.closingcount.app

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.closingcount.app.data.local.ClosingCountDatabase
import com.closingcount.app.data.local.IngredientCategoryEntity
import com.closingcount.app.data.local.IngredientEntity
import com.closingcount.app.data.local.MenuCategoryEntity
import com.closingcount.app.data.local.MenuEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MenuDaoInstrumentedTest {
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
    fun recipeCanBeSavedAndReplacedWithoutDuplicateIngredients() = runBlocking {
        val ingredientCategoryId = database.ingredientDao().insertCategory(
            IngredientCategoryEntity(name = "Bahan Baku", sortOrder = 1),
        )
        val coffeeId = database.ingredientDao().insertIngredient(
            IngredientEntity(categoryId = ingredientCategoryId, name = "Biji Kopi", sortOrder = 1),
        )
        val milkId = database.ingredientDao().insertIngredient(
            IngredientEntity(categoryId = ingredientCategoryId, name = "Fresh Milk", sortOrder = 2),
        )
        val menuCategoryId = database.menuDao().insertCategory(
            MenuCategoryEntity(name = "Coffee", sortOrder = 1),
        )

        val menuId = database.menuDao().insertMenuWithRecipe(
            menu = MenuEntity(categoryId = menuCategoryId, name = "Latte", sortOrder = 1),
            ingredientIds = setOf(coffeeId, milkId, milkId),
        )

        assertEquals(setOf(coffeeId, milkId), database.menuDao().getIngredientIds(menuId).toSet())
        assertEquals(2, database.menuDao().observeMenuRows().first().single().ingredientCount)

        database.menuDao().updateMenuWithRecipe(
            menu = MenuEntity(
                id = menuId,
                categoryId = menuCategoryId,
                name = "Espresso",
                sortOrder = 1,
            ),
            ingredientIds = setOf(coffeeId),
        )

        assertEquals(listOf(coffeeId), database.menuDao().getIngredientIds(menuId))
        assertEquals(1, database.menuDao().observeMenuRows().first().single().ingredientCount)
    }
}
