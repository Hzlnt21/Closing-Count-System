package com.closingcount.app.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "menus",
    foreignKeys = [
        ForeignKey(
            entity = MenuCategoryEntity::class,
            parentColumns = ["id"],
            childColumns = ["categoryId"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [
        Index(value = ["categoryId"]),
        Index(value = ["categoryId", "name"], unique = true),
    ],
)
data class MenuEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val categoryId: Long,
    val name: String,
    val sortOrder: Int,
    val isActive: Boolean = true,
)

