package com.closingcount.app.data.online

import com.closingcount.app.data.local.OnlineSyncDao
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.IOException

class PendingOnlineDraftsException : IllegalStateException("Konfirmasi diperlukan: akun ini memiliki perubahan yang belum terkirim.")

/** Application-owned session and engines. No automatic network call at application startup. */
class OnlineRepository(private val dao: OnlineSyncDao, private val store: OnlineSessionStore,
    private val settingsStore: OnlineSettingsStore? = null, private val teamStore:OnlineTeamStore?=null, private val catalogStore:OnlineCatalogStore?=null) {
    private val recovered=OnlineRecoveredAdminStore(dao,settingsStore,teamStore,catalogStore)
    @Volatile private var active: OnlineSession? = null
    private val sessionMutex = Mutex()
    private val engines = mutableMapOf<String, OnlineSyncEngine>()
    fun session(): OnlineSession? = active
    private fun api(session: OnlineSession) = OnlineApi(OnlineEndpoint(session.origin, allowLocalQa = true))
    private fun prefix(session: OnlineSession) = "${session.origin}|${session.identity.id}|"
    private fun engine(session: OnlineSession): OnlineSyncEngine = synchronized(engines) {
        engines.getOrPut(session.origin) { OnlineSyncEngine(dao, api(session)) { active } }
    }

    suspend fun initialize() = sessionMutex.withLock {
        active = withContext(Dispatchers.IO) { store.load() }
    }

    suspend fun refreshAccount(): OnlineIdentity = sessionMutex.withLock {
        val session = requireNotNull(active); val fresh = api(session).refreshIdentity(session)
        withContext(Dispatchers.IO) { store.save(fresh) }; active = fresh
        fresh.identity
    }

    /** Prepare login, then allow UI to confirm switching accounts without replacing the old session. */
    suspend fun login(origin: String, username: String, password: String): OnlineSession =
        OnlineApi(OnlineEndpoint(origin, allowLocalQa = true)).login(username, password)

    suspend fun activate(candidate: OnlineSession, confirmPendingSwitch: Boolean = false) = sessionMutex.withLock {
        val old = active
        if (old != null && prefix(old) != prefix(candidate) && pending(old) > 0 && !confirmPendingSwitch) {
            throw PendingOnlineDraftsException()
        }
        withContext(Dispatchers.IO) { store.save(candidate) }
        active = candidate
        for (outlet in candidate.identity.outlets) {
            val scope = candidate.scope(outlet.id)
            for (id in dao.closingIds(scope)) dao.resumeAfterLogin(scope, id)
            withContext(Dispatchers.IO){recovered.catalog(scope)?.takeIf{it.status=="AUTH_REQUIRED"}?.let{
                recovered.save(it.copy(status=if(it.wasSent)"SENT" else "QUEUED",error=null))
            }}
            withContext(Dispatchers.IO) { recovered.settings(scope)?.takeIf { it.status == "AUTH_REQUIRED" }?.let {
                recovered.save(it.copy(status=if(it.wasSent) "SENT" else "QUEUED",error=null))
            } }
        }
        withContext(Dispatchers.IO){recovered.teamForAccount(prefix(candidate)).filter{it.status=="AUTH_REQUIRED"}.forEach{
            recovered.save(it.copy(status=if(it.wasSent)"SENT" else "QUEUED",error=null))
        }}
    }

    private suspend fun pending(session: OnlineSession) = dao.pendingForAccount(prefix(session)) +
        withContext(Dispatchers.IO) { recovered.count(prefix(session)) }
    suspend fun pendingCount(): Int = active?.let { pending(it) } ?: 0

    /** Local backup works without transport, including when the cached token has expired. */
    suspend fun capturePendingBackup(expected:OnlineSession,outletId:String,deviceId:String):com.closingcount.app.data.transfer.OnlinePendingBackup = sessionMutex.withLock {
        check(active===expected) { "Backup session changed" }
        val scope=expected.scope(outletId)
        val outlet=expected.identity.outlets.first{it.id==outletId}
        withContext(Dispatchers.IO) {
            val admin=recovered.capture(scope)
            val closings=dao.capturePendingForBackup(scope).map { capture ->
                val h=capture.header
                com.closingcount.app.data.transfer.PendingBackupClosing(h.closingId,h.date,h.generation,h.revision,h.state,
                    capture.entries.map{e->com.closingcount.app.data.transfer.PendingBackupEntry(e.menuId,h.menuNames.getValue(e.menuId),e.serverQuantity,e.serverRevision,e.localQuantity,e.status)},
                    capture.operations.map{op->com.closingcount.app.data.transfer.PendingBackupQuantity(op.operationId,op.menuId,op.generation,op.expectedRevision,op.quantity,op.status)},
                    capture.lifecycle?.let{op->com.closingcount.app.data.transfer.PendingBackupLifecycle(op.operationId,op.action,op.generation,op.expectedRevision,op.reason,op.status,op.wasSent)})
            }
            com.closingcount.app.data.transfer.OnlinePendingBackup(java.util.UUID.randomUUID().toString(),java.time.Instant.now().toString(),deviceId,
                expected.origin,com.closingcount.app.data.transfer.PendingBackupAccount(expected.identity.id,expected.identity.username,expected.identity.displayName),
                com.closingcount.app.data.transfer.PendingBackupOutlet(outletId,outlet.name),closings,admin)
        }
    }

    suspend fun withPendingBackupSession(expected:OnlineSession,write:suspend()->Unit)=sessionMutex.withLock {
        check(active===expected) { "Backup session changed" }
        withContext(Dispatchers.IO){write()}
    }
    internal suspend fun <T> withRecoverySession(expected:OnlineSession,outletId:String,block:suspend(OnlineApi,OnlineSession,OnlineSyncDao)->T):T=sessionMutex.withLock {
        check(active===expected) { "RECOVERY_SESSION_CHANGED" }
        engine(expected).withRecovery {
            val client=api(expected);val fresh=client.refreshIdentity(expected)
            check(fresh.identity.id==expected.identity.id && fresh.origin==expected.origin)
            fresh.scope(outletId)
            val result=block(client,fresh,dao)
            check(active===expected) { "RECOVERY_SESSION_CHANGED" };result
        }
    }
    internal suspend fun <T> withAdminRecoverySession(expected:OnlineSession,outletId:String,
        block:suspend(OnlineApi,OnlineSession,OnlineSyncDao,OnlineRecoveredAdminStore)->T):T =
        withRecoverySession(expected,outletId){client,fresh,storage->withContext(Dispatchers.IO){block(client,fresh,storage,recovered)}}
    suspend fun team(outlet:String,after:String?=null):OnlineTeamPage{
        val session=requireNotNull(active);val value=api(session).team(session,outlet,after);check(active===session);return value
    }
    suspend fun pendingTeam(outlet:String):OnlineTeamOperation?{
        val session=requireNotNull(active);return withContext(Dispatchers.IO){recovered.team(session.scope(outlet))}
    }
    suspend fun prepareTeamCreate(outlet:String,username:String,name:String,role:String)=sessionMutex.withLock{
        val session=requireNotNull(active);requireNotNull(teamStore)
        check(session.identity.outlets.first{it.id==outlet}.role=="ADMIN")
        val payload=org.json.JSONObject().put("username",username.trim().lowercase(java.util.Locale.ROOT)).put("displayName",name.trim()).put("role",role)
        val op=OnlineTeamOperation(session.scope(outlet),java.util.UUID.randomUUID().toString(),"CREATE_ACCOUNT",payload.toString())
        withContext(Dispatchers.IO){check(recovered.team(op.scope)==null);recovered.save(op)}
    }
    suspend fun prepareTeamEdit(outlet:String,reviewed:OnlineTeamMember,role:String,enabled:Boolean)=sessionMutex.withLock{
        val session=requireNotNull(active);requireNotNull(teamStore)
        check(session.identity.outlets.first{it.id==outlet}.role=="ADMIN")
        val payload=org.json.JSONObject().put("accountId",reviewed.id).put("expectedRevision",reviewed.revision).put("role",role).put("active",enabled)
        val op=OnlineTeamOperation(session.scope(outlet),java.util.UUID.randomUUID().toString(),"UPDATE_MEMBERSHIP",payload.toString())
        withContext(Dispatchers.IO){check(recovered.team(op.scope)==null);recovered.save(op)}
    }
    suspend fun syncTeam(outlet:String,password:String?=null):OnlineTeamReceipt=sessionMutex.withLock{
        val session=requireNotNull(active);requireNotNull(teamStore);val scope=session.scope(outlet)
        var op=withContext(Dispatchers.IO){requireNotNull(recovered.team(scope))};check(op.status in setOf("QUEUED","SENT"))
        try{
            if(op.wasSent){
                try{val receipt=api(session).teamReceipt(session,op);withContext(Dispatchers.IO){recovered.remove(scope,"TEAM",op.operationId,true)};return@withLock receipt}
                catch(e:OnlineApiException){if(e.status!=404||e.code!="TEAM_OPERATION_NOT_FOUND")throw e}
            }
            if(op.kind=="CREATE_ACCOUNT"&&password==null)throw TeamPasswordRequiredException()
            if(op.kind=="UPDATE_MEMBERSHIP"&&!op.wasSent){
                val p=org.json.JSONObject(op.payload);val fresh=api(session).member(session,outlet,p.getString("accountId"))
                if(fresh.revision!=p.getLong("expectedRevision")){
                    op=op.copy(status="CONFLICT",error="MEMBERSHIP_CONFLICT");withContext(Dispatchers.IO){recovered.save(op)};throw OnlineConflictChangedException()
                }
            }
            op=op.copy(status="SENT",wasSent=true,error=null);withContext(Dispatchers.IO){recovered.save(op)}
            val receipt=api(session).sendTeam(session,op,password);withContext(Dispatchers.IO){recovered.remove(scope,"TEAM",op.operationId,true)};receipt
        }catch(e:OnlineApiException){
            val status=when{e.status in setOf(401,403)->"AUTH_REQUIRED";e.status>=500||e.status in setOf(408,429)||e.code=="NOT_FOUND"->null;e.status==409->"CONFLICT";else->"FAILED"}
            if(status!=null)withContext(Dispatchers.IO){recovered.save(op.copy(status=status,error=e.code))};throw e
        }
    }
    suspend fun dismissTeam(outlet:String)=sessionMutex.withLock{
        val session=requireNotNull(active);requireNotNull(teamStore)
        withContext(Dispatchers.IO){recovered.team(session.scope(outlet))?.let{recovered.remove(it.scope,"TEAM",it.operationId)}}
    }
    suspend fun pendingOwnTeamAccessCount():Int{
        val session=active?:return 0
        return withContext(Dispatchers.IO){recovered.teamForAccount(prefix(session)).count{it.wasSent&&it.status in setOf("SENT","AUTH_REQUIRED")&&it.ownAccessLoss(session.identity.id)}}
    }
    suspend fun reconcileOwnTeamAccess():Int=sessionMutex.withLock{
        val session=requireNotNull(active);requireNotNull(teamStore)
        val ops=withContext(Dispatchers.IO){recovered.teamForAccount(prefix(session)).filter{it.wasSent&&it.status in setOf("SENT","AUTH_REQUIRED")&&it.ownAccessLoss(session.identity.id)}}
        var confirmed=0
        for(op in ops){
            val receipt=api(session).teamReceipt(session,op);check(receipt.member.id==session.identity.id)
            withContext(Dispatchers.IO){recovered.remove(op.scope,"TEAM",op.operationId,true)};confirmed++
        };confirmed
    }

    suspend fun catalog(outlet:String):OnlineCatalogVersion{
        val session=requireNotNull(active);val result=api(session).catalog(session,outlet);check(active===session);return result
    }
    suspend fun pendingCatalog(outlet:String):OnlineCatalogOperation?{
        val session=requireNotNull(active);return withContext(Dispatchers.IO){recovered.catalog(session.scope(outlet))}
    }
    suspend fun prepareCatalog(outlet:String,reviewedRevision:Long,catalog:OnlineCatalog)=sessionMutex.withLock{
        val session=requireNotNull(active);requireNotNull(catalogStore)
        val settings=api(session).outletSettings(session,outlet);check(settings.role=="ADMIN")
        val fresh=api(session).catalog(session,outlet)
        if(fresh.revision!=reviewedRevision)throw OnlineConflictChangedException()
        val scope=session.scope(outlet);val existing=withContext(Dispatchers.IO){recovered.catalog(scope)}
        val json=catalog.json().toString()
        val op=if(existing==null)OnlineCatalogOperation(scope,java.util.UUID.randomUUID().toString(),reviewedRevision,json)
            else{check(existing.status=="EDITING"&&existing.expectedRevision==reviewedRevision&&existing.catalogJson==json);existing.copy(status="QUEUED")}
        withContext(Dispatchers.IO){recovered.save(op)}
    }
    suspend fun startCatalogDraft(outlet:String,reviewed:OnlineCatalogVersion)=sessionMutex.withLock{
        val session=requireNotNull(active);requireNotNull(catalogStore)
        check(api(session).outletSettings(session,outlet).role=="ADMIN")
        if(api(session).catalog(session,outlet).revision!=reviewed.revision)throw OnlineConflictChangedException()
        val op=OnlineCatalogOperation(session.scope(outlet),java.util.UUID.randomUUID().toString(),reviewed.revision,reviewed.catalog.json().toString(),"EDITING")
        withContext(Dispatchers.IO){check(recovered.catalog(op.scope)==null);recovered.save(op)}
    }
    suspend fun editCatalogDraft(outlet:String,catalog:OnlineCatalog)=sessionMutex.withLock{
        val session=requireNotNull(active);requireNotNull(catalogStore)
        withContext(Dispatchers.IO){val op=requireNotNull(recovered.catalog(session.scope(outlet)));check(op.status=="EDITING");recovered.save(op.copy(catalogJson=catalog.json().toString()))}
    }

    suspend fun syncCatalog(outlet:String):OnlineCatalogVersion=sessionMutex.withLock{
        val session=requireNotNull(active);requireNotNull(catalogStore);val scope=session.scope(outlet)
        var op=withContext(Dispatchers.IO){requireNotNull(recovered.catalog(scope))};check(op.status in setOf("QUEUED","SENT"))
        var acknowledged=false
        try{
            if(!op.wasSent){
                val fresh=api(session).catalog(session,outlet)
                if(fresh.revision!=op.expectedRevision){op=op.copy(status="CONFLICT",error="CATALOG_CONFLICT");withContext(Dispatchers.IO){recovered.save(op)};throw OnlineConflictChangedException()}
            }
            op=op.copy(status="SENT",wasSent=true,error=null);withContext(Dispatchers.IO){recovered.save(op)}
            val receipt=api(session).publishCatalog(session,op);acknowledged=true
            val fresh=api(session).catalog(session,outlet);check(fresh.revision>=receipt.revision)
            withContext(Dispatchers.IO){recovered.remove(scope,"CATALOG",op.operationId,true)};fresh
        }catch(e:OnlineApiException){
            val status=when{e.status in setOf(401,403)->"AUTH_REQUIRED"
                acknowledged||e.status>=500||e.status in setOf(408,429)||e.code=="NOT_FOUND"->null
                e.status==409->"CONFLICT"
                else->"FAILED"}
            if(status!=null)withContext(Dispatchers.IO){recovered.save(op.copy(status=status,error=e.code))};throw e
        }
    }
    suspend fun dismissCatalog(outlet:String)=sessionMutex.withLock{
        val session=requireNotNull(active);requireNotNull(catalogStore)
        withContext(Dispatchers.IO){recovered.catalog(session.scope(outlet))?.let{recovered.remove(it.scope,"CATALOG",it.operationId)}}
    }

    suspend fun outletSettings(outletId: String): OnlineOutletSettings {
        val session = requireNotNull(active); val value = api(session).outletSettings(session,outletId)
        check(active === session); return value
    }
    suspend fun pendingSettings(outletId: String): OnlineSettingsOperation? {
        val session = requireNotNull(active)
        return withContext(Dispatchers.IO) { recovered.settings(session.scope(outletId)) }
    }
    suspend fun prepareSettings(outletId: String, name: String, timezone: String, cutoff: Int, reviewedRevision: Long) = sessionMutex.withLock {
        val session = requireNotNull(active); requireNotNull(settingsStore)
        val fresh = api(session).outletSettings(session,outletId)
        check(fresh.role == "ADMIN"); if (fresh.revision != reviewedRevision) throw OnlineConflictChangedException()
        val op = OnlineSettingsOperation(session.scope(outletId),java.util.UUID.randomUUID().toString(),reviewedRevision,name.trim(),timezone,cutoff)
        withContext(Dispatchers.IO) { check(recovered.settings(op.scope) == null);recovered.save(op) }
    }
    suspend fun syncSettings(outletId: String): OnlineOutletSettings = sessionMutex.withLock {
        val session = requireNotNull(active); requireNotNull(settingsStore)
        val scope = session.scope(outletId); var op = withContext(Dispatchers.IO) { recovered.settings(scope) }
            ?: return@withLock api(session).outletSettings(session,outletId)
        check(op.status in setOf("QUEUED","SENT"))
        var acknowledged = false
        try {
            if (!op.wasSent) {
                val fresh = api(session).outletSettings(session,outletId)
                if (fresh.revision != op.expectedRevision) {
                    op = op.copy(status="CONFLICT",error="SETTINGS_CONFLICT")
                    withContext(Dispatchers.IO) { recovered.save(op) }; throw OnlineConflictChangedException()
                }
            }
            op = op.copy(status="SENT",wasSent=true,error=null)
            withContext(Dispatchers.IO) { recovered.save(op) }
            val revision = api(session).updateSettings(session,op); acknowledged = true
            val fresh = api(session).outletSettings(session,outletId)
            check(fresh.revision >= revision)
            withContext(Dispatchers.IO) { recovered.remove(scope,"SETTINGS",op.operationId,confirmed=true) }
            fresh
        } catch (e: OnlineApiException) {
            val status = when {
                e.status in setOf(401,403) -> "AUTH_REQUIRED"
                acknowledged || e.status >= 500 || e.status in setOf(408,429) -> null
                e.status == 409 -> "CONFLICT"
                else -> "FAILED"
            }
            if (status != null) withContext(Dispatchers.IO) { recovered.save(op.copy(status=status,error=e.code)) }
            throw e
        }
    }
    suspend fun dismissSettings(outletId: String) = sessionMutex.withLock {
        val session = requireNotNull(active); requireNotNull(settingsStore)
        withContext(Dispatchers.IO) { recovered.settings(session.scope(outletId))?.let { recovered.remove(it.scope,"SETTINGS",it.operationId) } }
    }

    suspend fun cancelPreparedLogin(candidate: OnlineSession) {
        try { api(candidate).logout(candidate) } catch (_: IOException) { }
    }

    suspend fun businessDate(outletId: String): String {
        val session = requireNotNull(active)
        return api(session).businessDate(session, outletId)
    }

    suspend fun history(outletId: String, before: String? = null): OnlineHistoryPage {
        val session = requireNotNull(active); val result = api(session).history(session, outletId, before)
        check(active === session) { "Session changed" }; return result
    }
    suspend fun audit(outletId: String, closingId: String, after: String = "0"): OnlineAuditPage {
        val session = requireNotNull(active); val result = api(session).audit(session, outletId, closingId, after)
        check(active === session) { "Session changed" }; return result
    }
    suspend fun reports(outletId: String, closingId: String, before: Long? = null): OnlineReportPage {
        val session = requireNotNull(active); val result = api(session).reports(session, outletId, closingId, before)
        check(active === session) { "Session changed" }; return result
    }
    suspend fun report(outletId: String, closingId: String, generation: Long): String {
        val session = requireNotNull(active); val result = api(session).report(session, outletId, closingId, generation)
        check(active === session) { "Session changed" }; return result
    }

    /** Keep the picker-authorized identity pinned until the verified payload has been written. */
    suspend fun withReportForExport(expectedSession: OnlineSession, reviewed: FrozenClosingReport,
        write: suspend (FrozenClosingReport, String, ReportActor) -> Unit) = sessionMutex.withLock {
        check(active === expectedSession) { "Export session changed" }
        expectedSession.scope(reviewed.outletId)
        val client = api(expectedSession)
        val freshIdentity = client.refreshIdentity(expectedSession).identity
        val report = FrozenClosingReport.parse(client.report(expectedSession, reviewed.outletId, reviewed.closingId, reviewed.generation))
        check(report == reviewed) { "Export report changed" }
        val outlet = client.outletSettings(expectedSession, reviewed.outletId)
        check(active === expectedSession && freshIdentity.outlets.any { it.id == reviewed.outletId })
        write(report, outlet.name, ReportActor(freshIdentity.id, freshIdentity.username, freshIdentity.displayName))
    }
    suspend fun readClosing(outletId: String, closingId: String): OnlineSnapshot {
        val session = requireNotNull(active); val result = api(session).fetch(session, outletId, closingId)
        check(active === session) { "Session changed" }; dao.pull(result.header, result.entries)
        return result
    }

    suspend fun save(outletId: String, closingId: String, quantities: Map<String, Int>) = sessionMutex.withLock {
        val session = requireNotNull(active)
        dao.editBatch(session.scope(outletId), closingId, quantities)
    }

    suspend fun lifecycle(outletId: String, closingId: String, action: String, reason: String,
        reviewedGeneration: Long, reviewedRevision: Long): String = sessionMutex.withLock {
        val session = requireNotNull(active)
        engine(session).prepareLifecycle(session, outletId, closingId, action, reason, reviewedGeneration, reviewedRevision)
    }

    suspend fun dismissLifecycle(outletId: String, closingId: String, operationId: String) {
        val session = requireNotNull(active)
        engine(session).dismissLifecycle(session, outletId, closingId, operationId)
    }

    /** Local credentials are cleared even if server logout is unreachable. Drafts remain scoped. */
    suspend fun logout(confirmPending: Boolean = false): Boolean {
        val old = sessionMutex.withLock {
            val selected = active ?: return true
            if (!confirmPending && pending(selected) > 0) throw PendingOnlineDraftsException()
            withContext(Dispatchers.IO) { store.clear() }
            active = null
            selected
        }
        return try { api(old).logout(old); true } catch (_: IOException) { false }
    }

    suspend fun create(outletId: String, businessDate: String): String {
        val session = requireNotNull(active)
        val remote = api(session).create(session, outletId, businessDate)
        check(active === session) { "Session changed" }
        dao.pull(remote.header, remote.entries)
        return remote.header.closingId
    }

    suspend fun edit(outletId: String, closingId: String, menuId: String, quantity: Int): String = sessionMutex.withLock {
        val session = requireNotNull(active)
        dao.edit(session.scope(outletId), closingId, menuId, quantity)
    }

    suspend fun sync(outletId: String, closingId: String): OnlineSyncResult {
        val session = active ?: return OnlineSyncResult.Blocked("SESSION_REQUIRED")
        return engine(session).sync(session, outletId, closingId)
    }
    suspend fun syncAutomatically(outletId:String,closingId:String):OnlineSyncResult {
        val session=active?:return OnlineSyncResult.Blocked("SESSION_REQUIRED")
        return engine(session).sync(session,outletId,closingId,allowRecovered=false)
    }

    suspend fun resolve(outletId: String, closingId: String, menuId: String, reapply: Boolean,
        reviewedRevision: Long? = null, reviewedGeneration: Long? = null): String? {
        val session = requireNotNull(active)
        return engine(session).resolve(session, outletId, closingId, menuId, reapply, reviewedRevision, reviewedGeneration)
    }
}
