package com.closingcount.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import java.util.UUID
import kotlinx.coroutines.flow.Flow

@Dao
abstract class OnlineSyncDao {
    @Query("SELECT * FROM online_lifecycle_operations WHERE scope=:scope AND closingId=:closingId")
    abstract suspend fun lifecycle(scope: String, closingId: String): OnlineLifecycleEntity?

    @Query("SELECT * FROM online_lifecycle_operations WHERE scope=:scope AND closingId=:closingId")
    abstract fun observeLifecycle(scope: String, closingId: String): Flow<OnlineLifecycleEntity?>

    @Insert
    protected abstract suspend fun insertLifecycle(value: OnlineLifecycleEntity)

    @Update
    protected abstract suspend fun updateLifecycle(value: OnlineLifecycleEntity)

    @Query("DELETE FROM online_lifecycle_operations WHERE scope=:scope AND closingId=:closingId AND operationId=:operationId")
    protected abstract suspend fun deleteLifecycle(scope: String, closingId: String, operationId: String)

    @Transaction
    open suspend fun prepareLifecycle(scope: String, closingId: String, action: String, reason: String,
        reviewedGeneration: Long, reviewedRevision: Long, role: String): String {
        require(action in setOf("submit", "return", "lock", "reopen"))
        require(role in setOf("EMPLOYEE", "SUPERVISOR", "ADMIN"))
        check(action == "submit" || role != "EMPLOYEE")
        check(lifecycle(scope, closingId) == null && operations(scope, closingId).isEmpty())
        val rows = entries(scope, closingId)
        check(rows.isNotEmpty() && rows.all { it.status == "SYNCED" })
        val header = requireNotNull(closing(scope, closingId))
        check(header.generation == reviewedGeneration && header.revision == reviewedRevision) { "REVIEW_CHANGED" }
        check(header.state == lifecycleSourceState(action))
        val trimmed = reason.trim()
        if (action in setOf("return", "reopen")) require(trimmed.isNotBlank() && trimmed.length <= 500)
        else require(trimmed.isEmpty())
        val id = UUID.randomUUID().toString()
        insertLifecycle(OnlineLifecycleEntity(scope, closingId, id, action, reviewedGeneration, reviewedRevision,
            trimmed, "QUEUED", false, null))
        return id
    }

    @Transaction
    open suspend fun nextLifecycle(scope: String, closingId: String): OnlineLifecycleEntity? {
        val op = lifecycle(scope, closingId) ?: return null
        if (op.status !in setOf("QUEUED", "SENT")) return null
        check(operations(scope, closingId).isEmpty())
        val header = requireNotNull(closing(scope, closingId))
        if (!op.wasSent && (header.generation != op.generation || header.revision != op.expectedRevision ||
                header.state != lifecycleSourceState(op.action))) {
            updateLifecycle(op.copy(status = "CONFLICT", errorCode = "LIFECYCLE_REVIEW_CHANGED")); return null
        }
        return op.copy(status = "SENT", wasSent = true).also { updateLifecycle(it) }
    }

    @Transaction
    open suspend fun blockLifecycle(scope: String, closingId: String, operationId: String, status: String, code: String) {
        require(status in setOf("AUTH_REQUIRED", "CONFLICT", "FAILED"))
        val op = lifecycle(scope, closingId) ?: return
        check(op.operationId == operationId)
        updateLifecycle(op.copy(status = status, errorCode = code))
    }

    @Transaction
    open suspend fun completeLifecycle(scope: String, closingId: String, operationId: String, ackState: String,
        ackGeneration: Long, ackRevision: Long, remote: OnlineClosingEntity, rows: List<OnlineEntryEntity>) {
        val op = lifecycle(scope, closingId) ?: return
        if (op.operationId != operationId) return // an old callback must not remove a new action
        check(op.status == "SENT" && op.wasSent)
        val expectedState = when (op.action) { "submit" -> "SUBMITTED"; "lock" -> "LOCKED"; else -> "DRAFT" }
        val nextGeneration = op.generation + if (op.action in setOf("return", "reopen")) 1 else 0
        require(ackState == expectedState && ackGeneration == nextGeneration && ackRevision == op.expectedRevision + 1)
        require(remote.scope == scope && remote.closingId == closingId && remote.generation >= ackGeneration && remote.revision >= ackRevision)
        pull(remote, rows)
        deleteLifecycle(scope, closingId, operationId)
    }

    @Transaction
    open suspend fun dismissLifecycle(scope: String, closingId: String, operationId: String) {
        val op = requireNotNull(lifecycle(scope, closingId))
        check(op.operationId == operationId && op.status in setOf("CONFLICT", "FAILED"))
        deleteLifecycle(scope, closingId, operationId)
    }

    @Query("SELECT * FROM online_closings WHERE scope=:scope")
    abstract fun observeClosings(scope: String): Flow<List<OnlineClosingEntity>>

    @Query("SELECT * FROM online_closings WHERE scope=:scope AND closingId=:closingId")
    abstract fun observeClosing(scope: String, closingId: String): Flow<OnlineClosingEntity?>

