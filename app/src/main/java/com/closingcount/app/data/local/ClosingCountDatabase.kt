package com.closingcount.app.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [AppMetadataEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class ClosingCountDatabase : RoomDatabase() {
    abstract fun appMetadataDao(): AppMetadataDao

    companion object {
        @Volatile
        private var instance: ClosingCountDatabase? = null

        fun getInstance(context: Context): ClosingCountDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    ClosingCountDatabase::class.java,
                    "closing_count.db",
                ).build().also { instance = it }
            }
    }
}

