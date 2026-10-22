package com.closingcount.app.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

// scope = canonical service origin + account UUID + outlet UUID; never a session token.
// Online cache deliberately has no foreign keys to legacy integer-ID history.
@Entity(tableName = "online_closings", primaryKeys = ["scope", "closingId"])
data class OnlineClosingEntity(
    val scope: String,
    val closingId: String,
    val generation: Long,
    val revision: Long,
    val state: String,
    val snapshotJson: String,
)

@Entity(tableName = "online_entries", primaryKeys = ["scope", "closingId", "menuId"])
data class OnlineEntryEntity(
    val scope: String,
    val closingId: String,
    val menuId: String,
    val generation: Long,
    val serverQuantity: Int,
    val serverRevision: Long,
    val localQuantity: Int,
    val status: String,
)

@Entity(
    tableName = "online_operations",
    indices = [Index(value = ["operationId"], unique = true), Index(value = ["scope", "closingId"])],
)
data class OnlineOperationEntity(
    @PrimaryKey(autoGenerate = true) val sequence: Long = 0,
    val operationId: String,
    val scope: String,
    val closingId: String,
    val menuId: String,
    val generation: Long,
    val expectedRevision: Long?,
    val quantity: Int,
    val status: String,
    val errorCode: String?,
)
