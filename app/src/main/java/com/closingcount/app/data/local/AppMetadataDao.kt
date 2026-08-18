package com.closingcount.app.data.local

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface AppMetadataDao {
    @Query("SELECT * FROM app_metadata WHERE `key` = :key LIMIT 1")
    fun observe(key: String): Flow<AppMetadataEntity?>

    @Upsert
    suspend fun upsert(metadata: AppMetadataEntity)
}

