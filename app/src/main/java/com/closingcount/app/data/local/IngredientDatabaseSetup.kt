package com.closingcount.app.data.local

import androidx.sqlite.db.SupportSQLiteDatabase

object IngredientDatabaseSetup {
    fun createTables(database: SupportSQLiteDatabase) {
        database.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `ingredient_categories` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `name` TEXT NOT NULL,
                `sortOrder` INTEGER NOT NULL,
                `isActive` INTEGER NOT NULL
            )
            """.trimIndent(),
        )
        database.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_ingredient_categories_name` " +
                "ON `ingredient_categories` (`name`)",
        )
        database.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `ingredients` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `categoryId` INTEGER NOT NULL,
                `name` TEXT NOT NULL,
                `sortOrder` INTEGER NOT NULL,
                `isActive` INTEGER NOT NULL,
                FOREIGN KEY(`categoryId`) REFERENCES `ingredient_categories`(`id`)
                    ON UPDATE NO ACTION ON DELETE RESTRICT
            )
            """.trimIndent(),
        )
        database.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_ingredients_categoryId` " +
                "ON `ingredients` (`categoryId`)",
        )
        database.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_ingredients_categoryId_name` " +
                "ON `ingredients` (`categoryId`, `name`)",
        )
    }

    fun seed(database: SupportSQLiteDatabase) {
        var ingredientId = 1L
        InitialIngredientData.categories.forEachIndexed { categoryIndex, category ->
            val categoryId = categoryIndex + 1L
            database.execSQL(
                "INSERT OR IGNORE INTO ingredient_categories " +
                    "(id, name, sortOrder, isActive) VALUES (?, ?, ?, 1)",
                arrayOf<Any>(categoryId, category.name, categoryIndex + 1),
            )

            category.ingredients.forEachIndexed { ingredientIndex, ingredientName ->
                database.execSQL(
                    "INSERT OR IGNORE INTO ingredients " +
                        "(id, categoryId, name, sortOrder, isActive) VALUES (?, ?, ?, ?, 1)",
                    arrayOf<Any>(ingredientId, categoryId, ingredientName, ingredientIndex + 1),
                )
                ingredientId += 1
            }
        }
    }
}
