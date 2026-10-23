package com.closingcount.app

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import com.closingcount.app.data.local.*
import java.util.UUID

/** Only synthetic files inserted by this helper are removed; never enumerates or clears Downloads. */
class LocalTransferQaFiles(private val context: Context) {
    private val owned = mutableListOf<Uri>()
    fun create(label: String, bytes: ByteArray): Pair<Uri, String> {
        require(label.matches(Regex("[a-z-]{1,30}")))
        val name = "ccs-qa-$label-${UUID.randomUUID()}.json"
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, name)
            put(MediaStore.Downloads.MIME_TYPE, "application/json")
            put(MediaStore.Downloads.RELATIVE_PATH, "Download/")
            put(MediaStore.Downloads.IS_PENDING, 1)
        }
        val uri = requireNotNull(context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values))
        owned += uri
        rewrite(uri, bytes)
        context.contentResolver.update(uri, ContentValues().apply { put(MediaStore.Downloads.IS_PENDING, 0) }, null, null)
        return uri to name
    }
    fun rewrite(uri: Uri, bytes: ByteArray) {
        require(uri in owned)
        requireNotNull(context.contentResolver.openOutputStream(uri, "wt")).use { it.write(bytes) }
    }
    fun close() { owned.forEach { context.contentResolver.delete(it, null, null) }; owned.clear() }
}

fun localTransferQaSnapshot(name: String, history: Boolean = false) = BackupSnapshot(
    metadata = emptyList(),
    ingredientCategories = listOf(IngredientCategoryEntity(1, "Bahan QA", 1)),
    ingredients = if (history) listOf(IngredientEntity(51, 1, "Susu QA", 1), IngredientEntity(52, 1, "Kopi QA", 2))
        else listOf(IngredientEntity(51, 1, "Bahan awal QA", 1)),
    menuCategories = listOf(MenuCategoryEntity(1, "Menu QA", 1)),
    menus = listOf(MenuEntity(41, 1, name, 1)),
    menuIngredients = listOf(MenuIngredientCrossRef(41, 51)),
    closings = if (history) listOf(ClosingEntity(101, "2024-02-29", 123), ClosingEntity(102, "2026-01-01", 123), ClosingEntity(103, "2026-10-23", 123)) else emptyList(),
    closingMenuEntries = if (history) (101L..103L).map { ClosingMenuEntryEntity(it, 41, name, 1, 1, "Menu QA", 1, 2) } else emptyList(),
    closingIngredientResults = if (history) (101L..103L).map { ClosingIngredientResultEntity(it, 51, "Susu QA", 1, "Bahan QA", 1, 1, 2) } else emptyList(),
    closingMenuRecipes = if (history) (101L..103L).map { ClosingMenuRecipeEntity(it, 41, 51, "Susu QA", 1, 1, "Bahan QA", 1) } else emptyList(),
)
