package com.closingcount.app

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.closingcount.app.data.local.*
import com.closingcount.app.data.online.*
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.io.IOException
import java.util.UUID

class OnlineLifecycleHttpInstrumentedTest {
    private val context=ApplicationProvider.getApplicationContext<Context>()
    private lateinit var api:OnlineApi
    private lateinit var outlet:String
    private lateinit var employee:String
    private lateinit var colleague:String
    private lateinit var supervisor:String
    private lateinit var password:String
    private lateinit var name:String
    private lateinit var db:ClosingCountDatabase
    private val sessions=mutableListOf<OnlineSession>()
    @Before fun setup(){
        val args=InstrumentationRegistry.getArguments()
        org.junit.Assume.assumeTrue("Explicit owned QA only",args.getString("ccsLifecycleQa")=="true")
        require(args.getString("ccsQaOrigin")=="http://10.0.2.2:4371")
        api=OnlineApi(OnlineEndpoint("http://10.0.2.2:4371",true),4000)
        outlet=requireNotNull(args.getString("ccsQaOutlet"));employee=requireNotNull(args.getString("ccsQaEmployee"))
        colleague=requireNotNull(args.getString("ccsQaColleague"));supervisor=requireNotNull(args.getString("ccsQaSupervisor"))
        password=requireNotNull(args.getString("ccsQaPassword"));name="ccs-life-http-${UUID.randomUUID()}.db"
        db=open()
    }
    private fun open()=Room.databaseBuilder(context,ClosingCountDatabase::class.java,name).addMigrations(Migration6To7,Migration7To8).build()
    private suspend fun login(user:String=employee)=api.login(user,password).also{sessions.add(it)}
    @After fun cleanup()=runBlocking{
        if(::api.isInitialized)sessions.forEach{try{api.logout(it)}catch(_:IOException){}}
        if(::db.isInitialized){db.close();context.deleteDatabase(name)}
    }

    @Test fun lostLifecycleResponseRetriesExactReceiptAfterReopenWithoutDuplicateSubmit()=runBlocking{
        val a=login();var dao=db.onlineSyncDao();val first=api.create(a,outlet,"2026-10-10");val id=first.header.closingId
        dao.pull(first.header,first.entries)
        var lost=true
        val gateway=object:OnlineGateway by api{
            override suspend fun lifecycle(session:OnlineSession,operation:OnlineLifecycleEntity):OnlineLifecycleReceipt{
                val result=api.lifecycle(session,operation)
                if(lost){lost=false;throw IOException("Injected lost lifecycle response after commit")}
                return result
            }
        }
        val engine=OnlineSyncEngine(dao,gateway){a}
        val op=engine.prepareLifecycle(a,outlet,id,"submit","",1,0)
        assertTrue(engine.sync(a,outlet,id) is OnlineSyncResult.Retry)
        val sent=dao.lifecycle(a.scope(outlet),id)!!;assertEquals(op,sent.operationId);assertTrue(sent.wasSent)
        assertEquals("SUBMITTED",api.fetch(a,outlet,id).header.state)
        db.close();db=open();dao=db.onlineSyncDao()
        assertEquals(sent,dao.lifecycle(a.scope(outlet),id))
        assertEquals(OnlineSyncResult.Synced,OnlineSyncEngine(dao,api){a}.sync(a,outlet,id))
        assertNull(dao.lifecycle(a.scope(outlet),id));assertEquals(1L,dao.closing(a.scope(outlet),id)!!.revision)
        var denied=false
        try{api.lifecycle(a,OnlineLifecycleEntity(a.scope(outlet),id,UUID.randomUUID().toString(),"lock",1,1,"","SENT",true,null))}
        catch(error:OnlineApiException){denied=error.status==403}
        assertTrue(denied);assertEquals("SUBMITTED",api.fetch(a,outlet,id).header.state)
    }

    @Test fun submitReturnLockReopenPreserveImmutableReportAndActorScope()=runBlocking{
        val a=login();val s=login(supervisor);val dao=db.onlineSyncDao()
        val initial=api.create(a,outlet,"2026-10-11");val id=initial.header.closingId;val menu=initial.entries.first().menuId
        val ea=OnlineSyncEngine(dao,api){a};val es=OnlineSyncEngine(dao,api){s}
        dao.pull(initial.header,initial.entries);dao.edit(a.scope(outlet),id,menu,4)
        assertEquals(OnlineSyncResult.Synced,ea.sync(a,outlet,id))
        suspend fun act(session:OnlineSession,engine:OnlineSyncEngine,action:String,reason:String=""):OnlineSnapshot{
            val reviewed=api.fetch(session,outlet,id)
            engine.prepareLifecycle(session,outlet,id,action,reason,reviewed.header.generation,reviewed.header.revision)
            assertEquals(OnlineSyncResult.Synced,engine.sync(session,outlet,id))
            return api.fetch(session,outlet,id)
        }
        assertEquals("SUBMITTED",act(a,ea,"submit").header.state)
        assertEquals(2L,act(s,es,"return","Periksa rekap").header.generation)
        act(a,ea,"submit");val locked=act(s,es,"lock")
        assertEquals("LOCKED",locked.header.state);val report=api.report(s,outlet,id,2)
        val reopened=act(s,es,"reopen","Koreksi jumlah");assertEquals(3L,reopened.header.generation)
        assertEquals(OnlineSyncResult.Synced,ea.sync(a,outlet,id))
        dao.edit(a.scope(outlet),id,menu,6);assertEquals(OnlineSyncResult.Synced,ea.sync(a,outlet,id))
        assertEquals(report,api.report(a,outlet,id,2))
        assertEquals(6,api.fetch(s,outlet,id).entries.first{it.menuId==menu}.serverQuantity)
    }

    @Test fun queuedReviewDoesNotSilentlyRebaseWhenColleagueChangesServer()=runBlocking{
        val a=login();val b=login(colleague);val dao=db.onlineSyncDao()
        val first=api.create(a,outlet,"2026-10-12");val id=first.header.closingId;val entry=first.entries.first()
        val engine=OnlineSyncEngine(dao,api){a}
        val queued=engine.prepareLifecycle(a,outlet,id,"submit","",1,0)
        api.send(b,OnlineOperationEntity(operationId=UUID.randomUUID().toString(),scope=b.scope(outlet),closingId=id,menuId=entry.menuId,
            generation=1,expectedRevision=0,quantity=5,status="SENT",errorCode=null))
        assertEquals(OnlineSyncResult.Blocked("LIFECYCLE_REVIEW_CHANGED"),engine.sync(a,outlet,id))
        assertEquals("DRAFT",api.fetch(a,outlet,id).header.state);assertFalse(dao.lifecycle(a.scope(outlet),id)!!.wasSent)
        engine.dismissLifecycle(a,outlet,id,queued)
        val fresh=api.fetch(a,outlet,id)
        val next=engine.prepareLifecycle(a,outlet,id,"submit","",fresh.header.generation,fresh.header.revision)
        assertNotEquals(queued,next);assertEquals(OnlineSyncResult.Synced,engine.sync(a,outlet,id))
        assertEquals("SUBMITTED",api.fetch(a,outlet,id).header.state)
    }
}
