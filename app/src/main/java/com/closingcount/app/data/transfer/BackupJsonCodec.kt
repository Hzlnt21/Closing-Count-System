package com.closingcount.app.data.transfer

import com.closingcount.app.data.local.AppMetadataEntity
import com.closingcount.app.data.local.BackupSnapshot
import com.closingcount.app.data.local.ClosingEntity
import com.closingcount.app.data.local.ClosingIngredientResultEntity
import com.closingcount.app.data.local.ClosingMenuEntryEntity
import com.closingcount.app.data.local.ClosingMenuRecipeEntity
import com.closingcount.app.data.local.IngredientCategoryEntity
import com.closingcount.app.data.local.IngredientEntity
import com.closingcount.app.data.local.MenuCategoryEntity
import com.closingcount.app.data.local.MenuEntity
import com.closingcount.app.data.local.MenuIngredientCrossRef
import org.json.JSONArray
import org.json.JSONObject

object BackupJsonCodec {
    const val FORMAT = "closing-count-system-backup"
    const val SCHEMA_VERSION = 1

    fun encode(snapshot: BackupSnapshot, createdAt: Long): ByteArray {
        val data = JSONObject()
            .put("metadata", array(snapshot.metadata) { JSONObject().put("key", it.key).put("value", it.value) })
            .put("ingredientCategories", array(snapshot.ingredientCategories) {
                JSONObject().put("id", it.id).put("name", it.name).put("sortOrder", it.sortOrder)
                    .put("isActive", it.isActive)
            })
            .put("ingredients", array(snapshot.ingredients) {
                JSONObject().put("id", it.id).put("categoryId", it.categoryId).put("name", it.name)
                    .put("sortOrder", it.sortOrder).put("isActive", it.isActive)
            })
            .put("menuCategories", array(snapshot.menuCategories) {
                JSONObject().put("id", it.id).put("name", it.name).put("sortOrder", it.sortOrder)
                    .put("isActive", it.isActive)
            })
            .put("menus", array(snapshot.menus) {
                JSONObject().put("id", it.id).put("categoryId", it.categoryId).put("name", it.name)
                    .put("sortOrder", it.sortOrder).put("isActive", it.isActive)
            })
            .put("menuIngredients", array(snapshot.menuIngredients) {
                JSONObject().put("menuId", it.menuId).put("ingredientId", it.ingredientId)
            })
            .put("closings", array(snapshot.closings) {
                JSONObject().put("id", it.id).put("date", it.date).put("updatedAt", it.updatedAt)
            })
            .put("closingMenuEntries", array(snapshot.closingMenuEntries) {
                JSONObject().put("closingId", it.closingId).put("menuId", it.menuId)
                    .put("menuName", it.menuName).put("menuSortOrder", it.menuSortOrder)
                    .put("menuCategoryId", it.menuCategoryId).put("menuCategoryName", it.menuCategoryName)
                    .put("menuCategorySortOrder", it.menuCategorySortOrder).put("quantity", it.quantity)
            })
            .put("closingIngredientResults", array(snapshot.closingIngredientResults) {
                JSONObject().put("closingId", it.closingId).put("ingredientId", it.ingredientId)
                    .put("ingredientName", it.ingredientName)
                    .put("ingredientCategoryId", it.ingredientCategoryId)
                    .put("ingredientCategoryName", it.ingredientCategoryName)
                    .put("ingredientCategorySortOrder", it.ingredientCategorySortOrder)
                    .put("ingredientSortOrder", it.ingredientSortOrder).put("total", it.total)
            })
            .put("closingMenuRecipes", array(snapshot.closingMenuRecipes) {
                JSONObject().put("closingId", it.closingId).put("menuId", it.menuId)
                    .put("ingredientId", it.ingredientId).put("ingredientName", it.ingredientName)
                    .put("ingredientSortOrder", it.ingredientSortOrder)
                    .put("ingredientCategoryId", it.ingredientCategoryId)
                    .put("ingredientCategoryName", it.ingredientCategoryName)
                    .put("ingredientCategorySortOrder", it.ingredientCategorySortOrder)
            })

        return JSONObject()
            .put("format", FORMAT)
            .put("schemaVersion", SCHEMA_VERSION)
            .put("createdAt", createdAt)
            .put("data", data)
            .toString(2)
            .toByteArray(Charsets.UTF_8)
    }

