package com.closingcount.app

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.closingcount.app.data.local.*
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.json.JSONArray
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.util.UUID

class OnlineLifecycleInstrumentedTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private lateinit var db: ClosingCountDatabase
    private lateinit var name: String
    private val scope = "https://qa.invalid|account-a|outlet-a"
    private val id = "closing-a"
    private fun header(revision: Long = 0, generation: Long = 1, state: String = "DRAFT") = OnlineClosingEntity(scope,id,generation,revision,state,"{}")
    private fun row(quantity: Int = 0, revision: Long = 0, generation: Long = 1) = OnlineEntryEntity(scope,id,"menu-a",generation,quantity,revision,quantity,"SYNCED")
    private fun open() = Room.databaseBuilder(context,ClosingCountDatabase::class.java,name).addMigrations(Migration6To7,Migration7To8,Migration8To9).build()
    @Before fun setup() { name="ccs-lifecycle-${UUID.randomUUID()}.db"; db=open() }
    @After fun cleanup() {db.close();context.deleteDatabase(name)}

    @Test fun unsyncedQuantityAndStaleReviewCannotPrepareAction() = runBlocking {
        val dao=db.onlineSyncDao();dao.pull(header(),listOf(row()))
        val quantity=dao.edit(scope,id,"menu-a",2)
        var rejected=false
        try {dao.prepareLifecycle(scope,id,"submit","",1,0,"EMPLOYEE")} catch (_:IllegalStateException){rejected=true}
        assertTrue(rejected);assertNull(dao.lifecycle(scope,id))
        dao.next(scope,id);dao.acknowledge(scope,id,quantity,1,1,1,2)
        rejected=false
        try {dao.prepareLifecycle(scope,id,"submit","",1,0,"EMPLOYEE")} catch (_:IllegalStateException){rejected=true}
        assertTrue(rejected);assertNull(dao.lifecycle(scope,id))
        dao.prepareLifecycle(scope,id,"submit","",1,1,"EMPLOYEE")
        assertEquals(1,dao.pendingForAccount("https://qa.invalid|account-a|"))
        rejected=false
        try {dao.edit(scope,id,"menu-a",3)} catch (_:IllegalStateException){rejected=true}
        assertTrue(rejected);assertEquals(2,dao.entry(scope,id,"menu-a")!!.localQuantity)
    }

    @Test fun sentPayloadSurvivesDatabaseReopenAndCannotBeDismissedAsUnsent() = runBlocking {
        var dao=db.onlineSyncDao();dao.pull(header(),listOf(row()))
        val operation=dao.prepareLifecycle(scope,id,"submit","",1,0,"EMPLOYEE")
        val sent=dao.nextLifecycle(scope,id)!!
        db.close();db=open();dao=db.onlineSyncDao()
        assertEquals(sent,dao.nextLifecycle(scope,id))
        var rejected=false
        try{dao.dismissLifecycle(scope,id,operation)}catch(_:IllegalStateException){rejected=true}
        assertTrue(rejected)
        dao.blockLifecycle(scope,id,operation,"AUTH_REQUIRED","SESSION_REQUIRED")
        assertNull(dao.nextLifecycle(scope,id))
        dao.resumeAfterLogin("https://qa.invalid|account-b|outlet-a",id)
        assertEquals("AUTH_REQUIRED",dao.lifecycle(scope,id)!!.status)
        dao.resumeAfterLogin(scope,id);assertEquals(sent,dao.nextLifecycle(scope,id))
    }

    @Test fun changedBaseBlocksQueuedActionButSentRetryKeepsItsOriginalPayload() = runBlocking {
        val dao=db.onlineSyncDao();dao.pull(header(),listOf(row()))
        val op=dao.prepareLifecycle(scope,id,"submit","",1,0,"EMPLOYEE")
        dao.pull(header(1),listOf(row(2,1)))
        assertNull(dao.nextLifecycle(scope,id));assertEquals("CONFLICT",dao.lifecycle(scope,id)!!.status)
        dao.dismissLifecycle(scope,id,op)
        dao.prepareLifecycle(scope,id,"submit","",1,1,"EMPLOYEE")
        val sent=dao.nextLifecycle(scope,id)!!
        dao.pull(header(2,state="SUBMITTED"),listOf(row(2,1)))
        assertEquals(sent,dao.nextLifecycle(scope,id))
    }

    @Test fun acknowledgementAndSnapshotRefreshRollbackTogetherOnSqlFailure() = runBlocking {
        val dao=db.onlineSyncDao();dao.pull(header(),listOf(row()))
        val op=dao.prepareLifecycle(scope,id,"submit","",1,0,"EMPLOYEE");val sent=dao.nextLifecycle(scope,id)
        db.openHelper.writableDatabase.execSQL("CREATE TRIGGER reject_lifecycle BEFORE INSERT ON online_entries BEGIN SELECT RAISE(ABORT,'QA lifecycle failure'); END")
        var failed=false
        try {dao.completeLifecycle(scope,id,op,"SUBMITTED",1,1,header(1,state="SUBMITTED"),listOf(row()))}catch(_:Exception){failed=true}
        assertTrue(failed);assertEquals(sent,dao.lifecycle(scope,id));assertEquals("DRAFT",dao.closing(scope,id)!!.state)
        db.openHelper.writableDatabase.execSQL("DROP TRIGGER reject_lifecycle")
        dao.completeLifecycle(scope,id,op,"SUBMITTED",1,1,header(1,state="SUBMITTED"),listOf(row()))
        assertNull(dao.lifecycle(scope,id));assertEquals("SUBMITTED",dao.closing(scope,id)!!.state)
        val successor=dao.prepareLifecycle(scope,id,"return","Periksa ulang",1,1,"SUPERVISOR")
        dao.completeLifecycle(scope,id,op,"SUBMITTED",1,1,header(1,state="SUBMITTED"),listOf(row()))
        assertEquals(successor,dao.lifecycle(scope,id)!!.operationId)
    }

    @Test fun historicalReceiptDoesNotRegressNewerReopenedGeneration() = runBlocking {
        val dao=db.onlineSyncDao();dao.pull(header(),listOf(row()))
        val op=dao.prepareLifecycle(scope,id,"submit","",1,0,"EMPLOYEE");dao.nextLifecycle(scope,id)
        dao.completeLifecycle(scope,id,op,"SUBMITTED",1,1,header(4,2),listOf(row(6,2,2)))
        assertEquals(2L,dao.closing(scope,id)!!.generation);assertEquals(4L,dao.closing(scope,id)!!.revision)
        assertEquals(6,dao.entry(scope,id,"menu-a")!!.localQuantity);assertNull(dao.lifecycle(scope,id))
    }

    @Test fun roleReasonsAndDefinitiveFailureRecoveryAreExplicit() = runBlocking {
        val dao=db.onlineSyncDao();dao.pull(header(1,state="SUBMITTED"),listOf(row()))
        var failed=false
        try{dao.prepareLifecycle(scope,id,"lock","",1,1,"EMPLOYEE")}catch(_:IllegalStateException){failed=true}
        assertTrue(failed)
        for(reason in listOf(" ","x".repeat(501))){
            failed=false;try{dao.prepareLifecycle(scope,id,"return",reason,1,1,"SUPERVISOR")}catch(_:IllegalArgumentException){failed=true}
            assertTrue(failed);assertNull(dao.lifecycle(scope,id))
        }
        val op=dao.prepareLifecycle(scope,id,"return","  Periksa ulang  ",1,1,"SUPERVISOR")
        assertEquals("Periksa ulang",dao.lifecycle(scope,id)!!.reason)
        dao.nextLifecycle(scope,id);dao.blockLifecycle(scope,id,op,"FAILED","VALIDATION_ERROR")
        dao.dismissLifecycle(scope,id,op);assertNull(dao.lifecycle(scope,id))
    }

    @Test fun version7PendingOperationSurvivesAdditive8Migration() = runBlocking {
        db.close()
        val schema=JSONObject(InstrumentationRegistry.getInstrumentation().context.assets
            .open("com.closingcount.app.data.local.ClosingCountDatabase/7.json").bufferedReader().use{it.readText()}).getJSONObject("database")
        val legacy=SQLiteDatabase.openOrCreateDatabase(context.getDatabasePath(name),null)
        val entities=schema.getJSONArray("entities")
        for(i in 0 until entities.length()){
            val entity=entities.getJSONObject(i);val table=entity.getString("tableName")
            legacy.execSQL(entity.getString("createSql").replace("\${TABLE_NAME}",table))
            val indices=entity.optJSONArray("indices")?:JSONArray()
            for(j in 0 until indices.length())legacy.execSQL(indices.getJSONObject(j).getString("createSql").replace("\${TABLE_NAME}",table))
        }
        val setup=schema.getJSONArray("setupQueries");for(i in 0 until setup.length())legacy.execSQL(setup.getString(i))
        val operation=UUID.randomUUID().toString()
        legacy.execSQL("INSERT INTO online_closings VALUES (?,?,1,0,'DRAFT','{}')",arrayOf(scope,id))
        legacy.execSQL("INSERT INTO online_entries VALUES (?,?,'menu-a',1,0,0,7,'SYNCING')",arrayOf(scope,id))
        legacy.execSQL("INSERT INTO online_operations VALUES (1,?,?,?,'menu-a',1,0,7,'SENT',NULL)",arrayOf(operation,scope,id))
        legacy.version=7;legacy.close();db=open()
        assertEquals(9,db.openHelper.readableDatabase.version)
        val sent=db.onlineSyncDao().next(scope,id)!!;assertEquals(operation,sent.operationId);assertEquals(7,sent.quantity)
        assertNull(db.onlineSyncDao().lifecycle(scope,id))
    }
}
