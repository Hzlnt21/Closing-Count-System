package com.closingcount.app.data.local

import androidx.sqlite.db.SupportSQLiteDatabase

object ClosingDatabaseSetup {
    fun createTables(database: SupportSQLiteDatabase) {
        database.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `closings` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `date` TEXT NOT NULL,
                `updatedAt` INTEGER NOT NULL
            )
            """.trimIndent(),
        )
        database.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_closings_date` ON `closings` (`date`)",
        )
        database.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `closing_menu_entries` (
                `closingId` INTEGER NOT NULL,
                `menuId` INTEGER NOT NULL,
                `menuName` TEXT NOT NULL,
                `menuCategoryName` TEXT NOT NULL,
                `quantity` INTEGER NOT NULL,
                PRIMARY KEY(`closingId`, `menuId`),
                FOREIGN KEY(`closingId`) REFERENCES `closings`(`id`)
                    ON UPDATE NO ACTION ON DELETE CASCADE
            )
            """.trimIndent(),
        )
        database.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_closing_menu_entries_menuId` " +
                "ON `closing_menu_entries` (`menuId`)",
        )
        database.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `closing_ingredient_results` (
                `closingId` INTEGER NOT NULL,
                `ingredientId` INTEGER NOT NULL,
                `ingredientName` TEXT NOT NULL,
                `ingredientCategoryId` INTEGER NOT NULL,
                `ingredientCategoryName` TEXT NOT NULL,
                `ingredientCategorySortOrder` INTEGER NOT NULL,
                `ingredientSortOrder` INTEGER NOT NULL,
                `total` INTEGER NOT NULL,
                PRIMARY KEY(`closingId`, `ingredientId`),
                FOREIGN KEY(`closingId`) REFERENCES `closings`(`id`)
                    ON UPDATE NO ACTION ON DELETE CASCADE
            )
            """.trimIndent(),
        )
        database.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_closing_ingredient_results_ingredientId` " +
                "ON `closing_ingredient_results` (`ingredientId`)",
        )
    }
}
