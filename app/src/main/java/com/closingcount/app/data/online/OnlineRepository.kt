package com.closingcount.app.data.online

import com.closingcount.app.data.local.OnlineSyncDao
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.IOException

class PendingOnlineDraftsException : IllegalStateException("Konfirmasi diperlukan: akun ini memiliki perubahan yang belum terkirim.")

/** Application-owned session and engines. No automatic network call at application startup. */
class OnlineRepository(private val dao: OnlineSyncDao, private val store: OnlineSessionStore) {
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

    /** Prepare login, then allow UI to confirm switching accounts without replacing the old session. */
    suspend fun login(origin: String, username: String, password: String): OnlineSession =
        OnlineApi(OnlineEndpoint(origin, allowLocalQa = true)).login(username, password)

    suspend fun activate(candidate: OnlineSession, confirmPendingSwitch: Boolean = false) = sessionMutex.withLock {
        val old = active
        if (old != null && prefix(old) != prefix(candidate) && dao.pendingForAccount(prefix(old)) > 0 && !confirmPendingSwitch) {
            throw PendingOnlineDraftsException()
        }
        withContext(Dispatchers.IO) { store.save(candidate) }
        active = candidate
        for (outlet in candidate.identity.outlets) {
            val scope = candidate.scope(outlet.id)
            for (id in dao.closingIds(scope)) dao.resumeAfterLogin(scope, id)
        }
    }

    suspend fun pendingCount(): Int = active?.let { dao.pendingForAccount(prefix(it)) } ?: 0

    suspend fun cancelPreparedLogin(candidate: OnlineSession) {
        try { api(candidate).logout(candidate) } catch (_: IOException) { }
    }

    suspend fun businessDate(outletId: String): String {
        val session = requireNotNull(active)
        return api(session).businessDate(session, outletId)
    }

    suspend fun save(outletId: String, closingId: String, quantities: Map<String, Int>) = sessionMutex.withLock {
        val session = requireNotNull(active)
        dao.editBatch(session.scope(outletId), closingId, quantities)
    }

    /** Local credentials are cleared even if server logout is unreachable. Drafts remain scoped. */
    suspend fun logout(confirmPending: Boolean = false): Boolean {
        val old = sessionMutex.withLock {
            val selected = active ?: return true
            if (!confirmPending && dao.pendingForAccount(prefix(selected)) > 0) throw PendingOnlineDraftsException()
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
