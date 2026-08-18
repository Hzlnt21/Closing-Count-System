package com.closingcount.app.data.local

import androidx.sqlite.db.SupportSQLiteDatabase

object MenuDatabaseSetup {
    fun createTables(database: SupportSQLiteDatabase) {
        database.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `menu_categories` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `name` TEXT NOT NULL,
                `sortOrder` INTEGER NOT NULL,
                `isActive` INTEGER NOT NULL
            )
            """.trimIndent(),
        )
        database.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_menu_categories_name` " +
                "ON `menu_categories` (`name`)",
        )
        database.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `menus` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `categoryId` INTEGER NOT NULL,
                `name` TEXT NOT NULL,
                `sortOrder` INTEGER NOT NULL,
                `isActive` INTEGER NOT NULL,
                FOREIGN KEY(`categoryId`) REFERENCES `menu_categories`(`id`)
                    ON UPDATE NO ACTION ON DELETE RESTRICT
            )
            """.trimIndent(),
        )
        database.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_menus_categoryId` ON `menus` (`categoryId`)",
        )
        database.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_menus_categoryId_name` " +
                "ON `menus` (`categoryId`, `name`)",
        )
        database.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `menu_ingredients` (
                `menuId` INTEGER NOT NULL,
                `ingredientId` INTEGER NOT NULL,
                PRIMARY KEY(`menuId`, `ingredientId`),
                FOREIGN KEY(`menuId`) REFERENCES `menus`(`id`)
                    ON UPDATE NO ACTION ON DELETE CASCADE,
                FOREIGN KEY(`ingredientId`) REFERENCES `ingredients`(`id`)
                    ON UPDATE NO ACTION ON DELETE RESTRICT
            )
            """.trimIndent(),
        )
        database.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_menu_ingredients_ingredientId` " +
                "ON `menu_ingredients` (`ingredientId`)",
        )
    }
}

