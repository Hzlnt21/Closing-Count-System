package com.closingcount.app

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.closingcount.app.data.local.*
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.json.JSONArray
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class OnlineSyncInstrumentedTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private lateinit var db: ClosingCountDatabase
    private lateinit var name: String
    private val scope = "https://qa.invalid|account-a|outlet-a"
    private val closingId = "closing-a"
    private fun header(revision: Long = 0, generation: Long = 1, state: String = "DRAFT") =
        OnlineClosingEntity(scope, closingId, generation, revision, state, "{}")
    private fun remote(menu: String = "menu-a", quantity: Int = 0, revision: Long = 0, generation: Long = 1) =
        OnlineEntryEntity(scope, closingId, menu, generation, quantity, revision, quantity, "SYNCED")
    private fun open() = Room.databaseBuilder(context, ClosingCountDatabase::class.java, name)
        .addMigrations(Migration6To7, Migration7To8, Migration8To9).build()

    @Before fun setup() {
        name = "ccs-online-test-${UUID.randomUUID()}.db"
        db = open()
    }
    @After fun cleanup() { db.close(); context.deleteDatabase(name) }

    @Test fun pendingAndSentSurviveProcessStyleDatabaseReopenWithSamePayload() = runBlocking {
        var dao = db.onlineSyncDao()
        dao.pull(header(), listOf(remote()))
        val id = dao.edit(scope, closingId, "menu-a", 4)
        val sent = dao.next(scope, closingId)!!
        db.close(); db = open(); dao = db.onlineSyncDao()
        assertEquals(sent, dao.next(scope, closingId))
        assertEquals(id, dao.operations(scope, closingId).single().operationId)
        assertEquals(4, dao.entry(scope, closingId, "menu-a")!!.localQuantity)
    }

    @Test fun lateAckPreservesNewerLocalEditAndRebasesOnlyAfterPredecessorAck() = runBlocking {
        val dao = db.onlineSyncDao()
        dao.pull(header(), listOf(remote()))
        val first = dao.edit(scope, closingId, "menu-a", 2)
        dao.next(scope, closingId)
        val second = dao.edit(scope, closingId, "menu-a", 7)
        assertNull(dao.operations(scope, closingId).last().expectedRevision)
        dao.acknowledge(scope, closingId, first, 1, 1, 1, 2)
        assertEquals(7, dao.entry(scope, closingId, "menu-a")!!.localQuantity)
        val next = dao.next(scope, closingId)!!
        assertEquals(second, next.operationId); assertEquals(1L, next.expectedRevision)
        dao.acknowledge(scope, closingId, first, 1, 1, 1, 2) // duplicate ack cannot delete successor
        assertEquals(next, dao.next(scope, closingId))
        dao.acknowledge(scope, closingId, second, 1, 2, 2, 7)
        assertTrue(dao.operations(scope, closingId).isEmpty())
        assertEquals("SYNCED", dao.entry(scope, closingId, "menu-a")!!.status)
    }

    @Test fun pullPreservesDirtyQuantityAndExplicitReapplyUsesNewOperationAndBase() = runBlocking {
        val dao = db.onlineSyncDao()
        dao.pull(header(), listOf(remote()))
        val oldId = dao.edit(scope, closingId, "menu-a", 8)
        dao.pull(header(2), listOf(remote(quantity = 3, revision = 2)))
        assertEquals(8, dao.entry(scope, closingId, "menu-a")!!.localQuantity)
        assertEquals("CONFLICT", dao.entry(scope, closingId, "menu-a")!!.status)
        assertNull(dao.next(scope, closingId))
        val newId = dao.resolve(scope, closingId, "menu-a", true)
        assertNotEquals(oldId, newId)
        assertEquals(2L, dao.next(scope, closingId)!!.expectedRevision)
    }

    @Test fun committedButUnacknowledgedPullKeepsExactRetryAndOldReceiptCannotRegressNewerServer() = runBlocking {
        val dao = db.onlineSyncDao()
        dao.pull(header(), listOf(remote()))
        val id = dao.edit(scope, closingId, "menu-a", 2)
        val sent = dao.next(scope, closingId)
        dao.edit(scope, closingId, "menu-a", 8)
        dao.pull(header(4), listOf(remote(quantity = 5, revision = 4)))
        assertEquals(sent, dao.next(scope, closingId))
        dao.acknowledge(scope, closingId, id, 1, 1, 1, 2)
        val row = dao.entry(scope, closingId, "menu-a")!!
        assertEquals(5, row.serverQuantity); assertEquals(8, row.localQuantity)
        assertEquals("CONFLICT", row.status)
        assertEquals(4L, dao.closing(scope, closingId)!!.revision)
    }

    @Test fun outOfOrderPullDoesNotRegressAcknowledgedEntry() = runBlocking {
        val dao = db.onlineSyncDao()
        dao.pull(header(), listOf(remote()))
        val id = dao.edit(scope, closingId, "menu-a", 3); dao.next(scope, closingId)
        dao.acknowledge(scope, closingId, id, 1, 2, 2, 3)
        dao.pull(header(1), listOf(remote(quantity = 1, revision = 1)))
        assertEquals(3, dao.entry(scope, closingId, "menu-a")!!.localQuantity)
    }

    @Test fun partitionAndAuthPauseNeverDeliverUnderDifferentAccount() = runBlocking {
        val dao = db.onlineSyncDao()
        dao.pull(header(), listOf(remote()))
        val id = dao.edit(scope, closingId, "menu-a", 4)
        val sent = dao.next(scope, closingId)!!
        dao.block(scope, closingId, id, "AUTH_REQUIRED", "SESSION_REQUIRED")
        assertNull(dao.next(scope, closingId))
        assertNull(dao.next("https://qa.invalid|account-b|outlet-a", closingId))
        dao.resumeAfterLogin("https://qa.invalid|account-b|outlet-a", closingId)
        assertNull(dao.next(scope, closingId))
        dao.resumeAfterLogin(scope, closingId)
        assertEquals(sent, dao.next(scope, closingId))
    }

    @Test fun reopenedGenerationPreservesDraftAndUseServerDoesNotUploadOldGeneration() = runBlocking {
        val dao = db.onlineSyncDao()
        dao.pull(header(), listOf(remote()))
        dao.edit(scope, closingId, "menu-a", 9)
        dao.pull(header(4, 2), listOf(remote(quantity = 2, revision = 1, generation = 2)))
        assertEquals("CONFLICT", dao.entry(scope, closingId, "menu-a")!!.status)
        dao.resolve(scope, closingId, "menu-a", false)
        assertTrue(dao.operations(scope, closingId).isEmpty())
        assertEquals(2, dao.entry(scope, closingId, "menu-a")!!.localQuantity)
    }

    @Test fun localQuantityAndOperationRollbackTogetherWhenSqlWriteFails() = runBlocking {
        val dao = db.onlineSyncDao()
        dao.pull(header(), listOf(remote()))
        db.openHelper.writableDatabase.execSQL("""CREATE TRIGGER reject_local_update BEFORE INSERT ON online_entries
            WHEN NEW.localQuantity != NEW.serverQuantity BEGIN SELECT RAISE(ABORT, 'test failure'); END""")
        var failed = false
        try { dao.edit(scope, closingId, "menu-a", 7) } catch (_: Exception) { failed = true }
        assertTrue(failed)
        assertTrue(dao.operations(scope, closingId).isEmpty())
        assertEquals(0, dao.entry(scope, closingId, "menu-a")!!.localQuantity)
    }

    @Test fun invalidEditAndSubmittedStateDoNotCreateOperations() = runBlocking {
        val dao = db.onlineSyncDao()
        dao.pull(header(), listOf(remote()))
        for (quantity in listOf(-1, 10000)) {
            var failed = false
            try { dao.edit(scope, closingId, "menu-a", quantity) } catch (_: IllegalArgumentException) { failed = true }
            assertTrue(failed)
        }
        dao.pull(header(1, state = "SUBMITTED"), listOf(remote()))
        var failed = false
        try { dao.edit(scope, closingId, "menu-a", 1) } catch (_: IllegalStateException) { failed = true }
        assertTrue(failed); assertTrue(dao.operations(scope, closingId).isEmpty())
    }

    @Test fun acknowledgementRollbackKeepsExactRetryUntilAllWritesSucceed() = runBlocking {
        val dao = db.onlineSyncDao()
        dao.pull(header(), listOf(remote()))
        val id = dao.edit(scope, closingId, "menu-a", 7)
        val sent = dao.next(scope, closingId)!!
        db.openHelper.writableDatabase.execSQL("""CREATE TRIGGER reject_ack BEFORE INSERT ON online_entries
            WHEN NEW.serverQuantity = 7 BEGIN SELECT RAISE(ABORT, 'ack test failure'); END""")
        var failed = false
        try { dao.acknowledge(scope, closingId, id, 1, 1, 1, 7) } catch (_: Exception) { failed = true }
        assertTrue(failed)
        assertEquals(sent, dao.operations(scope, closingId).single())
        assertEquals(0L, dao.closing(scope, closingId)!!.revision)
        assertEquals(0, dao.entry(scope, closingId, "menu-a")!!.serverQuantity)
        db.openHelper.writableDatabase.execSQL("DROP TRIGGER reject_ack")
        dao.acknowledge(scope, closingId, id, 1, 1, 1, 7)
        assertTrue(dao.operations(scope, closingId).isEmpty())
        assertEquals(7, dao.entry(scope, closingId, "menu-a")!!.serverQuantity)
    }

    @Test fun invalidRemoteBatchRollsBackHeaderAndEarlierEntries() = runBlocking {
        val dao = db.onlineSyncDao()
        dao.pull(header(), listOf(remote()))
        var failed = false
        try {
            dao.pull(header(2), listOf(remote(quantity = 2, revision = 1),
                remote(menu = "menu-b", quantity = 10000, revision = 2)))
        } catch (_: IllegalArgumentException) { failed = true }
        assertTrue(failed)
        assertEquals(0L, dao.closing(scope, closingId)!!.revision)
        assertEquals(0, dao.entry(scope, closingId, "menu-a")!!.serverQuantity)
        assertNull(dao.entry(scope, closingId, "menu-b"))
    }

    @Test fun failedOperationNeedsExplicitRecoveryAndBoundaryValuesRemainValid() = runBlocking {
        val dao = db.onlineSyncDao()
        dao.pull(header(), listOf(remote()))
        val oldId = dao.edit(scope, closingId, "menu-a", 9999)
        dao.next(scope, closingId)
        dao.block(scope, closingId, oldId, "FAILED", "VALIDATION_ERROR")
        assertNull(dao.next(scope, closingId))
        val newId = dao.resolve(scope, closingId, "menu-a", true)
        assertNotEquals(oldId, newId)
        val sent = dao.next(scope, closingId)!!
        assertEquals(9999, sent.quantity)
        dao.acknowledge(scope, closingId, sent.operationId, 1, 1, 1, 9999)
        val zero = dao.edit(scope, closingId, "menu-a", 0)
        dao.next(scope, closingId)
        dao.acknowledge(scope, closingId, zero, 1, 2, 2, 0)
        assertEquals(0, dao.entry(scope, closingId, "menu-a")!!.serverQuantity)
    }

    @Test fun receiptForOldGenerationCannotReplaceReopenedServerQuantity() = runBlocking {
        val dao = db.onlineSyncDao()
        dao.pull(header(), listOf(remote()))
        val id = dao.edit(scope, closingId, "menu-a", 9)
        val sent = dao.next(scope, closingId)!!
        dao.pull(header(4, 2), listOf(remote(quantity = 2, revision = 1, generation = 2)))
        assertEquals(sent, dao.next(scope, closingId))
        dao.acknowledge(scope, closingId, id, 1, 1, 1, 9)
        assertTrue(dao.operations(scope, closingId).isEmpty())
        val row = dao.entry(scope, closingId, "menu-a")!!
        assertEquals(2L, row.generation)
        assertEquals(2, row.localQuantity)
        assertEquals("SYNCED", row.status)
        assertEquals(4L, dao.closing(scope, closingId)!!.revision)
    }

    @Test fun batchEditRollsBackEarlierMenusIfAnyMenuIsBlocked() = runBlocking {
        val dao = db.onlineSyncDao()
        dao.pull(header(), listOf(remote(), remote(menu = "menu-b")))
        val blocked = dao.edit(scope, closingId, "menu-b", 1)
        dao.next(scope, closingId); dao.block(scope, closingId, blocked, "FAILED", "VALIDATION_ERROR")
        var failed = false
        try { dao.editBatch(scope, closingId, linkedMapOf("menu-a" to 3, "menu-b" to 4)) }
        catch (_: IllegalStateException) { failed = true }
        assertTrue(failed)
        assertEquals(0, dao.entry(scope, closingId, "menu-a")!!.localQuantity)
        assertEquals(blocked, dao.operations(scope, closingId).single().operationId)
        dao.resolve(scope, closingId, "menu-b", false)
        dao.editBatch(scope, closingId, mapOf("menu-a" to 2, "menu-b" to 3))
        assertEquals(2, dao.entry(scope, closingId, "menu-a")!!.localQuantity)
        assertEquals(3, dao.entry(scope, closingId, "menu-b")!!.localQuantity)
        assertEquals(2, dao.operations(scope, closingId).size)
    }

    @Test fun realVersion6MigrationPreservesLegacyRowsAndDoesNotUploadHistory() = runBlocking {
        db.close()
        val schema = JSONObject(InstrumentationRegistry.getInstrumentation().context.assets
            .open("com.closingcount.app.data.local.ClosingCountDatabase/6.json")
            .bufferedReader().use { it.readText() }).getJSONObject("database")
        val legacy = SQLiteDatabase.openOrCreateDatabase(context.getDatabasePath(name), null)
        val entities = schema.getJSONArray("entities")
        for (i in 0 until entities.length()) {
            val entity = entities.getJSONObject(i)
            legacy.execSQL(entity.getString("createSql").replace("\${TABLE_NAME}", entity.getString("tableName")))
            val indices = entity.optJSONArray("indices") ?: JSONArray()
            for (j in 0 until indices.length()) legacy.execSQL(indices.getJSONObject(j).getString("createSql")
                .replace("\${TABLE_NAME}", entity.getString("tableName")))
        }
        val setup = schema.getJSONArray("setupQueries")
        for (i in 0 until setup.length()) legacy.execSQL(setup.getString(i))
        legacy.execSQL("INSERT INTO app_metadata VALUES ('migration-marker','preserve-me')")
        legacy.execSQL("INSERT INTO closings (id,date,updatedAt) VALUES (41,'2026-10-21',123)")
        legacy.execSQL("INSERT INTO closing_menu_entries VALUES (41,7,'Legacy Latte',1,1,'Coffee',1,3)")
        legacy.version = 6; legacy.close()
        db = open()
        assertEquals("preserve-me", db.backupDao().getMetadata().single().value)
        assertEquals(41L, db.backupDao().getClosings().single().id)
        assertEquals(3, db.backupDao().getClosingMenuEntries().single().quantity)
        assertTrue(db.onlineSyncDao().operations(scope, closingId).isEmpty())
        assertNull(db.onlineSyncDao().closing(scope, closingId))
        assertEquals(9, db.openHelper.readableDatabase.version)
    }
}