    @Query("SELECT * FROM online_entries WHERE scope=:scope AND closingId=:closingId")
    abstract fun observeEntries(scope: String, closingId: String): Flow<List<OnlineEntryEntity>>

    @Query("SELECT * FROM online_operations WHERE scope=:scope AND closingId=:closingId ORDER BY sequence")
    abstract fun observeOperations(scope: String, closingId: String): Flow<List<OnlineOperationEntity>>

    @Query("SELECT * FROM online_entries WHERE scope=:scope AND closingId=:closingId")
    abstract suspend fun entries(scope: String, closingId: String): List<OnlineEntryEntity>

    @Transaction
    open suspend fun editBatch(scope: String, closingId: String, quantities: Map<String, Int>) {
        require(quantities.values.all { it in 0..9999 })
        for ((menuId, quantity) in quantities) {
            val current = requireNotNull(entry(scope, closingId, menuId))
            if (current.localQuantity != quantity) edit(scope, closingId, menuId, quantity)
        }
    }

    @Query("SELECT (SELECT COUNT(*) FROM online_operations WHERE substr(scope,1,length(:accountPrefix))=:accountPrefix) + (SELECT COUNT(*) FROM online_lifecycle_operations WHERE substr(scope,1,length(:accountPrefix))=:accountPrefix)")
    abstract suspend fun pendingForAccount(accountPrefix: String): Int

    @Query("SELECT closingId FROM online_closings WHERE scope=:scope")
    abstract suspend fun closingIds(scope: String): List<String>

    @Query("SELECT * FROM online_closings WHERE scope=:scope AND closingId=:closingId")
    abstract suspend fun closing(scope: String, closingId: String): OnlineClosingEntity?

    @Query("SELECT * FROM online_entries WHERE scope=:scope AND closingId=:closingId AND menuId=:menuId")
    abstract suspend fun entry(scope: String, closingId: String, menuId: String): OnlineEntryEntity?

