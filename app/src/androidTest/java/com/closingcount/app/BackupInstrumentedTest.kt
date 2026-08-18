package com.closingcount.app

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.closingcount.app.data.local.BackupSnapshot
import com.closingcount.app.data.local.ClosingCountDatabase
import com.closingcount.app.data.local.IngredientCategoryEntity
import com.closingcount.app.data.local.IngredientEntity
import com.closingcount.app.data.local.MenuCategoryEntity
import com.closingcount.app.data.local.MenuEntity
import com.closingcount.app.data.local.MenuIngredientCrossRef
import com.closingcount.app.data.transfer.BackupJsonCodec
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BackupInstrumentedTest {
    private lateinit var database: ClosingCountDatabase

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, ClosingCountDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun jsonRoundTripAndDatabaseRestorePreserveMasterData() = runBlocking {
        val snapshot = validSnapshot()
        val encoded = BackupJsonCodec.encode(snapshot, createdAt = 123)
        val decoded = BackupJsonCodec.decode(encoded)

        database.backupDao().restore(decoded)

        assertEquals(snapshot, database.backupDao().createSnapshot())
    }

    @Test
    fun invalidForeignReferenceIsRejected() {
        val invalid = validSnapshot().copy(
            ingredients = listOf(
                IngredientEntity(id = 1, categoryId = 999, name = "Rusak", sortOrder = 1),
            ),
        )
        val bytes = BackupJsonCodec.encode(invalid, createdAt = 123)

        assertThrows(IllegalArgumentException::class.java) {
            BackupJsonCodec.decode(bytes)
        }
    }

    private fun validSnapshot() = BackupSnapshot(
        metadata = emptyList(),
        ingredientCategories = listOf(IngredientCategoryEntity(1, "Bahan Baku", 1)),
        ingredients = listOf(IngredientEntity(1, 1, "Fresh Milk", 1)),
        menuCategories = listOf(MenuCategoryEntity(1, "Coffee", 1)),
        menus = listOf(MenuEntity(1, 1, "Latte", 1)),
        menuIngredients = listOf(MenuIngredientCrossRef(1, 1)),
        closings = emptyList(),
        closingMenuEntries = emptyList(),
        closingIngredientResults = emptyList(),
        closingMenuRecipes = emptyList(),
    )
}
