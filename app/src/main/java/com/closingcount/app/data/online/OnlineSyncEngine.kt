package com.closingcount.app.data.online

import com.closingcount.app.data.local.OnlineSyncDao
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.IOException

sealed interface OnlineSyncResult {
    data object Synced : OnlineSyncResult
    data class Blocked(val code: String) : OnlineSyncResult
    data class Retry(val afterMillis: Long) : OnlineSyncResult
    data object MorePending : OnlineSyncResult
}

class OnlineConflictChangedException : IllegalStateException("Angka server berubah lagi. Periksa pilihan terbaru sebelum mengirim ulang.")

/** One instance per app repository. Manual/foreground callers share this mutex. */
class OnlineSyncEngine(private val dao: OnlineSyncDao, private val gateway: OnlineGateway,
    private val currentSession: () -> OnlineSession?) {
    private val mutex = Mutex()
    private val attempts = mutableMapOf<String, Int>()
    private fun same(session: OnlineSession) = currentSession()?.let {
        it.origin == session.origin && it.identity.id == session.identity.id && it.token == session.token
    } == true

    suspend fun sync(session: OnlineSession, outletId: String, closingId: String): OnlineSyncResult = mutex.withLock {
        val scope = session.scope(outletId)
        if (!same(session)) return@withLock OnlineSyncResult.Blocked("SESSION_CHANGED")
        var sending: String? = null
        try {
            val initial = gateway.fetch(session, outletId, closingId)
            if (!same(session)) return@withLock OnlineSyncResult.Blocked("SESSION_CHANGED")
            dao.pull(initial.header, initial.entries)
            repeat(100) {
                if (!same(session)) return@withLock OnlineSyncResult.Blocked("SESSION_CHANGED")
                val op = dao.next(scope, closingId)
                if (op == null) {
                    val blocked = dao.operations(scope, closingId).firstOrNull()
                    if (blocked != null) return@withLock OnlineSyncResult.Blocked(blocked.errorCode ?: blocked.status)
                    val final = gateway.fetch(session, outletId, closingId)
                    if (!same(session)) return@withLock OnlineSyncResult.Blocked("SESSION_CHANGED")
                    dao.pull(final.header, final.entries)
                    // Another local edit may have been saved while fetch was in flight.
                    if (dao.operations(scope, closingId).isNotEmpty()) return@withLock OnlineSyncResult.MorePending
                    attempts.remove(scope)
                    return@withLock OnlineSyncResult.Synced
                }
                if (!same(session)) return@withLock OnlineSyncResult.Blocked("SESSION_CHANGED")
                sending = op.operationId
                val ack = gateway.send(session, op)
                if (!same(session)) return@withLock OnlineSyncResult.Blocked("SESSION_CHANGED")
                dao.acknowledge(scope, closingId, op.operationId, ack.generation, ack.closingRevision, ack.entryRevision, ack.quantity)
                sending = null
            }
            OnlineSyncResult.MorePending
        } catch (error: OnlineApiException) {
            if (!same(session)) return@withLock OnlineSyncResult.Blocked("SESSION_CHANGED")
            if (error.status >= 500 || error.status in setOf(408, 429)) return@withLock retry(scope)
            val status = when (error.status) {401, 403 -> "AUTH_REQUIRED"; 409 -> "CONFLICT"; else -> "FAILED"}
            val head = dao.operations(scope, closingId).firstOrNull()
            if (head != null && (sending == head.operationId || status == "AUTH_REQUIRED")) {
                dao.block(scope, closingId, head.operationId, status, error.code)
            }
            if (status == "CONFLICT") {
                // Best-effort refresh only; draft and the blocked queue survive a failed refresh.
                try {
                    val remote = gateway.fetch(session, outletId, closingId)
                    if (same(session)) dao.pull(remote.header, remote.entries)
                } catch (cancel: CancellationException) { throw cancel } catch (_: IOException) { }
            }
            OnlineSyncResult.Blocked(error.code)
        } catch (cancel: CancellationException) { throw cancel
        } catch (_: IOException) { retry(scope) }
    }

    suspend fun resolve(session: OnlineSession, outletId: String, closingId: String, menuId: String, reapply: Boolean,
        reviewedRevision: Long? = null, reviewedGeneration: Long? = null): String? =
        mutex.withLock {
            check(same(session))
            val before = requireNotNull(dao.entry(session.scope(outletId), closingId, menuId))
            val latest = gateway.fetch(session, outletId, closingId)
            check(same(session)); dao.pull(latest.header, latest.entries)
            val now = requireNotNull(dao.entry(session.scope(outletId), closingId, menuId))
            if (reapply && (now.serverRevision != (reviewedRevision ?: before.serverRevision) ||
                    now.generation != (reviewedGeneration ?: before.generation))) throw OnlineConflictChangedException()
            dao.resolve(session.scope(outletId), closingId, menuId, reapply)
        }

    private fun retry(scope: String): OnlineSyncResult.Retry {
        val attempt = ((attempts[scope] ?: 0) + 1).coerceAtMost(6); attempts[scope] = attempt
        return OnlineSyncResult.Retry((1000L shl attempt).coerceAtMost(60000))
    }
}