    fun decode(bytes: ByteArray): BackupSnapshot {
        require(bytes.isNotEmpty()) { "File backup kosong." }
        val root = runCatching { JSONObject(bytes.toString(Charsets.UTF_8)) }
            .getOrElse { throw IllegalArgumentException("File bukan backup JSON yang valid.") }
        require(root.getString("format") == FORMAT) { "Format file backup tidak dikenali." }
        require(root.getInt("schemaVersion") == SCHEMA_VERSION) {
            "Versi file backup belum didukung."
        }
        val data = root.getJSONObject("data")
        val snapshot = BackupSnapshot(
            metadata = data.getJSONArray("metadata").mapObjects {
                AppMetadataEntity(key = it.getString("key"), value = it.getString("value"))
            },
            ingredientCategories = data.getJSONArray("ingredientCategories").mapObjects {
                IngredientCategoryEntity(
                    id = it.getLong("id"),
                    name = it.getString("name"),
                    sortOrder = it.getInt("sortOrder"),
                    isActive = it.getBoolean("isActive"),
                )
            },
            ingredients = data.getJSONArray("ingredients").mapObjects {
                IngredientEntity(
                    id = it.getLong("id"),
                    categoryId = it.getLong("categoryId"),
                    name = it.getString("name"),
                    sortOrder = it.getInt("sortOrder"),
                    isActive = it.getBoolean("isActive"),
                )
            },
            menuCategories = data.getJSONArray("menuCategories").mapObjects {
                MenuCategoryEntity(
                    id = it.getLong("id"),
                    name = it.getString("name"),
                    sortOrder = it.getInt("sortOrder"),
                    isActive = it.getBoolean("isActive"),
                )
            },
            menus = data.getJSONArray("menus").mapObjects {
                MenuEntity(
                    id = it.getLong("id"),
                    categoryId = it.getLong("categoryId"),
                    name = it.getString("name"),
                    sortOrder = it.getInt("sortOrder"),
                    isActive = it.getBoolean("isActive"),
                )
            },
            menuIngredients = data.getJSONArray("menuIngredients").mapObjects {
                MenuIngredientCrossRef(
                    menuId = it.getLong("menuId"),
                    ingredientId = it.getLong("ingredientId"),
                )
            },
            closings = data.getJSONArray("closings").mapObjects {
                ClosingEntity(
                    id = it.getLong("id"),
                    date = it.getString("date"),
                    updatedAt = it.getLong("updatedAt"),
                )
            },
            closingMenuEntries = data.getJSONArray("closingMenuEntries").mapObjects {
                ClosingMenuEntryEntity(
                    closingId = it.getLong("closingId"),
                    menuId = it.getLong("menuId"),
                    menuName = it.getString("menuName"),
                    menuSortOrder = it.getInt("menuSortOrder"),
                    menuCategoryId = it.getLong("menuCategoryId"),
                    menuCategoryName = it.getString("menuCategoryName"),
                    menuCategorySortOrder = it.getInt("menuCategorySortOrder"),
                    quantity = it.getInt("quantity"),
                )
            },
            closingIngredientResults = data.getJSONArray("closingIngredientResults").mapObjects {
                ClosingIngredientResultEntity(
                    closingId = it.getLong("closingId"),
                    ingredientId = it.getLong("ingredientId"),
                    ingredientName = it.getString("ingredientName"),
                    ingredientCategoryId = it.getLong("ingredientCategoryId"),
                    ingredientCategoryName = it.getString("ingredientCategoryName"),
                    ingredientCategorySortOrder = it.getInt("ingredientCategorySortOrder"),
                    ingredientSortOrder = it.getInt("ingredientSortOrder"),
                    total = it.getInt("total"),
                )
            },
            closingMenuRecipes = data.getJSONArray("closingMenuRecipes").mapObjects {
                ClosingMenuRecipeEntity(
                    closingId = it.getLong("closingId"),
                    menuId = it.getLong("menuId"),
                    ingredientId = it.getLong("ingredientId"),
                    ingredientName = it.getString("ingredientName"),
                    ingredientSortOrder = it.getInt("ingredientSortOrder"),
                    ingredientCategoryId = it.getLong("ingredientCategoryId"),
                    ingredientCategoryName = it.getString("ingredientCategoryName"),
                    ingredientCategorySortOrder = it.getInt("ingredientCategorySortOrder"),
                )
            },
        )
        validate(snapshot)
        return snapshot
    }

