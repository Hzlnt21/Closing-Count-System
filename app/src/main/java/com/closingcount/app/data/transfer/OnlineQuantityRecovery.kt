package com.closingcount.app.data.transfer

import com.closingcount.app.data.online.*
import com.closingcount.app.data.local.OnlineRecoveryClaimEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.util.UUID

data class QuantityRecoveryRow(val menuId:String,val fileName:String,val serverName:String,val fileBase:Int,
    val fileRevision:Long,val desired:Int,val serverQuantity:Int,val serverRevision:Long,val disposition:String,val sourceGenerations:List<Long>)
data class QuantityRecoveryReview(val id:String,val fingerprint:String,val closingId:String,val date:String,
    val fileGeneration:Long,val serverGeneration:Long,val serverRevision:Long,val serverState:String,val accountId:String,val outletId:String,
    val rows:List<QuantityRecoveryRow>)
data class QuantityRecoveryApplied(val fingerprint:String,val closingId:String,val newOperations:Map<String,String>)

/** Review one closing at a time. Old IDs are references; only new ordinary local operations are created. */
class OnlineQuantityRecovery(private val inbox:OnlinePendingRecoveryInbox,private val repository:OnlineRepository) {
    private data class State(val snapshot:OnlineSnapshot,val statuses:Map<String,Boolean>,val claims:List<OnlineRecoveryClaimEntity>,val rows:List<QuantityRecoveryRow>,val applied:Map<String,String>?=null)
    private data class Ticket(val review:QuantityRecoveryReview,val session:OnlineSession,val model:OnlinePendingBackup,val source:PendingBackupClosing,val state:State)
    private val mutex=Mutex()
    private var pending:Ticket?=null

    suspend fun prepare(fingerprint:String,closingId:String):QuantityRecoveryReview=mutex.withLock {
        pending=null
        val model=withContext(Dispatchers.IO){inbox.read(fingerprint).model}
        val source=model.closings.single{it.id==closingId};require(source.operations.isNotEmpty() && source.lifecycle==null)
        val session=requireNotNull(repository.session());require(session.origin==model.origin && session.identity.id==model.account.id)
        val state=inspect(session,model,source,fingerprint,null)
        val review=QuantityRecoveryReview(UUID.randomUUID().toString(),fingerprint,closingId,source.date,source.generation,
            state.snapshot.header.generation,state.snapshot.header.revision,state.snapshot.header.state,model.account.id,model.outlet.id,state.rows.toList())
        pending=Ticket(review,session,model,source,state);review
    }
    suspend fun discard(id:String)=mutex.withLock{if(pending?.review?.id==id)pending=null}
    suspend fun apply(id:String):QuantityRecoveryApplied=mutex.withLock {
        val ticket=requireNotNull(pending).also{check(it.review.id==id)};pending=null
        val state=inspect(ticket.session,ticket.model,ticket.source,ticket.review.fingerprint,ticket)
        QuantityRecoveryApplied(ticket.review.fingerprint,ticket.source.id,requireNotNull(state.applied))
    }

    private suspend fun inspect(expected:OnlineSession,model:OnlinePendingBackup,source:PendingBackupClosing,fingerprint:String,apply:Ticket?):State =
        repository.withRecoverySession(expected,model.outlet.id){client,fresh,dao->
            val scope=fresh.scope(model.outlet.id)
            val snapshot=client.fetch(fresh,model.outlet.id,source.id)
            val json=JSONObject(snapshot.header.snapshotJson)
            check(json.getString("businessDate")==source.date && snapshot.header.closingId==source.id) { "RECOVERY_DATE_CONFLICT" }
            val ids=source.operations.map{it.operationId}
            dao.requireQuantityReferenceScope(scope,ids)
            val statuses=ids.chunked(100).flatMap{client.operationStatuses(fresh,model.outlet.id,it).entries.map{entry->entry.key to entry.value}}.toMap()
            val claims=dao.recoveryClaims(scope,ids)
            check(claims.all{it.closingId==source.id}) { "RECOVERY_PROVENANCE_CONFLICT" }
            val claimed=claims.map{it.originalOperationId}.toSet()
            val names=json.getJSONObject("catalog").getJSONArray("menus").let{menus->(0 until menus.length()).associate{
                val menu=menus.getJSONObject(it);menu.getString("id") to menu.getString("name")
            }}
            val rows=source.entries.map{entry->
                val server=requireNotNull(snapshot.entries.singleOrNull{it.menuId==entry.menuId}) { "RECOVERY_MENU_MISSING" }
                val queue=source.operations.filter{it.menuId==entry.menuId}
                var missing=false
                for(op in queue){val handled=statuses.getValue(op.operationId)||op.operationId in claimed
                    check(!handled||!missing) { "RECOVERY_OPERATION_ORDER" };if(!handled)missing=true}
                val disposition=when {
                    queue.all{statuses.getValue(it.operationId)}->"ALREADY_RECORDED"
                    queue.all{statuses.getValue(it.operationId)||it.operationId in claimed}->"ALREADY_RECOVERED_LOCALLY"
                    else->"PROPOSAL"
                }
                QuantityRecoveryRow(entry.menuId,entry.name,names.getValue(entry.menuId),entry.baseQuantity,entry.baseRevision,
                    entry.localQuantity,server.serverQuantity,server.serverRevision,disposition,queue.map{it.generation}.distinct())
            }
            if(rows.any{it.disposition=="PROPOSAL"}) {
                check(snapshot.header.state=="DRAFT") { "RECOVERY_NOT_WRITABLE" }
                dao.requireRecoveryTargetFree(scope,source.id,source.date)
            }
            var state=State(snapshot,statuses,claims,rows)
            if(apply!=null) {
                val before=apply.state
                check(before.snapshot.header.generation==snapshot.header.generation && before.snapshot.header.revision==snapshot.header.revision &&
                    before.rows==rows && before.statuses==statuses && before.claims==claims) { "RECOVERY_REVIEW_CHANGED" }
                val desired=rows.filter{it.disposition=="PROPOSAL"}.associate{it.menuId to it.desired}
                val operations=if(desired.isEmpty())emptyMap() else dao.recoverQuantities(snapshot.header,snapshot.entries,source.date,desired,
                    source.operations.associate{it.operationId to it.menuId},claimed,fingerprint)
                state=state.copy(applied=operations)
            }
            state
        }
}
