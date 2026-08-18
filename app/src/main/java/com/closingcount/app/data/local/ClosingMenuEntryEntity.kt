package com.closingcount.app.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(
    tableName = "closing_menu_entries",
    primaryKeys = ["closingId", "menuId"],
    foreignKeys = [
        ForeignKey(
            entity = ClosingEntity::class,
            parentColumns = ["id"],
            childColumns = ["closingId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["menuId"])],
)
data class ClosingMenuEntryEntity(
    val closingId: Long,
    val menuId: Long,
    val menuName: String,
    val menuSortOrder: Int,
    val menuCategoryId: Long,
    val menuCategoryName: String,
    val menuCategorySortOrder: Int,
    val quantity: Int,
)
