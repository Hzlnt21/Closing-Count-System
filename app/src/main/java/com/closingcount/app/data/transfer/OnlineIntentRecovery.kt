package com.closingcount.app.data.transfer

import com.closingcount.app.data.online.*
import com.closingcount.app.data.local.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.time.Instant
import java.util.UUID

data class IntentRecoveryReview(val id:String,val fingerprint:String,val originalOperationId:String,val kind:String,
    val outletId:String,val closingId:String?,val sourcePayloadJson:String,val sourceRevision:Long?,val sourceGeneration:Long?,
    val currentPayloadJson:String?,val currentRevision:Long?,val currentGeneration:Long?,val currentState:String?,
    val disposition:String,val passwordRequired:Boolean)
data class IntentRecoveryApplied(val fingerprint:String,val originalOperationId:String,val kind:String,val newOperationId:String?)

/** Only explicit review attaches a new normal local action. No server mutation/password storage. */
class OnlineIntentRecovery(private val inbox:OnlinePendingRecoveryInbox,private val repository:OnlineRepository) {
    private data class Target(val admin:PendingBackupAdmin?,val closing:PendingBackupClosing?) {
        val lifecycle get()=closing?.lifecycle
        val originalId get()=admin?.operationId?:requireNotNull(lifecycle).operationId
        val kind get()=admin?.kind?:"LIFECYCLE"
        val payload get()=admin?.payloadJson?:JSONObject().put("action",requireNotNull(lifecycle).action).put("reason",requireNotNull(lifecycle).reason).toString()
    }
    private data class State(val disposition:String,val role:String,val revision:Long?=null,val generation:Long?=null,
        val state:String?=null,val currentJson:String?=null,val snapshot:OnlineSnapshot?=null,val newId:String?=null)
    private data class Ticket(val review:IntentRecoveryReview,val session:OnlineSession,val model:OnlinePendingBackup,val target:Target,val state:State)
    private val mutex=Mutex();private var pending:Ticket?=null

