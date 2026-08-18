package com.closingcount.app.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        AppMetadataEntity::class,
        IngredientCategoryEntity::class,
        IngredientEntity::class,
        MenuCategoryEntity::class,
        MenuEntity::class,
        MenuIngredientCrossRef::class,
        ClosingEntity::class,
        ClosingMenuEntryEntity::class,
        ClosingIngredientResultEntity::class,
    ],
    version = 5,
    exportSchema = true,
)
abstract class ClosingCountDatabase : RoomDatabase() {
    abstract fun appMetadataDao(): AppMetadataDao
    abstract fun ingredientDao(): IngredientDao
    abstract fun menuDao(): MenuDao
    abstract fun closingDao(): ClosingDao

    companion object {
        @Volatile
        private var instance: ClosingCountDatabase? = null

        fun getInstance(context: Context): ClosingCountDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    ClosingCountDatabase::class.java,
                    "closing_count.db",
                )
                    .addMigrations(Migration1To2)
                    .addMigrations(Migration2To3)
                    .addMigrations(Migration3To5)
                    .addMigrations(Migration4To5)
                    .addCallback(
                        object : Callback() {
                            override fun onCreate(db: SupportSQLiteDatabase) {
                                super.onCreate(db)
                                IngredientDatabaseSetup.seed(db)
                            }
                        },
                    )
                    .build()
                    .also { instance = it }
            }

        private val Migration1To2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                IngredientDatabaseSetup.createTables(db)
                IngredientDatabaseSetup.seed(db)
            }
        }

        private val Migration2To3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                MenuDatabaseSetup.createTables(db)
            }
        }

        private val Migration3To5 = object : Migration(3, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                ClosingDatabaseSetup.createTables(db)
            }
        }

        private val Migration4To5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE `closing_ingredient_results` " +
                        "ADD COLUMN `ingredientCategoryId` INTEGER NOT NULL DEFAULT 0",
                )
            }
        }
    }
}
