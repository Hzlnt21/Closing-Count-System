package com.closingcount.app

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.closingcount.app.data.local.*
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.json.JSONObject
import org.json.JSONArray

@RunWith(AndroidJUnit4::class)
class OnlinePendingCaptureInstrumentedTest {
    private lateinit var db: ClosingCountDatabase
    private val scope="https://closing.example.test|account-a|outlet-a"
    private val other="https://closing.example.test|account-b|outlet-a"
    @Before fun setup(){db=Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(),ClosingCountDatabase::class.java).build()}
    @After fun close(){db.close()}
    private suspend fun seed(s:String,id:String) {
        val snapshot=JSONObject().put("id",id).put("outletId",s.substringAfterLast('|')).put("generation",1).put("businessDate","2026-10-23")
            .put("catalog",JSONObject().put("menus",JSONArray(listOf(JSONObject().put("id","menu-a").put("name","Menu A"),JSONObject().put("id","menu-b").put("name","Menu B")))))
        db.onlineSyncDao().pull(OnlineClosingEntity(s,id,1,0,"DRAFT",snapshot.toString()),listOf(
            OnlineEntryEntity(s,id,"menu-a",1,0,0,0,"SYNCED"),OnlineEntryEntity(s,id,"menu-b",1,0,0,0,"SYNCED")))
    }
    @Test fun captureIsScopedAndLeavesSentDependentQueueAndSourceRowsUnchanged():Unit=runBlocking {
        val dao=db.onlineSyncDao();seed(scope,"closing-a");seed(scope,"synced-only");seed(other,"closing-a")
        dao.edit(scope,"closing-a","menu-a",4);dao.next(scope,"closing-a");dao.edit(scope,"closing-a","menu-a",8)
        dao.edit(other,"closing-a","menu-b",9)
        val beforeOps=dao.operations(scope,"closing-a");val beforeRows=dao.entries(scope,"closing-a")
        val captured=dao.capturePendingForBackup(scope)
        assertEquals(1,captured.size);assertEquals("closing-a",captured.single().header.closingId)
        assertEquals(beforeOps,captured.single().operations);assertEquals(listOf(4,8),captured.single().operations.map{it.quantity})
        assertEquals(listOf("menu-a"),captured.single().entries.map{it.menuId})
        assertEquals(mapOf("menu-a" to "Menu A"),captured.single().header.menuNames)
        assertEquals(beforeOps,dao.operations(scope,"closing-a"));assertEquals(beforeRows,dao.entries(scope,"closing-a"))
        assertEquals(9,dao.capturePendingForBackup(other).single().entries.single().localQuantity)
    }
    @Test fun lifecycleOnlyCaptureIncludesItsOriginalReviewWithoutSyntheticQuantityWork():Unit=runBlocking {
        val dao=db.onlineSyncDao();seed(scope,"review-a")
        val id=dao.prepareLifecycle(scope,"review-a","submit","",1,0,"EMPLOYEE")
        dao.nextLifecycle(scope,"review-a")
        val captured=dao.capturePendingForBackup(scope).single()
        assertEquals(id,captured.lifecycle?.operationId);assertTrue(captured.lifecycle!!.wasSent)
        assertTrue(captured.operations.isEmpty());assertTrue(captured.entries.isEmpty())
        assertEquals(captured.lifecycle,dao.lifecycle(scope,"review-a"))
    }
    @Test fun inconsistentLocalStateFailsInsteadOfReturningAnIncompleteSuccessfulBackup():Unit=runBlocking {
        val dao=db.onlineSyncDao();seed(scope,"closing-a");dao.edit(scope,"closing-a","menu-a",4)
        db.openHelper.writableDatabase.execSQL("UPDATE online_entries SET status='SYNCED',localQuantity=serverQuantity WHERE scope=? AND closingId=?",arrayOf(scope,"closing-a"))
        assertNotNull(runCatching{dao.capturePendingForBackup(scope)}.exceptionOrNull())
        assertEquals(1,dao.operations(scope,"closing-a").size)
    }
    @Test fun orphanedPendingWorkCannotBeSilentlyOmitted():Unit=runBlocking {
        val dao=db.onlineSyncDao();seed(scope,"closing-a");dao.edit(scope,"closing-a","menu-a",4)
        db.openHelper.writableDatabase.execSQL("DELETE FROM online_closings WHERE scope=? AND closingId=?",arrayOf(scope,"closing-a"))
        assertNotNull(runCatching{dao.capturePendingForBackup(scope)}.exceptionOrNull())
        assertEquals(1,dao.operations(scope,"closing-a").size)
        assertEquals(4,dao.entry(scope,"closing-a","menu-a")!!.localQuantity)
    }
}
