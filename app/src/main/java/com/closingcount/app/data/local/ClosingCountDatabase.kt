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
    ],
    version = 2,
    exportSchema = true,
)
abstract class ClosingCountDatabase : RoomDatabase() {
    abstract fun appMetadataDao(): AppMetadataDao
    abstract fun ingredientDao(): IngredientDao

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
    }
}
