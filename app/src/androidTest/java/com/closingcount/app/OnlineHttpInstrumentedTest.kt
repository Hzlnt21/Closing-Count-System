package com.closingcount.app

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import android.content.Context
import com.closingcount.app.data.local.*
import com.closingcount.app.data.online.*
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.IOException
import java.security.KeyStore
import java.util.UUID

/** Explicit opt-in arguments required; never selects a physical phone or operational endpoint. */
@RunWith(AndroidJUnit4::class)
class OnlineHttpInstrumentedTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private lateinit var api: OnlineApi
    private lateinit var outlet: String
    private lateinit var username: String
    private lateinit var colleague: String
    private lateinit var password: String
    private val databases = mutableListOf<Pair<String, ClosingCountDatabase>>()
    private val sessions = mutableListOf<OnlineSession>()
    @Before fun setup() {
        val args = InstrumentationRegistry.getArguments()
        org.junit.Assume.assumeTrue("Run with the explicit owned HTTP fixture", args.getString("ccsHttpQa") == "true")
        val base = requireNotNull(args.getString("ccsQaOrigin"))
        require(base == "http://10.0.2.2:4371")
        api = OnlineApi(OnlineEndpoint(base, true), 3000)
        outlet = OnlineApi.uuid(requireNotNull(args.getString("ccsQaOutlet")))
        username = requireNotNull(args.getString("ccsQaEmployee"))
        colleague = requireNotNull(args.getString("ccsQaColleague"))
        password = requireNotNull(args.getString("ccsQaPassword"))
    }
    private fun database(): ClosingCountDatabase {
        val name = "ccs-http-${UUID.randomUUID()}.db"
        return Room.databaseBuilder(context, ClosingCountDatabase::class.java, name).addMigrations(Migration6To7).build()
            .also { databases.add(name to it) }
    }
    private suspend fun login(user: String = username) = api.login(user, password).also { sessions.add(it) }
    @After fun cleanup() = runBlocking {
        if (::api.isInitialized) sessions.forEach { try { api.logout(it) } catch (_: IOException) { } }
        databases.forEach { (name, db) -> db.close(); context.deleteDatabase(name) }
    }

    @Test fun twoAccountsDetectServerConflictAndDeliberateReapplyPreservesAttribution() = runBlocking {
        val a = login(); val b = login(colleague)
        val da = database().onlineSyncDao(); val db = database().onlineSyncDao()
        val initial = api.create(a, outlet, "2026-10-01")
        val id = initial.header.closingId; val menu = initial.entries.first().menuId
        da.pull(initial.header, initial.entries)
        val remoteB = api.fetch(b, outlet, id); db.pull(remoteB.header, remoteB.entries)
        da.edit(a.scope(outlet), id, menu, 2); db.edit(b.scope(outlet), id, menu, 3)
        val engineA = OnlineSyncEngine(da, api) { a }
        var race = true
        val racingGateway = object : OnlineGateway {
            override suspend fun fetch(session: OnlineSession, outletId: String, closingId: String): OnlineSnapshot {
                val old = api.fetch(session, outletId, closingId)
                if (race) { race = false; assertEquals(OnlineSyncResult.Synced, engineA.sync(a, outlet, id)) }
                return old
            }
            override suspend fun send(session: OnlineSession, operation: OnlineOperationEntity) = api.send(session, operation)
        }
        val engineB = OnlineSyncEngine(db, racingGateway) { b }
        assertEquals(OnlineSyncResult.Blocked("ENTRY_CONFLICT"), engineB.sync(b, outlet, id))
        val conflict = db.entry(b.scope(outlet), id, menu)!!
        assertEquals(2, conflict.serverQuantity); assertEquals(3, conflict.localQuantity); assertEquals("CONFLICT", conflict.status)
        val rejected = db.operations(b.scope(outlet), id).single().operationId
        val replacement = engineB.resolve(b, outlet, id, menu, true)
        assertNotEquals(rejected, replacement)
        assertEquals(OnlineSyncResult.Synced, engineB.sync(b, outlet, id))
        val final = api.fetch(a, outlet, id)
        assertEquals(3, final.entries.first { it.menuId == menu }.serverQuantity)
        val json = org.json.JSONObject(final.header.snapshotJson).getJSONArray("entries")
        val changed = (0 until json.length()).map { json.getJSONObject(it) }.first { it.getString("menuId") == menu }
        assertEquals(b.identity.id, changed.getJSONObject("updatedBy").getString("id"))
    }

    @Test fun lostAcknowledgementRetriesSameReceiptAfterDatabaseReopen() = runBlocking {
        val session = login(); var db = database()
        var dao = db.onlineSyncDao()
        val initial = api.create(session, outlet, "2026-10-02")
        val id = initial.header.closingId; val menu = initial.entries.first().menuId; val scope = session.scope(outlet)
        dao.pull(initial.header, initial.entries)
        val operation = dao.edit(scope, id, menu, 7)
        var lost = true
        val gateway = object : OnlineGateway {
            override suspend fun fetch(session: OnlineSession, outletId: String, closingId: String) = api.fetch(session, outletId, closingId)
            override suspend fun send(session: OnlineSession, operation: OnlineOperationEntity): OnlineReceipt {
                val receipt = api.send(session, operation)
                if (lost) { lost = false; throw IOException("Injected lost response after real commit") }
                return receipt
            }
        }
        assertTrue(OnlineSyncEngine(dao, gateway) { session }.sync(session, outlet, id) is OnlineSyncResult.Retry)
        assertEquals("SENT", dao.operations(scope, id).single().status)
        val accepted = api.fetch(session, outlet, id).entries.first { it.menuId == menu }
        assertEquals(7, accepted.serverQuantity)
        val name = databases.last().first; db.close()
        db = Room.databaseBuilder(context, ClosingCountDatabase::class.java, name).addMigrations(Migration6To7).build()
        databases[databases.lastIndex] = name to db; dao = db.onlineSyncDao()
        assertEquals(operation, dao.operations(scope, id).single().operationId)
        assertEquals(OnlineSyncResult.Synced, OnlineSyncEngine(dao, api) { session }.sync(session, outlet, id))
        val after = api.fetch(session, outlet, id).entries.first { it.menuId == menu }
        assertEquals(accepted.serverRevision, after.serverRevision)
        assertTrue(dao.operations(scope, id).isEmpty())
    }

    @Test fun revokedSessionPausesQueueWithoutMovingItToAnotherAccount() = runBlocking {
        val a = login(); val b = login(colleague); val dao = database().onlineSyncDao()
        var active: OnlineSession? = a
        val remote = api.create(a, outlet, "2026-10-03"); val id = remote.header.closingId
        dao.pull(remote.header, remote.entries)
        val operation = dao.edit(a.scope(outlet), id, remote.entries.first().menuId, 9)
        api.logout(a)
        val engine = OnlineSyncEngine(dao, api) { active }
        assertEquals(OnlineSyncResult.Blocked("SESSION_REQUIRED"), engine.sync(a, outlet, id))
        assertEquals("AUTH_REQUIRED", dao.operations(a.scope(outlet), id).single().status)
        active = b
        assertEquals(OnlineSyncResult.Blocked("SESSION_CHANGED"), engine.sync(a, outlet, id))
        assertTrue(dao.operations(b.scope(outlet), id).isEmpty())
        active = login(); dao.resumeAfterLogin(active!!.scope(outlet), id)
        assertEquals(operation, dao.operations(a.scope(outlet), id).single().operationId)
        assertEquals(OnlineSyncResult.Synced, engine.sync(active!!, outlet, id))
    }

    @Test fun accountSwitchDuringResponseLeavesOriginalScopeReceiptForSafeRetry() = runBlocking {
        val a = login(); val b = login(colleague); var active: OnlineSession? = a
        val dao = database().onlineSyncDao(); val remote = api.create(a, outlet, "2026-10-04")
        val id = remote.header.closingId; val menu = remote.entries.first().menuId
        dao.pull(remote.header, remote.entries); val op = dao.edit(a.scope(outlet), id, menu, 5)
        val gateway = object : OnlineGateway {
            override suspend fun fetch(session: OnlineSession, outletId: String, closingId: String) = api.fetch(session, outletId, closingId)
            override suspend fun send(session: OnlineSession, operation: OnlineOperationEntity): OnlineReceipt {
                val response = api.send(session, operation); active = b; return response
            }
        }
        assertEquals(OnlineSyncResult.Blocked("SESSION_CHANGED"), OnlineSyncEngine(dao, gateway) { active }.sync(a, outlet, id))
        assertEquals(op, dao.operations(a.scope(outlet), id).single().operationId)
        assertEquals("SENT", dao.operations(a.scope(outlet), id).single().status)
        assertTrue(dao.operations(b.scope(outlet), id).isEmpty())
        active = a
        assertEquals(OnlineSyncResult.Synced, OnlineSyncEngine(dao, api) { active }.sync(a, outlet, id))
    }

    @Test fun newerServerValueAfterConflictReviewRequiresAnotherDeliberateReview() = runBlocking {
        val a = login(); val b = login(colleague)
        val da = database().onlineSyncDao(); val db = database().onlineSyncDao()
        val initial = api.create(a, outlet, "2026-10-06"); val id = initial.header.closingId; val menu = initial.entries.first().menuId
        da.pull(initial.header, initial.entries)
        val rb = api.fetch(b, outlet, id); db.pull(rb.header, rb.entries)
        da.edit(a.scope(outlet), id, menu, 2); db.edit(b.scope(outlet), id, menu, 3)
        val ea = OnlineSyncEngine(da, api) { a }; val eb = OnlineSyncEngine(db, api) { b }
        assertEquals(OnlineSyncResult.Synced, ea.sync(a, outlet, id))
        assertTrue(eb.sync(b, outlet, id) is OnlineSyncResult.Blocked)
        val reviewed = db.entry(b.scope(outlet), id, menu)!!
        da.edit(a.scope(outlet), id, menu, 5); assertEquals(OnlineSyncResult.Synced, ea.sync(a, outlet, id))
        var warned = false
        try { eb.resolve(b, outlet, id, menu, true, reviewed.serverRevision, reviewed.generation) }
        catch (_: OnlineConflictChangedException) { warned = true }
        assertTrue(warned)
        assertEquals(5, api.fetch(a, outlet, id).entries.first { it.menuId == menu }.serverQuantity)
        val latest = db.entry(b.scope(outlet), id, menu)!!
        assertEquals(3, latest.localQuantity); assertEquals("CONFLICT", latest.status)
        eb.resolve(b, outlet, id, menu, true, latest.serverRevision, latest.generation)
        assertEquals(OnlineSyncResult.Synced, eb.sync(b, outlet, id))
        assertEquals(3, api.fetch(a, outlet, id).entries.first { it.menuId == menu }.serverQuantity)
    }

    @Test fun sessionCiphertextSurvivesStoreReopenWithoutPlainTokenOrPassword() = runBlocking {
        val session = login(); val alias = "ccs-qa-session-${UUID.randomUUID()}"
        val store = OnlineSessionStore(context, alias)
        try {
            store.save(session)
            val raw = context.getSharedPreferences("ccs_online_session", Context.MODE_PRIVATE).all.values.joinToString()
            assertFalse(raw.contains(session.token)); assertFalse(raw.contains(password))
            val loaded = OnlineSessionStore(context, alias).load()!!
            assertTrue(session.token == loaded.token); assertEquals(session.identity.id, loaded.identity.id)
            assertEquals("OnlineSession(redacted)", loaded.toString())
            assertEquals(session.identity.id, api.refreshIdentity(loaded).identity.id)
        } finally {
            store.clear(); KeyStore.getInstance("AndroidKeyStore").apply { load(null); deleteEntry(alias) }
        }
    }

    @Test fun repositoryRequiresConfirmationBeforeLeavingPendingAccount() = runBlocking {
        val a = login(); val b = login(colleague); val dao = database().onlineSyncDao()
        val alias = "ccs-qa-repo-${UUID.randomUUID()}"; val store = OnlineSessionStore(context, alias)
        val repo = OnlineRepository(dao, store)
        try {
            repo.initialize(); repo.activate(a)
            val id = repo.create(outlet, "2026-10-05")
            val menu = dao.entry(a.scope(outlet), id, api.fetch(a, outlet, id).entries.first().menuId)!!.menuId
            repo.edit(outlet, id, menu, 17)
            var warned = false
            try { repo.activate(b) } catch (_: PendingOnlineDraftsException) { warned = true }
            assertTrue(warned); assertEquals(a.identity.id, repo.session()!!.identity.id)
            repo.activate(b, true)
            assertEquals(OnlineSyncResult.Synced, repo.sync(outlet, id))
            assertEquals(0, api.fetch(b, outlet, id).entries.first { it.menuId == menu }.serverQuantity)
            assertEquals(17, dao.entry(a.scope(outlet), id, menu)!!.localQuantity)
            repo.activate(a)
            assertEquals(OnlineSyncResult.Synced, repo.sync(outlet, id))
            repo.edit(outlet, id, menu, 18)
            warned = false
            try { repo.logout() } catch (_: PendingOnlineDraftsException) { warned = true }
            assertTrue(warned); assertNotNull(repo.session())
            assertTrue(repo.logout(true)); assertNull(repo.session())
            assertEquals(18, dao.entry(a.scope(outlet), id, menu)!!.localQuantity)
            assertEquals(1, dao.operations(a.scope(outlet), id).size)
        } finally {
            store.clear(); KeyStore.getInstance("AndroidKeyStore").apply { load(null); deleteEntry(alias) }
        }
    }
}