    suspend fun prepareAdmin(fingerprint:String,originalId:String):IntentRecoveryReview=prepare(fingerprint,originalId,null)
    suspend fun prepareLifecycle(fingerprint:String,closingId:String):IntentRecoveryReview=prepare(fingerprint,null,closingId)
    private suspend fun prepare(fingerprint:String,originalId:String?,closingId:String?):IntentRecoveryReview=mutex.withLock {
        pending=null
        val model=withContext(Dispatchers.IO){inbox.read(fingerprint).model}
        val target=if(originalId!=null)Target(model.admin.single{it.operationId==originalId},null)
            else Target(null,model.closings.single{it.id==closingId}.also{require(it.lifecycle!=null&&it.operations.isEmpty())})
        val session=requireNotNull(repository.session());require(session.origin==model.origin&&session.identity.id==model.account.id)
        val state=inspect(session,model,target,fingerprint,null)
        val review=IntentRecoveryReview(UUID.randomUUID().toString(),fingerprint,target.originalId,target.kind,model.outlet.id,target.closing?.id,
            target.payload,target.admin?.expectedRevision?:target.lifecycle?.expectedRevision,target.lifecycle?.generation,
            state.currentJson,state.revision,state.generation,state.state,state.disposition,target.kind=="TEAM_CREATE")
        pending=Ticket(review,session,model,target,state);review
    }
    suspend fun discard(id:String)=mutex.withLock{if(pending?.review?.id==id)pending=null}
    suspend fun apply(id:String):IntentRecoveryApplied=mutex.withLock {
        val ticket=requireNotNull(pending).also{check(it.review.id==id)};pending=null
        val state=inspect(ticket.session,ticket.model,ticket.target,ticket.review.fingerprint,ticket)
        IntentRecoveryApplied(ticket.review.fingerprint,ticket.target.originalId,ticket.target.kind,state.newId)
    }
    private suspend fun inspect(expected:OnlineSession,model:OnlinePendingBackup,target:Target,fingerprint:String,apply:Ticket?):State =
        repository.withAdminRecoverySession(expected,model.outlet.id){client,fresh,dao,storage->
            val scope=fresh.scope(model.outlet.id);val role=fresh.identity.outlets.single{it.id==model.outlet.id}.role
            val recorded=client.operationStatuses(fresh,model.outlet.id,listOf(target.originalId)).getValue(target.originalId)
            val adminClaim=dao.recoveredAdminReference(scope,target.originalId)
            val claims=dao.recoveryClaims(scope,listOf(target.originalId))
            if(target.admin!=null)check(claims.isEmpty()&&(adminClaim==null||adminClaim.kind==target.kind)) { "RECOVERY_PROVENANCE_CONFLICT" }
            else check(adminClaim==null&&claims.all{it.closingId==target.closing!!.id}) { "RECOVERY_PROVENANCE_CONFLICT" }
            var current=when {
                recorded->State("ALREADY_RECORDED",role)
                adminClaim!=null||claims.isNotEmpty()->State("ALREADY_RECOVERED_LOCALLY",role)
                target.admin!=null&&role!="ADMIN"->State("BLOCKED_ROLE",role)
                target.admin!=null->adminState(client,fresh,target.admin,model.outlet.id,role,storage)
                else->lifecycleState(client,fresh,requireNotNull(target.closing),model.outlet.id,role,dao)
            }
            if(apply!=null) {
                check(current==apply.state) { "RECOVERY_REVIEW_CHANGED" }
                check(current.disposition in setOf("READY","ALREADY_RECORDED","ALREADY_RECOVERED_LOCALLY")) { "RECOVERY_NOT_READY" }
                if(current.disposition=="READY") {
                    val id=if(target.admin!=null) {
                        val source=target.admin;val newId=UUID.randomUUID().toString()
                        storage.attach(OnlineRecoveredAdminEntity(scope,source.operationId,fingerprint,OnlineRecoveredAdminStore.group(source.kind),source.kind,newId,
                            current.revision,source.payloadJson,if(source.kind=="CATALOG")"EDITING" else "QUEUED",false,null,true,Instant.now().toString()))
                        newId
                    } else {
                        val snapshot=requireNotNull(current.snapshot);val closing=requireNotNull(target.closing);val intent=requireNotNull(closing.lifecycle)
                        dao.recoverLifecycle(snapshot.header,snapshot.entries,closing.date,intent.operationId,fingerprint,intent.action,intent.reason,role)
                    }
                    current=current.copy(newId=id)
                }
            }
            current
        }
    private suspend fun adminState(client:OnlineApi,session:OnlineSession,source:PendingBackupAdmin,outlet:String,role:String,storage:OnlineRecoveredAdminStore):State {
        val group=OnlineRecoveredAdminStore.group(source.kind)
        storage.requireFree(session.scope(outlet),group)
        return when(source.kind) {
            "SETTINGS"->{val s=client.outletSettings(session,outlet);check(s.role=="ADMIN")
                State("READY",role,s.revision,currentJson=JSONObject().put("name",s.name).put("timezone",s.timezone).put("cutoffMinutes",s.cutoffMinutes).toString())}
            "CATALOG"->{check(client.outletSettings(session,outlet).role=="ADMIN");val c=client.catalog(session,outlet)
                State("READY",role,c.revision,currentJson=c.catalog.json().toString())}
            "TEAM_UPDATE"->{val p=JSONObject(source.payloadJson);val m=client.member(session,outlet,p.getString("accountId"))
                State("READY",role,m.revision,currentJson=JSONObject().put("accountId",m.id).put("username",m.username).put("displayName",m.name).put("role",m.role).put("active",m.active).put("accountActive",m.accountActive).toString())}
            "TEAM_CREATE"->{
                val username=JSONObject(source.payloadJson).getString("username");var after:String?=null;var complete=false;var found=false
                for(page in 0 until 100){val roster=client.team(session,outlet,after,100)
                    found=found||roster.items.any{it.username==username};after=roster.nextAfter
                    if(found||after==null){complete=true;break}}
                check(complete) { "RECOVERY_ROSTER_LIMIT" }
                State(if(found)"BLOCKED_USERNAME" else "READY",role,currentJson=JSONObject().put("username",username).put("existsInOutlet",found).toString())
            }
            else->error("Unsupported recovery intent")
        }
    }
    private suspend fun lifecycleState(client:OnlineApi,session:OnlineSession,closing:PendingBackupClosing,outlet:String,role:String,dao:OnlineSyncDao):State {
        val source=requireNotNull(closing.lifecycle);val snapshot=client.fetch(session,outlet,closing.id)
        check(JSONObject(snapshot.header.snapshotJson).getString("businessDate")==closing.date) { "RECOVERY_DATE_CONFLICT" }
        val needed=when(source.action){"submit"->"DRAFT";"return","lock"->"SUBMITTED";"reopen"->"LOCKED";else->error("Unsupported action")}
        val disposition=when {source.action!="submit"&&role=="EMPLOYEE"->"BLOCKED_ROLE";snapshot.header.state!=needed->"BLOCKED_STATE";else->"READY"}
        if(disposition=="READY")dao.requireRecoveryTargetFree(session.scope(outlet),closing.id,closing.date)
        return State(disposition,role,snapshot.header.revision,snapshot.header.generation,snapshot.header.state,
            JSONObject().put("state",snapshot.header.state).put("generation",snapshot.header.generation).put("revision",snapshot.header.revision).toString(),snapshot)
    }
}
