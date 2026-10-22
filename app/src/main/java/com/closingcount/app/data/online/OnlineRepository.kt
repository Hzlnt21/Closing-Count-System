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
            withContext(Dispatchers.IO){catalogStore?.load(scope)?.takeIf{it.status=="AUTH_REQUIRED"}?.let{
                catalogStore.save(it.copy(status=if(it.wasSent)"SENT" else "QUEUED",error=null))
            }}
            withContext(Dispatchers.IO) { settingsStore?.load(scope)?.takeIf { it.status == "AUTH_REQUIRED" }?.let {
                settingsStore.save(it.copy(status=if(it.wasSent) "SENT" else "QUEUED",error=null))
            } }
        }
        withContext(Dispatchers.IO){teamStore?.forAccount(prefix(candidate))?.filter{it.status=="AUTH_REQUIRED"}?.forEach{
            teamStore.save(it.copy(status=if(it.wasSent)"SENT" else "QUEUED",error=null))
        }}
    }

    private suspend fun pending(session: OnlineSession) = dao.pendingForAccount(prefix(session)) +
        withContext(Dispatchers.IO) { (settingsStore?.pendingForAccount(prefix(session)) ?: 0)+(teamStore?.forAccount(prefix(session))?.size?:0)+(catalogStore?.pendingForAccount(prefix(session))?:0) }
    suspend fun pendingCount(): Int = active?.let { pending(it) } ?: 0
    suspend fun team(outlet:String,after:String?=null):OnlineTeamPage{
        val session=requireNotNull(active);val value=api(session).team(session,outlet,after);check(active===session);return value
    }
    suspend fun pendingTeam(outlet:String):OnlineTeamOperation?{
        val session=requireNotNull(active);return withContext(Dispatchers.IO){teamStore?.load(session.scope(outlet))}
    }
    suspend fun prepareTeamCreate(outlet:String,username:String,name:String,role:String)=sessionMutex.withLock{
        val session=requireNotNull(active);val storage=requireNotNull(teamStore)
        check(session.identity.outlets.first{it.id==outlet}.role=="ADMIN")
        val payload=org.json.JSONObject().put("username",username.trim().lowercase(java.util.Locale.ROOT)).put("displayName",name.trim()).put("role",role)
        val op=OnlineTeamOperation(session.scope(outlet),java.util.UUID.randomUUID().toString(),"CREATE_ACCOUNT",payload.toString())
        withContext(Dispatchers.IO){check(storage.load(op.scope)==null);storage.save(op)}
    }
    suspend fun prepareTeamEdit(outlet:String,reviewed:OnlineTeamMember,role:String,enabled:Boolean)=sessionMutex.withLock{
        val session=requireNotNull(active);val storage=requireNotNull(teamStore)
        check(session.identity.outlets.first{it.id==outlet}.role=="ADMIN")
        val payload=org.json.JSONObject().put("accountId",reviewed.id).put("expectedRevision",reviewed.revision).put("role",role).put("active",enabled)
        val op=OnlineTeamOperation(session.scope(outlet),java.util.UUID.randomUUID().toString(),"UPDATE_MEMBERSHIP",payload.toString())
        withContext(Dispatchers.IO){check(storage.load(op.scope)==null);storage.save(op)}
    }
    suspend fun syncTeam(outlet:String,password:String?=null):OnlineTeamReceipt=sessionMutex.withLock{
        val session=requireNotNull(active);val storage=requireNotNull(teamStore);val scope=session.scope(outlet)
        var op=withContext(Dispatchers.IO){requireNotNull(storage.load(scope))};check(op.status in setOf("QUEUED","SENT"))
        try{
            if(op.wasSent){
                try{val receipt=api(session).teamReceipt(session,op);withContext(Dispatchers.IO){storage.remove(scope,op.operationId,true)};return@withLock receipt}
                catch(e:OnlineApiException){if(e.status!=404||e.code!="TEAM_OPERATION_NOT_FOUND")throw e}
            }
            if(op.kind=="CREATE_ACCOUNT"&&password==null)throw TeamPasswordRequiredException()
            if(op.kind=="UPDATE_MEMBERSHIP"&&!op.wasSent){
                val p=org.json.JSONObject(op.payload);val fresh=api(session).member(session,outlet,p.getString("accountId"))
                if(fresh.revision!=p.getLong("expectedRevision")){
                    op=op.copy(status="CONFLICT",error="MEMBERSHIP_CONFLICT");withContext(Dispatchers.IO){storage.save(op)};throw OnlineConflictChangedException()
                }
            }
            op=op.copy(status="SENT",wasSent=true,error=null);withContext(Dispatchers.IO){storage.save(op)}
            val receipt=api(session).sendTeam(session,op,password);withContext(Dispatchers.IO){storage.remove(scope,op.operationId,true)};receipt
        }catch(e:OnlineApiException){
            val status=when{e.status in setOf(401,403)->"AUTH_REQUIRED";e.status>=500||e.status in setOf(408,429)||e.code=="NOT_FOUND"->null;e.status==409->"CONFLICT";else->"FAILED"}
            if(status!=null)withContext(Dispatchers.IO){storage.save(op.copy(status=status,error=e.code))};throw e
        }
    }
    suspend fun dismissTeam(outlet:String)=sessionMutex.withLock{
        val session=requireNotNull(active);val storage=requireNotNull(teamStore)
        withContext(Dispatchers.IO){storage.load(session.scope(outlet))?.let{storage.remove(it.scope,it.operationId)}}
    }
    suspend fun pendingOwnTeamAccessCount():Int{
        val session=active?:return 0
        return withContext(Dispatchers.IO){teamStore?.forAccount(prefix(session))?.count{it.wasSent&&it.status in setOf("SENT","AUTH_REQUIRED")&&it.ownAccessLoss(session.identity.id)}?:0}
    }
    suspend fun reconcileOwnTeamAccess():Int=sessionMutex.withLock{
        val session=requireNotNull(active);val storage=requireNotNull(teamStore)
        val ops=withContext(Dispatchers.IO){storage.forAccount(prefix(session)).filter{it.wasSent&&it.status in setOf("SENT","AUTH_REQUIRED")&&it.ownAccessLoss(session.identity.id)}}
        var confirmed=0
        for(op in ops){
            val receipt=api(session).teamReceipt(session,op);check(receipt.member.id==session.identity.id)
            withContext(Dispatchers.IO){storage.remove(op.scope,op.operationId,true)};confirmed++
        };confirmed
    }

    suspend fun catalog(outlet:String):OnlineCatalogVersion{
        val session=requireNotNull(active);val result=api(session).catalog(session,outlet);check(active===session);return result
    }
    suspend fun pendingCatalog(outlet:String):OnlineCatalogOperation?{
        val session=requireNotNull(active);return withContext(Dispatchers.IO){catalogStore?.load(session.scope(outlet))}
    }
    suspend fun prepareCatalog(outlet:String,reviewedRevision:Long,catalog:OnlineCatalog)=sessionMutex.withLock{
        val session=requireNotNull(active);val storage=requireNotNull(catalogStore)
        val settings=api(session).outletSettings(session,outlet);check(settings.role=="ADMIN")
        val fresh=api(session).catalog(session,outlet)
        if(fresh.revision!=reviewedRevision)throw OnlineConflictChangedException()
        val op=OnlineCatalogOperation(session.scope(outlet),java.util.UUID.randomUUID().toString(),reviewedRevision,catalog.json().toString())
        withContext(Dispatchers.IO){check(storage.load(op.scope)==null);storage.save(op)}
    }
    suspend fun syncCatalog(outlet:String):OnlineCatalogVersion=sessionMutex.withLock{
        val session=requireNotNull(active);val storage=requireNotNull(catalogStore);val scope=session.scope(outlet)
        var op=withContext(Dispatchers.IO){requireNotNull(storage.load(scope))};check(op.status in setOf("QUEUED","SENT"))
        var acknowledged=false
        try{
            if(!op.wasSent){
                val fresh=api(session).catalog(session,outlet)
                if(fresh.revision!=op.expectedRevision){op=op.copy(status="CONFLICT",error="CATALOG_CONFLICT");withContext(Dispatchers.IO){storage.save(op)};throw OnlineConflictChangedException()}
            }
            op=op.copy(status="SENT",wasSent=true,error=null);withContext(Dispatchers.IO){storage.save(op)}
            val receipt=api(session).publishCatalog(session,op);acknowledged=true
            val fresh=api(session).catalog(session,outlet);check(fresh.revision>=receipt.revision)
            withContext(Dispatchers.IO){storage.remove(scope,op.operationId,true)};fresh
        }catch(e:OnlineApiException){
            val status=when{e.status in setOf(401,403)->"AUTH_REQUIRED"
                acknowledged||e.status>=500||e.status in setOf(408,429)||e.code=="NOT_FOUND"->null
                e.status==409->"CONFLICT"
                else->"FAILED"}
            if(status!=null)withContext(Dispatchers.IO){storage.save(op.copy(status=status,error=e.code))};throw e
        }
    }
    suspend fun dismissCatalog(outlet:String)=sessionMutex.withLock{
        val session=requireNotNull(active);val storage=requireNotNull(catalogStore)
        withContext(Dispatchers.IO){storage.load(session.scope(outlet))?.let{storage.remove(it.scope,it.operationId)}}
    }

    suspend fun outletSettings(outletId: String): OnlineOutletSettings {
        val session = requireNotNull(active); val value = api(session).outletSettings(session,outletId)
        check(active === session); return value
    }
    suspend fun pendingSettings(outletId: String): OnlineSettingsOperation? {
        val session = requireNotNull(active)
        return withContext(Dispatchers.IO) { settingsStore?.load(session.scope(outletId)) }
    }
    suspend fun prepareSettings(outletId: String, name: String, timezone: String, cutoff: Int, reviewedRevision: Long) = sessionMutex.withLock {
        val session = requireNotNull(active); val storage = requireNotNull(settingsStore)
        val fresh = api(session).outletSettings(session,outletId)
        check(fresh.role == "ADMIN"); if (fresh.revision != reviewedRevision) throw OnlineConflictChangedException()
        val op = OnlineSettingsOperation(session.scope(outletId),java.util.UUID.randomUUID().toString(),reviewedRevision,name.trim(),timezone,cutoff)
        withContext(Dispatchers.IO) { check(storage.load(op.scope) == null);storage.save(op) }
    }
    suspend fun syncSettings(outletId: String): OnlineOutletSettings = sessionMutex.withLock {
        val session = requireNotNull(active); val storage = requireNotNull(settingsStore)
        val scope = session.scope(outletId); var op = withContext(Dispatchers.IO) { storage.load(scope) }
            ?: return@withLock api(session).outletSettings(session,outletId)
        check(op.status in setOf("QUEUED","SENT"))
        var acknowledged = false
        try {
            if (!op.wasSent) {
                val fresh = api(session).outletSettings(session,outletId)
                if (fresh.revision != op.expectedRevision) {
                    op = op.copy(status="CONFLICT",error="SETTINGS_CONFLICT")
                    withContext(Dispatchers.IO) { storage.save(op) }; throw OnlineConflictChangedException()
                }
            }
            op = op.copy(status="SENT",wasSent=true,error=null)
            withContext(Dispatchers.IO) { storage.save(op) }
            val revision = api(session).updateSettings(session,op); acknowledged = true
            val fresh = api(session).outletSettings(session,outletId)
            check(fresh.revision >= revision)
            withContext(Dispatchers.IO) { storage.remove(scope,op.operationId,confirmed=true) }
            fresh
        } catch (e: OnlineApiException) {
            val status = when {
                e.status in setOf(401,403) -> "AUTH_REQUIRED"
                acknowledged || e.status >= 500 || e.status in setOf(408,429) -> null
                e.status == 409 -> "CONFLICT"
                else -> "FAILED"
            }
            if (status != null) withContext(Dispatchers.IO) { storage.save(op.copy(status=status,error=e.code)) }
            throw e
        }
    }
    suspend fun dismissSettings(outletId: String) = sessionMutex.withLock {
        val session = requireNotNull(active); val storage = requireNotNull(settingsStore)
        withContext(Dispatchers.IO) { storage.load(session.scope(outletId))?.let { storage.remove(it.scope,it.operationId) } }
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

    suspend fun resolve(outletId: String, closingId: String, menuId: String, reapply: Boolean,
        reviewedRevision: Long? = null, reviewedGeneration: Long? = null): String? {
        val session = requireNotNull(active)
        return engine(session).resolve(session, outletId, closingId, menuId, reapply, reviewedRevision, reviewedGeneration)
    }
}