    private fun validate(snapshot: BackupSnapshot) {
        require(snapshot.ingredientCategories.isNotEmpty()) { "Backup tidak memiliki kategori bahan." }
        requireUnique(snapshot.ingredientCategories.map { it.id }, "ID kategori bahan")
        requireUnique(snapshot.ingredients.map { it.id }, "ID bahan")
        requireUnique(snapshot.menuCategories.map { it.id }, "ID kategori menu")
        requireUnique(snapshot.menus.map { it.id }, "ID menu")
        requireUnique(snapshot.closings.map { it.id }, "ID closing")
        requireUnique(snapshot.closings.map { it.date }, "tanggal closing")

        val ingredientCategoryIds = snapshot.ingredientCategories.mapTo(mutableSetOf()) { it.id }
        val ingredientIds = snapshot.ingredients.mapTo(mutableSetOf()) { it.id }
        val menuCategoryIds = snapshot.menuCategories.mapTo(mutableSetOf()) { it.id }
        val menuIds = snapshot.menus.mapTo(mutableSetOf()) { it.id }
        val closingIds = snapshot.closings.mapTo(mutableSetOf()) { it.id }
        require(snapshot.ingredients.all { it.categoryId in ingredientCategoryIds }) {
            "Backup memiliki bahan tanpa kategori yang valid."
        }
        require(snapshot.menus.all { it.categoryId in menuCategoryIds }) {
            "Backup memiliki menu tanpa kategori yang valid."
        }
        require(snapshot.menuIngredients.all { it.menuId in menuIds && it.ingredientId in ingredientIds }) {
            "Backup memiliki resep master yang tidak valid."
        }
        require(snapshot.closingMenuEntries.all { it.closingId in closingIds }) {
            "Backup memiliki detail menu tanpa closing."
        }
        require(snapshot.closingIngredientResults.all { it.closingId in closingIds }) {
            "Backup memiliki hasil bahan tanpa closing."
        }
        require(snapshot.closingMenuRecipes.all { it.closingId in closingIds }) {
            "Backup memiliki snapshot resep tanpa closing."
        }
        require(snapshot.closingMenuEntries.all { it.quantity in 0..9999 }) {
            "Backup memiliki jumlah menu yang tidak valid."
        }
        require(snapshot.closingIngredientResults.all { it.total >= 0 }) {
            "Backup memiliki total bahan yang tidak valid."
        }
    }

    private fun <T> array(items: List<T>, transform: (T) -> JSONObject): JSONArray =
        JSONArray().apply { items.forEach { put(transform(it)) } }

    private fun <T> JSONArray.mapObjects(transform: (JSONObject) -> T): List<T> =
        List(length()) { index -> transform(getJSONObject(index)) }

    private fun <T> requireUnique(values: List<T>, label: String) {
        require(values.size == values.toSet().size) { "Backup memiliki duplikat $label." }
    }
}
