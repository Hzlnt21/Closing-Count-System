package com.closingcount.app.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "closings",
    indices = [Index(value = ["date"], unique = true)],
)
data class ClosingEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val date: String,
    val updatedAt: Long,
)