    @Query("SELECT * FROM online_operations WHERE scope=:scope AND closingId=:closingId ORDER BY sequence")
    abstract suspend fun operations(scope: String, closingId: String): List<OnlineOperationEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    protected abstract suspend fun putClosing(value: OnlineClosingEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    protected abstract suspend fun putEntry(value: OnlineEntryEntity)

    @Insert
    protected abstract suspend fun insertOperation(value: OnlineOperationEntity): Long

    @Update
    protected abstract suspend fun updateOperation(value: OnlineOperationEntity)

    @Query("DELETE FROM online_operations WHERE sequence=:sequence")
    protected abstract suspend fun deleteOperation(sequence: Long)

    @Transaction
    open suspend fun pull(remote: OnlineClosingEntity, entries: List<OnlineEntryEntity>) {
        require(remote.generation >= 1 && remote.revision >= 0)
        require(remote.state in setOf("DRAFT", "SUBMITTED", "LOCKED"))
        require(entries.map { it.menuId }.distinct().size == entries.size)
        val old = closing(remote.scope, remote.closingId)
        // An old response (including one started before an ack) cannot regress local metadata.
        if (old != null && remote.revision < old.revision) return
        require(old == null || remote.generation >= old.generation)
        val pending = operations(remote.scope, remote.closingId)
        putClosing(remote)
        for (incoming in entries) {
            require(incoming.scope == remote.scope && incoming.closingId == remote.closingId)
            require(incoming.generation == remote.generation && incoming.serverRevision >= 0)
            require(incoming.serverQuantity in 0..9999)
            val current = entry(remote.scope, remote.closingId, incoming.menuId)
            val queue = pending.filter { it.menuId == incoming.menuId }
            if (queue.isEmpty()) {
                putEntry(incoming.copy(localQuantity = incoming.serverQuantity, status = "SYNCED"))
            } else {
                requireNotNull(current)
                val changed = queue.first().generation != remote.generation || remote.state != "DRAFT" ||
                    incoming.serverRevision != current.serverRevision
                // A SENT operation may have committed remotely: preserve its exact retry until receipt.
                // QUEUED operations can be blocked immediately by a changed server base.
                val conflict = changed && queue.first().status != "SENT"
                putEntry(incoming.copy(localQuantity = current.localQuantity,
                    status = if (conflict) "CONFLICT" else current.status))
                if (conflict) for (op in queue) updateOperation(op.copy(status = "CONFLICT", errorCode = "PULL_CONFLICT"))
            }
        }
    }

    @Transaction
    open suspend fun edit(scope: String, closingId: String, menuId: String, quantity: Int): String {
        require(quantity in 0..9999)
        check(lifecycle(scope, closingId) == null) { "LIFECYCLE_PENDING" }
        val header = requireNotNull(closing(scope, closingId))
        check(header.state == "DRAFT")
        val current = requireNotNull(entry(scope, closingId, menuId))
        check(current.status !in setOf("CONFLICT", "FAILED", "AUTH_REQUIRED"))
        check(current.generation == header.generation)
        val queue = operations(scope, closingId).filter { it.menuId == menuId }
        val id = UUID.randomUUID().toString()
        // Every edit receives an immutable operation; a dependent base is assigned only after ack.
        insertOperation(OnlineOperationEntity(operationId = id, scope = scope, closingId = closingId,
            menuId = menuId, generation = header.generation,
            expectedRevision = if (queue.isEmpty()) current.serverRevision else null,
            quantity = quantity, status = "QUEUED", errorCode = null))
        putEntry(current.copy(localQuantity = quantity, status = "PENDING"))
        return id
    }

    @Transaction
    open suspend fun next(scope: String, closingId: String): OnlineOperationEntity? {
        val first = operations(scope, closingId).firstOrNull() ?: return null
        if (first.status !in setOf("QUEUED", "SENT")) return null
        check(first.expectedRevision != null)
        val sent = first.copy(status = "SENT")
        updateOperation(sent)
        val current = requireNotNull(entry(scope, closingId, first.menuId))
        putEntry(current.copy(status = "SYNCING"))
        return sent
    }

    @Transaction
    open suspend fun acknowledge(scope: String, closingId: String, operationId: String,
        generation: Long, closingRevision: Long, entryRevision: Long, quantity: Int) {
        val queue = operations(scope, closingId)
        val op = queue.firstOrNull { it.operationId == operationId } ?: return // duplicate local callback
        check(op == queue.first() && op.status == "SENT")
        require(generation == op.generation && quantity == op.quantity && quantity in 0..9999)
        require(entryRevision >= requireNotNull(op.expectedRevision) && closingRevision >= 0)
        val current = requireNotNull(entry(scope, closingId, op.menuId))
        val header = requireNotNull(closing(scope, closingId))
        deleteOperation(op.sequence)
        val later = queue.filter { it.menuId == op.menuId && it.sequence > op.sequence }
        // Historical receipts cannot relabel a newer generation or replace a newer pulled value.
        val stale = header.generation != generation || current.serverRevision > entryRevision || header.state != "DRAFT"
        if (stale) {
            for (item in later) updateOperation(item.copy(status = "CONFLICT", errorCode = "STALE_ACK"))
            putEntry(current.copy(localQuantity = if (later.isEmpty()) current.serverQuantity else current.localQuantity,
                status = if (later.isEmpty()) "SYNCED" else "CONFLICT"))
        } else {
            later.firstOrNull()?.let { updateOperation(it.copy(expectedRevision = entryRevision)) }
            putEntry(current.copy(serverQuantity = quantity, serverRevision = entryRevision,
                localQuantity = if (later.isEmpty()) quantity else current.localQuantity,
                status = if (later.isEmpty()) "SYNCED" else "PENDING"))
        }
        if (header.generation == generation && closingRevision > header.revision) {
            putClosing(header.copy(revision = closingRevision))
        }
    }

    @Transaction
    open suspend fun block(scope: String, closingId: String, operationId: String, status: String, code: String) {
        require(status in setOf("CONFLICT", "FAILED", "AUTH_REQUIRED"))
        val queue = operations(scope, closingId)
        val head = queue.firstOrNull() ?: return
        check(head.operationId == operationId)
        val affected = if (status == "AUTH_REQUIRED") queue else queue.filter { it.menuId == head.menuId }
        for (op in affected) {
            updateOperation(op.copy(status = status, errorCode = code))
            entry(scope, closingId, op.menuId)?.let { putEntry(it.copy(status = status)) }
        }
    }

    // Called only after login to the SAME scope. SENT operations keep their original payload/base.
    @Transaction
    open suspend fun resumeAfterLogin(scope: String, closingId: String) {
        lifecycle(scope, closingId)?.takeIf { it.status == "AUTH_REQUIRED" }?.let {
            updateLifecycle(it.copy(status = if (it.wasSent) "SENT" else "QUEUED", errorCode = null))
        }
        for (op in operations(scope, closingId).filter { it.status == "AUTH_REQUIRED" }) {
            updateOperation(op.copy(status = if (op.expectedRevision == null) "QUEUED" else "SENT", errorCode = null))
            entry(scope, closingId, op.menuId)?.let { putEntry(it.copy(status = "PENDING")) }
        }
    }

    @Transaction
    open suspend fun resolve(scope: String, closingId: String, menuId: String, reapply: Boolean): String? {
        check(lifecycle(scope, closingId) == null) { "LIFECYCLE_PENDING" }
        val current = requireNotNull(entry(scope, closingId, menuId))
        check(current.status in setOf("CONFLICT", "FAILED"))
        val queue = operations(scope, closingId).filter { it.menuId == menuId }
        // Never discard an operation with an ambiguous server outcome.
        check(queue.none { it.status == "SENT" })
        for (op in queue) deleteOperation(op.sequence)
        putEntry(current.copy(localQuantity = current.serverQuantity, status = "SYNCED"))
        return if (reapply) edit(scope, closingId, menuId, current.localQuantity) else null
    }

    private fun lifecycleSourceState(action: String) = when (action) {
        "submit" -> "DRAFT"; "return", "lock" -> "SUBMITTED"; "reopen" -> "LOCKED"
        else -> error("Unsupported lifecycle action")
    }
}
