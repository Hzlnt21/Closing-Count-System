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
    @Query("SELECT COUNT(*) FROM online_recovered_admin WHERE scope=:scope AND originalOperationId IN (:ids)")
    protected abstract suspend fun recoveredAdminReferenceCount(scope:String,ids:List<String>):Int
    @Transaction
    open suspend fun requireQuantityReferenceScope(scope:String,ids:List<String>) {
        for(batch in ids.chunked(100))check(recoveredAdminReferenceCount(scope,batch)==0) { "RECOVERY_PROVENANCE_CONFLICT" }
    }
    @Query("SELECT * FROM online_recovered_admin WHERE scope=:scope AND `group`=:group AND active=1")
    abstract suspend fun activeRecoveredAdmin(scope:String,group:String):List<OnlineRecoveredAdminEntity>
    @Query("SELECT * FROM online_recovered_admin WHERE scope=:scope AND originalOperationId=:originalId")
    abstract suspend fun recoveredAdminReference(scope:String,originalId:String):OnlineRecoveredAdminEntity?
    @Query("SELECT * FROM online_recovered_admin WHERE operationId=:id")
    abstract suspend fun recoveredAdminOperation(id:String):OnlineRecoveredAdminEntity?
    @Query("SELECT * FROM online_recovered_admin WHERE substr(scope,1,length(:prefix))=:prefix AND active=1")
    abstract suspend fun recoveredAdminForAccount(prefix:String):List<OnlineRecoveredAdminEntity>
    @Insert(onConflict=OnConflictStrategy.ABORT)
    protected abstract suspend fun insertRecoveredAdmin(value:OnlineRecoveredAdminEntity)
    @Update
    protected abstract suspend fun putRecoveredAdmin(value:OnlineRecoveredAdminEntity)

    @Transaction
    open suspend fun attachRecoveredAdmin(value:OnlineRecoveredAdminEntity) {
        require(Regex("[0-9a-f]{64}").matches(value.fingerprint))
        com.closingcount.app.data.online.OnlineApi.uuid(value.originalOperationId)
        com.closingcount.app.data.online.OnlineApi.uuid(value.operationId)
        require(when(value.group){"SETTINGS"->value.kind=="SETTINGS";"TEAM"->value.kind in setOf("TEAM_CREATE","TEAM_UPDATE");"CATALOG"->value.kind=="CATALOG";else->false})
        check(value.active && !value.wasSent && value.status in setOf("QUEUED","EDITING"))
        check(recoveredAdminReference(value.scope,value.originalOperationId)==null &&
            recoveryClaims(value.scope,listOf(value.originalOperationId)).isEmpty()) { "RECOVERY_ALREADY_HANDLED" }
        check(activeRecoveredAdmin(value.scope,value.group).isEmpty()) { "RECOVERY_LOCAL_PENDING" }
        insertRecoveredAdmin(value)
    }
    @Transaction
    open suspend fun saveRecoveredAdmin(value:OnlineRecoveredAdminEntity) {
        val old=requireNotNull(recoveredAdminOperation(value.operationId));check(old.active)
        check(old.copy(payloadJson=value.payloadJson,status=value.status,wasSent=value.wasSent,error=value.error)==value)
        if(old.group=="CATALOG"&&old.status=="EDITING")check(value.status in setOf("EDITING","QUEUED")&&!value.wasSent)
        else check(old.payloadJson==value.payloadJson&&value.status!="EDITING") { "Recovered action payload is frozen" }
        check(!old.wasSent||value.wasSent);putRecoveredAdmin(value)
    }
    @Transaction
    open suspend fun archiveRecoveredAdmin(scope:String,id:String,confirmed:Boolean) {
        val old=requireNotNull(recoveredAdminOperation(id));check(old.scope==scope)
        if(!old.active)return
        check(confirmed||old.status in setOf("CONFLICT","FAILED")||(old.group=="CATALOG"&&old.status=="EDITING"))
        putRecoveredAdmin(old.copy(active=false))
    }

    @Query("SELECT * FROM online_recovery_claims WHERE scope=:scope AND originalOperationId IN (:ids) ORDER BY originalOperationId")
    protected abstract suspend fun claimsBatch(scope:String,ids:List<String>):List<OnlineRecoveryClaimEntity>

    @Insert(onConflict=OnConflictStrategy.ABORT)
    protected abstract suspend fun insertClaims(values:List<OnlineRecoveryClaimEntity>)

    @Transaction
    open suspend fun recoveryClaims(scope:String,ids:List<String>):List<OnlineRecoveryClaimEntity> =
        ids.chunked(100).flatMap{claimsBatch(scope,it)}

    @Transaction
    open suspend fun requireRecoveryTargetFree(scope:String,closingId:String,date:String) {
        check(operations(scope,closingId).isEmpty() && lifecycle(scope,closingId)==null &&
            entries(scope,closingId).all{it.status=="SYNCED"}) { "RECOVERY_LOCAL_PENDING" }
        for(id in closingIds(scope)) {
            val row=requireNotNull(closing(scope,id))
            val cachedDate=org.json.JSONObject(row.snapshotJson).getString("businessDate")
            check(cachedDate!=date || id==closingId) { "RECOVERY_DATE_CONFLICT" }
            if(id==closingId)check(cachedDate==date) { "RECOVERY_DATE_CONFLICT" }
        }
    }

    /** Provenance and normal new operations commit together, without network transport. */
    @Transaction
    open suspend fun recoverQuantities(remote:OnlineClosingEntity,remoteEntries:List<OnlineEntryEntity>,date:String,
        quantities:Map<String,Int>,references:Map<String,String>,knownClaims:Set<String>,fingerprint:String):Map<String,String> {
        require(Regex("[0-9a-f]{64}").matches(fingerprint) && references.isNotEmpty())
        requireQuantityReferenceScope(remote.scope,references.keys.toList())
        require(quantities.isNotEmpty() && quantities.keys.all{key->remoteEntries.any{it.menuId==key}})
        require(remote.state=="DRAFT" && remoteEntries.all{it.generation==remote.generation})
        requireRecoveryTargetFree(remote.scope,remote.closingId,date)
        val claims=recoveryClaims(remote.scope,references.keys.toList())
        check(claims.map{it.originalOperationId}.toSet()==knownClaims && claims.all{it.closingId==remote.closingId}) { "RECOVERY_REVIEW_CHANGED" }
        val old=closing(remote.scope,remote.closingId)
        check(old==null || old.revision<=remote.revision) { "RECOVERY_REVIEW_CHANGED" }
        pull(remote,remoteEntries)
        val generated=quantities.mapValues{(menu,quantity)->edit(remote.scope,remote.closingId,menu,quantity)}
        val now=java.time.Instant.now().toString()
        insertClaims(references.filterKeys{it !in knownClaims}.map{(original,menu)->
            OnlineRecoveryClaimEntity(remote.scope,original,fingerprint,remote.closingId,generated[menu],now)
        })
        return generated
    }

    @Transaction
    open suspend fun recoverLifecycle(remote:OnlineClosingEntity,remoteEntries:List<OnlineEntryEntity>,date:String,
        originalId:String,fingerprint:String,action:String,reason:String,role:String):String {
        require(Regex("[0-9a-f]{64}").matches(fingerprint))
        check(recoveryClaims(remote.scope,listOf(originalId)).isEmpty()&&recoveredAdminReference(remote.scope,originalId)==null) { "RECOVERY_ALREADY_HANDLED" }
        requireRecoveryTargetFree(remote.scope,remote.closingId,date)
        val old=closing(remote.scope,remote.closingId)
        check(old==null||old.revision<=remote.revision) { "RECOVERY_REVIEW_CHANGED" }
        pull(remote,remoteEntries)
        val id=prepareLifecycle(remote.scope,remote.closingId,action,reason,remote.generation,remote.revision,role)
        insertClaims(listOf(OnlineRecoveryClaimEntity(remote.scope,originalId,fingerprint,remote.closingId,id,java.time.Instant.now().toString())))
        return id
    }

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

    @Query("SELECT c.closingId FROM online_closings c WHERE c.scope=:scope AND (EXISTS (SELECT 1 FROM online_operations o WHERE o.scope=c.scope AND o.closingId=c.closingId) OR EXISTS (SELECT 1 FROM online_lifecycle_operations l WHERE l.scope=c.scope AND l.closingId=c.closingId) OR EXISTS (SELECT 1 FROM online_entries e WHERE e.scope=c.scope AND e.closingId=c.closingId AND (e.status!='SYNCED' OR e.localQuantity!=e.serverQuantity))) ORDER BY c.closingId LIMIT 1001")
    protected abstract suspend fun closingIdsForBackup(scope: String): List<String>

    @Query("SELECT COUNT(*) FROM online_operations WHERE scope=:scope")
    protected abstract suspend fun quantityCountForBackup(scope: String): Long

    @Query("SELECT COUNT(*) FROM online_lifecycle_operations WHERE scope=:scope")
    protected abstract suspend fun lifecycleCountForBackup(scope: String): Long

    @Query("SELECT COUNT(*) FROM online_entries WHERE scope=:scope AND (status!='SYNCED' OR localQuantity!=serverQuantity)")
    protected abstract suspend fun pendingEntryCountForBackup(scope: String): Long

    /** One local transaction; no network call, acknowledgement or status transition. */
    @Transaction
    open suspend fun capturePendingForBackup(scope: String): List<OnlinePendingClosingCapture> {
        check(quantityCountForBackup(scope)<=10000 && lifecycleCountForBackup(scope)<=1000 && pendingEntryCountForBackup(scope)<=10000)
        val ids=closingIdsForBackup(scope);check(ids.size<=1000)
        val captured = ids.mapNotNull { id ->
            val header=requireNotNull(closing(scope,id))
            val queue = operations(scope, header.closingId)
            val action = lifecycle(scope, header.closingId)
            val rows = entries(scope, header.closingId)
            check(rows.size<=500)
            val pendingRows = rows.filter { it.status != "SYNCED" || it.localQuantity != it.serverQuantity }
            if (queue.isEmpty() && action == null && pendingRows.isEmpty()) null
            else {
                // Do not silently omit inconsistent local work from a successful backup.
                check(queue.isEmpty() || action == null)
                check(queue.map { it.menuId }.toSet() == pendingRows.map { it.menuId }.toSet())
                check(queue.map { it.operationId }.distinct().size == queue.size)
                check(rows.all { it.scope == scope && it.closingId == header.closingId && it.generation == header.generation })
                OnlinePendingClosingCapture(OnlinePendingClosingHeader.from(header,pendingRows.map{it.menuId}.toSet()), pendingRows, queue, action)
            }
        }
        check(captured.sumOf { it.operations.size.toLong() } == quantityCountForBackup(scope))
        check(captured.count { it.lifecycle != null }.toLong() == lifecycleCountForBackup(scope))
        check(captured.sumOf { it.entries.size.toLong() } == pendingEntryCountForBackup(scope))
        return captured
    }

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
