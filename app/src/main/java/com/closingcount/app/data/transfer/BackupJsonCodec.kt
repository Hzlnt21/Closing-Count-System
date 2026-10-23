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
    const val MAX_BACKUP_BYTES = 20 * 1024 * 1024

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
        require(bytes.size <= MAX_BACKUP_BYTES) { "File backup terlalu besar." }
        val root = runCatching {
            val text = Charsets.UTF_8.newDecoder()
                .onMalformedInput(java.nio.charset.CodingErrorAction.REPORT)
                .onUnmappableCharacter(java.nio.charset.CodingErrorAction.REPORT)
                .decode(java.nio.ByteBuffer.wrap(bytes)).toString()
            JSONObject(text)
        }
            .getOrElse { throw IllegalArgumentException("File bukan backup JSON yang valid.") }
        require(root.strictString("format") == FORMAT) { "Format file backup tidak dikenali." }
        require(root.strictInt("schemaVersion") == SCHEMA_VERSION) {
            "Versi file backup belum didukung."
        }
        require(root.strictLong("createdAt") >= 0) { "Waktu backup tidak valid." }
        val data = root.getJSONObject("data")
        val snapshot = BackupSnapshot(
            metadata = data.getJSONArray("metadata").mapObjects {
                AppMetadataEntity(key = it.strictString("key"), value = it.strictString("value"))
            },
            ingredientCategories = data.getJSONArray("ingredientCategories").mapObjects {
                IngredientCategoryEntity(
                    id = it.strictLong("id"),
                    name = it.strictString("name"),
                    sortOrder = it.strictInt("sortOrder"),
                    isActive = it.strictBoolean("isActive"),
                )
            },
            ingredients = data.getJSONArray("ingredients").mapObjects {
                IngredientEntity(
                    id = it.strictLong("id"),
                    categoryId = it.strictLong("categoryId"),
                    name = it.strictString("name"),
                    sortOrder = it.strictInt("sortOrder"),
                    isActive = it.strictBoolean("isActive"),
                )
            },
            menuCategories = data.getJSONArray("menuCategories").mapObjects {
                MenuCategoryEntity(
                    id = it.strictLong("id"),
                    name = it.strictString("name"),
                    sortOrder = it.strictInt("sortOrder"),
                    isActive = it.strictBoolean("isActive"),
                )
            },
            menus = data.getJSONArray("menus").mapObjects {
                MenuEntity(
                    id = it.strictLong("id"),
                    categoryId = it.strictLong("categoryId"),
                    name = it.strictString("name"),
                    sortOrder = it.strictInt("sortOrder"),
                    isActive = it.strictBoolean("isActive"),
                )
            },
            menuIngredients = data.getJSONArray("menuIngredients").mapObjects {
                MenuIngredientCrossRef(
                    menuId = it.strictLong("menuId"),
                    ingredientId = it.strictLong("ingredientId"),
                )
            },
            closings = data.getJSONArray("closings").mapObjects {
                ClosingEntity(
                    id = it.strictLong("id"),
                    date = it.strictString("date"),
                    updatedAt = it.strictLong("updatedAt"),
                )
            },
            closingMenuEntries = data.getJSONArray("closingMenuEntries").mapObjects {
                ClosingMenuEntryEntity(
                    closingId = it.strictLong("closingId"),
                    menuId = it.strictLong("menuId"),
                    menuName = it.strictString("menuName"),
                    menuSortOrder = it.strictInt("menuSortOrder"),
                    menuCategoryId = it.strictLong("menuCategoryId"),
                    menuCategoryName = it.strictString("menuCategoryName"),
                    menuCategorySortOrder = it.strictInt("menuCategorySortOrder"),
                    quantity = it.strictInt("quantity"),
                )
            },
            closingIngredientResults = data.getJSONArray("closingIngredientResults").mapObjects {
                ClosingIngredientResultEntity(
                    closingId = it.strictLong("closingId"),
                    ingredientId = it.strictLong("ingredientId"),
                    ingredientName = it.strictString("ingredientName"),
                    ingredientCategoryId = it.strictLong("ingredientCategoryId"),
                    ingredientCategoryName = it.strictString("ingredientCategoryName"),
                    ingredientCategorySortOrder = it.strictInt("ingredientCategorySortOrder"),
                    ingredientSortOrder = it.strictInt("ingredientSortOrder"),
                    total = it.strictInt("total"),
                )
            },
            closingMenuRecipes = data.getJSONArray("closingMenuRecipes").mapObjects {
                ClosingMenuRecipeEntity(
                    closingId = it.strictLong("closingId"),
                    menuId = it.strictLong("menuId"),
                    ingredientId = it.strictLong("ingredientId"),
                    ingredientName = it.strictString("ingredientName"),
                    ingredientSortOrder = it.strictInt("ingredientSortOrder"),
                    ingredientCategoryId = it.strictLong("ingredientCategoryId"),
                    ingredientCategoryName = it.strictString("ingredientCategoryName"),
                    ingredientCategorySortOrder = it.strictInt("ingredientCategorySortOrder"),
                )
            },
        )
        validate(snapshot)
        return snapshot
    }

    private fun validate(snapshot: BackupSnapshot) {
        require(snapshot.ingredientCategories.isNotEmpty()) { "Backup tidak memiliki kategori bahan." }
        requireUnique(snapshot.metadata.map { it.key }, "metadata")
        requireUnique(snapshot.ingredientCategories.map { it.id }, "ID kategori bahan")
        requireUnique(snapshot.ingredients.map { it.id }, "ID bahan")
        requireUnique(snapshot.menuCategories.map { it.id }, "ID kategori menu")
        requireUnique(snapshot.menus.map { it.id }, "ID menu")
        requireUnique(snapshot.closings.map { it.id }, "ID closing")
        requireUnique(snapshot.closings.map { it.date }, "tanggal closing")
        requireUnique(snapshot.menuIngredients.map { it.menuId to it.ingredientId }, "resep master")
        requireUnique(snapshot.closingMenuEntries.map { it.closingId to it.menuId }, "menu closing")
        requireUnique(snapshot.closingIngredientResults.map { it.closingId to it.ingredientId }, "bahan closing")
        requireUnique(snapshot.closingMenuRecipes.map { Triple(it.closingId, it.menuId, it.ingredientId) }, "resep closing")
        val identifiers = snapshot.ingredientCategories.map { it.id } + snapshot.ingredients.flatMap { listOf(it.id, it.categoryId) } +
            snapshot.menuCategories.map { it.id } + snapshot.menus.flatMap { listOf(it.id, it.categoryId) } +
            snapshot.closings.map { it.id } + snapshot.closingMenuEntries.map { it.menuId } +
            snapshot.closingIngredientResults.map { it.ingredientId } +
            snapshot.closingMenuRecipes.flatMap { listOf(it.menuId, it.ingredientId) }
        require(identifiers.all { it > 0 }) { "ID backup harus berupa bilangan bulat positif." }
        // Migrations 4->5 and 5->6 used categoryId=0 for history whose original category was unknown.
        val historicalCategories = snapshot.closingMenuEntries.map { it.menuCategoryId } +
            snapshot.closingIngredientResults.map { it.ingredientCategoryId } + snapshot.closingMenuRecipes.map { it.ingredientCategoryId }
        require(historicalCategories.all { it >= 0 }) { "Kategori historis backup tidak valid." }
        require(snapshot.closings.all { row -> row.updatedAt >= 0 && runCatching {
            val date = java.time.LocalDate.parse(row.date)
            row.date.matches(Regex("[0-9]{4}-[0-9]{2}-[0-9]{2}")) && date.year in 1..9999 && date.toString() == row.date
        }.getOrDefault(false) }) { "Tanggal atau waktu closing tidak valid." }
        val historicalMenus = snapshot.closingMenuEntries.mapTo(mutableSetOf()) { it.closingId to it.menuId }
        require(snapshot.closingMenuRecipes.all { (it.closingId to it.menuId) in historicalMenus }) {
            "Backup memiliki resep tanpa menu historis."
        }

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

    // JSONObject's numeric getters coerce strings, truncate fractions and narrow overflowing values.
    // A restore must reject those inputs before it reaches the transaction that replaces local history.
    private fun JSONObject.strictLong(key: String): Long {
        val value = get(key)
        require(value is Int || value is Long) { "Nilai $key harus berupa bilangan bulat." }
        return (value as Number).toLong()
    }
    private fun JSONObject.strictInt(key: String): Int {
        val value = strictLong(key)
        require(value in Int.MIN_VALUE.toLong()..Int.MAX_VALUE.toLong()) { "Nilai $key di luar batas." }
        return value.toInt()
    }
    private fun JSONObject.strictString(key: String): String {
        val value = get(key)
        require(value is String) { "Nilai $key harus berupa teks." }
        return value
    }
    private fun JSONObject.strictBoolean(key: String): Boolean {
        val value = get(key)
        require(value is Boolean) { "Nilai $key harus berupa benar/salah." }
        return value
    }
}
