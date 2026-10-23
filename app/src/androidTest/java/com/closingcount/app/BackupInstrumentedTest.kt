package com.closingcount.app

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.closingcount.app.data.local.*
import com.closingcount.app.data.transfer.BackupJsonCodec
import org.json.JSONObject
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

    @Test fun historicalNamesDeletedMasterIdsAndUnknownMigratedCategoriesRemainReadable() = runBlocking {
        val snapshot = historicalSnapshot()
        val bytes = BackupJsonCodec.encode(snapshot, 123)
        database.backupDao().restore(BackupJsonCodec.decode(bytes))
        assertEquals(snapshot, database.backupDao().createSnapshot())
        // Genuine old unknown-category sentinel remains zero; history is not inferred from today's masters.
        assertEquals(0L, database.closingDao().getMenuEntries(7).single().menuCategoryId)
        val large = validSnapshot().copy(ingredientCategories = listOf(IngredientCategoryEntity(Long.MAX_VALUE, "Bahan", 1)),
            ingredients = listOf(IngredientEntity(1, Long.MAX_VALUE, "Milk", 1)))
        assertEquals(large, BackupJsonCodec.decode(BackupJsonCodec.encode(large, 123)))
    }

    @Test fun coercedFractionalOverflowingAndNullValuesAreRejectedBeforeRestore() {
        val mutations: List<(JSONObject) -> Unit> = listOf(
            { it.data().getJSONArray("closingMenuEntries").getJSONObject(0).put("quantity", 1.75) },
            { it.data().getJSONArray("closingMenuEntries").getJSONObject(0).put("quantity", "3") },
            { it.data().getJSONArray("closingMenuEntries").getJSONObject(0).put("quantity", 4294967296L) },
            { it.data().getJSONArray("closingMenuEntries").getJSONObject(0).put("quantity", JSONObject.NULL) },
            { it.data().getJSONArray("ingredients").getJSONObject(0).put("id", 1.25) },
            { it.data().getJSONArray("ingredients").getJSONObject(0).put("id", 1e30) },
            { it.data().getJSONArray("ingredients").getJSONObject(0).put("isActive", "true") },
            { it.data().getJSONArray("ingredients").getJSONObject(0).put("name", 42) },
            { it.put("schemaVersion", "1") },
            { it.put("createdAt", -1) },
        )
        mutations.forEachIndexed { index, mutate ->
            val root = JSONObject(BackupJsonCodec.encode(historicalSnapshot(), 123).toString(Charsets.UTF_8))
            mutate(root)
            assertThrows("Invalid numeric/type case $index", IllegalArgumentException::class.java) {
                BackupJsonCodec.decode(root.toString().toByteArray(Charsets.UTF_8))
            }
        }
    }

    @Test fun invalidCalendarDatesAndNegativeIdentifiersAreRejected() {
        for (date in listOf("2026-02-30", "2026-2-03", "0000-01-01", "not-a-date")) {
            val snapshot = historicalSnapshot().let { it.copy(closings = listOf(it.closings.single().copy(date = date))) }
            assertThrows(IllegalArgumentException::class.java) { BackupJsonCodec.decode(BackupJsonCodec.encode(snapshot, 123)) }
        }
        val snapshot = validSnapshot().copy(ingredients = listOf(IngredientEntity(-1, 1, "Bad", 1)))
        assertThrows(IllegalArgumentException::class.java) { BackupJsonCodec.decode(BackupJsonCodec.encode(snapshot, 123)) }
    }

    @Test fun duplicateCompositeRowsAndMetadataAreRejected() {
        val seed = historicalSnapshot().copy(metadata = listOf(AppMetadataEntity("seed", "1")))
        for (key in listOf("metadata", "menuIngredients", "closingMenuEntries", "closingIngredientResults", "closingMenuRecipes")) {
            val root = JSONObject(BackupJsonCodec.encode(seed, 123).toString(Charsets.UTF_8))
            val rows = root.data().getJSONArray(key);rows.put(JSONObject(rows.getJSONObject(0).toString()))
            assertThrows("Duplicate $key", IllegalArgumentException::class.java) {
                BackupJsonCodec.decode(root.toString().toByteArray(Charsets.UTF_8))
            }
        }
    }

    @Test fun recipeWithoutItsHistoricalMenuIsRejected() {
        val snapshot = historicalSnapshot().let { it.copy(closingMenuRecipes = listOf(it.closingMenuRecipes.single().copy(menuId = 555))) }
        assertThrows(IllegalArgumentException::class.java) { BackupJsonCodec.decode(BackupJsonCodec.encode(snapshot, 123)) }
    }

    @Test fun corruptedUtf8AndOversizedBackupsAreRejected() {
        val bytes = BackupJsonCodec.encode(historicalSnapshot(), 123)
        val needle = "Old deleted menu".toByteArray(Charsets.UTF_8)
        val index = bytes.indices.first { i -> i + needle.size <= bytes.size && bytes.copyOfRange(i, i + needle.size).contentEquals(needle) }
        bytes[index] = 0xFF.toByte()
        assertThrows(IllegalArgumentException::class.java) { BackupJsonCodec.decode(bytes) }
        assertThrows(IllegalArgumentException::class.java) { BackupJsonCodec.decode(ByteArray(BackupJsonCodec.MAX_BACKUP_BYTES + 1)) }
    }

    @Test fun failedInsertionRollsBackAllDeletedLegacyRows() = runBlocking {
        val original = historicalSnapshot()
        database.backupDao().restore(original)
        val invalid = validSnapshot().let { it.copy(ingredients = it.ingredients + it.ingredients.single()) }
        val failed = runCatching { database.backupDao().restore(invalid) }.exceptionOrNull()
        org.junit.Assert.assertNotNull("Expected real SQLite duplicate constraint", failed)
        org.junit.Assert.assertTrue(failed is android.database.sqlite.SQLiteConstraintException)
        assertEquals(original, database.backupDao().createSnapshot())
    }

    @Test fun legacyRestoreLeavesOnlineHeadersEntriesPendingAndSentIntentsUntouched() = runBlocking {
        val sync = database.onlineSyncDao();val scope = "https://qa.invalid|account|outlet"
        val header = OnlineClosingEntity(scope, "closing", 1, 0, "DRAFT", "{}")
        val entry = OnlineEntryEntity(scope, "closing", "menu", 1, 0, 0, 0, "SYNCED")
        sync.pull(header, listOf(entry));sync.edit(scope, "closing", "menu", 4);sync.next(scope, "closing")
        sync.edit(scope, "closing", "menu", 7)
        sync.pull(header.copy(closingId = "lifecycle"), listOf(entry.copy(closingId = "lifecycle")))
        sync.prepareLifecycle(scope, "lifecycle", "submit", "", 1, 0, "EMPLOYEE");sync.nextLifecycle(scope, "lifecycle")
        val beforeEntry = sync.entry(scope, "closing", "menu")
        val beforeOps = sync.operations(scope, "closing")
        val beforeLifecycle = sync.lifecycle(scope, "lifecycle")
        database.backupDao().restore(BackupJsonCodec.decode(BackupJsonCodec.encode(historicalSnapshot(), 123)))
        assertEquals(header, sync.closing(scope, "closing"))
        assertEquals(beforeEntry, sync.entry(scope, "closing", "menu"))
        assertEquals(beforeOps, sync.operations(scope, "closing"))
        assertEquals(beforeLifecycle, sync.lifecycle(scope, "lifecycle"))
        val backup = JSONObject(BackupJsonCodec.encode(database.backupDao().createSnapshot(), 123).toString(Charsets.UTF_8))
        assertEquals(setOf("metadata","ingredientCategories","ingredients","menuCategories","menus","menuIngredients","closings","closingMenuEntries","closingIngredientResults","closingMenuRecipes"), backup.data().keys().asSequence().toSet())
    }

    private fun JSONObject.data() = getJSONObject("data")
    private fun historicalSnapshot() = validSnapshot().copy(
        closings = listOf(ClosingEntity(7, "2024-02-29", 123)),
        closingMenuEntries = listOf(ClosingMenuEntryEntity(7, 99, "Old deleted menu", 0, 0, "Unknown", 0, 3)),
        closingIngredientResults = listOf(ClosingIngredientResultEntity(7, 88, "Old ingredient", 0, "Unknown", 0, 0, 3)),
        closingMenuRecipes = listOf(ClosingMenuRecipeEntity(7, 99, 88, "Old ingredient", 0, 0, "Unknown", 0)),
    )

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
