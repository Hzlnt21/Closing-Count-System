package com.closingcount.app

import android.content.Context
import android.os.Process
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.closingcount.app.data.local.*
import com.closingcount.app.data.online.*
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Multi-process phases orchestrated by an owned-emulator host script. Deliberately retains QA state. */
class OnlineProcessRecoveryInstrumentedTest {
    @Test fun ownedRecoveryPhase() = runBlocking {
        val args=InstrumentationRegistry.getArguments()
        org.junit.Assume.assumeTrue("Explicit owned recovery fixture only",args.getString("ccsRecoveryQa")=="true")
        val context=ApplicationProvider.getApplicationContext<Context>()
        require(args.getString("ccsQaOrigin")=="http://10.0.2.2:4371")
        val phase=requireNotNull(args.getString("ccsRecoveryPhase"))
        val outlet=requireNotNull(args.getString("ccsQaOutlet"))
        require(outlet.matches(Regex("[a-f0-9-]{36}")))
        val peer=context.packageName.endsWith(".qapeer")
        val db=Room.databaseBuilder(context,ClosingCountDatabase::class.java,"ccs-process-recovery-r1.db")
            .addMigrations(Migration6To7,Migration7To8,Migration8To9).build()
        val dao=db.onlineSyncDao();val store=OnlineSessionStore(context);val repo=OnlineRepository(dao,store)
        val file=File(context.filesDir,"ccs-process-recovery-r1.json")
        val state=if(file.exists())JSONObject(file.readText()) else JSONObject()
        fun proof(waiting:Boolean=false){
            state.put("phase",phase).put("pid",Process.myPid()).put("package",context.packageName)
                .put("outlet",outlet).put("waitingForHostKill",waiting)
            file.writeText(state.toString(2))
            println("CCS_RECOVERY_PHASE_READY $phase pid=${Process.myPid()}")
        }
        fun awaitKill(){proof(true);assertFalse("Host must force-stop this process",CountDownLatch(1).await(90,TimeUnit.SECONDS))
            fail("Host did not kill the waiting process")}
        try {
            repo.initialize()
            if(phase=="seed"||phase=="peerSeed"){
                assertEquals(phase=="peerSeed",peer)
                check(!file.exists()){"A fresh owned fixture/installation is required"}
                val user=requireNotNull(args.getString(if(peer)"ccsQaColleague" else "ccsQaEmployee"))
                repo.activate(repo.login("http://10.0.2.2:4371",user,requireNotNull(args.getString("ccsQaPassword"))))
                val id=repo.create(outlet,"2026-10-20");val session=repo.session()!!;val scope=session.scope(outlet)
                val menu=dao.entries(scope,id).first().menuId
                if(peer)assertEquals(7,dao.entry(scope,id,menu)!!.serverQuantity)
                val op=repo.edit(outlet,id,menu,if(peer)9 else 7)
                state.put("closingId",id).put("menuId",menu).put("quantityOperation",op).put("accountId",session.identity.id)
                assertEquals("QUEUED",dao.operations(scope,id).single().status)
                awaitKill()
            }
            val session=requireNotNull(repo.session()){"Encrypted session did not survive process restart"}
            assertEquals(state.getString("accountId"),session.identity.id)
            val id=state.getString("closingId");val menu=state.getString("menuId");val scope=session.scope(outlet)
            when(phase){
                "offline"->{
                    assertFalse(peer);assertEquals(state.getString("quantityOperation"),dao.operations(scope,id).single().operationId)
                    assertTrue(repo.sync(outlet,id) is OnlineSyncResult.Retry)
                    assertEquals("QUEUED",dao.operations(scope,id).single().status)
                    assertEquals(7,dao.entry(scope,id,menu)!!.localQuantity);proof()
                }
                "sendQuantity"->{
                    assertFalse(peer);assertEquals(state.getString("quantityOperation"),dao.operations(scope,id).single().operationId)
                    assertEquals("QUEUED",dao.operations(scope,id).single().status)
                    repo.sync(outlet,id);fail("Host must kill process while committed quantity response is held")
                }
                "recoverQuantity"->{
                    val op=dao.operations(scope,id).single();assertEquals("SENT",op.status)
                    assertEquals(state.getString("quantityOperation"),op.operationId)
                    assertEquals(OnlineSyncResult.Synced,repo.sync(outlet,id))
                    assertTrue(dao.operations(scope,id).isEmpty());assertEquals(7,dao.entry(scope,id,menu)!!.serverQuantity)
                    assertEquals(1L,dao.entry(scope,id,menu)!!.serverRevision);proof()
                }
                "editA"->{
                    assertFalse(peer);repo.edit(outlet,id,menu,11)
                    assertEquals(OnlineSyncResult.Synced,repo.sync(outlet,id))
                    assertEquals(2L,dao.entry(scope,id,menu)!!.serverRevision);proof()
                }
                "peerRecover"->{
                    assertTrue(peer);assertEquals(state.getString("quantityOperation"),dao.operations(scope,id).single().operationId)
                    assertTrue(repo.sync(outlet,id) is OnlineSyncResult.Blocked)
                    val conflict=dao.entry(scope,id,menu)!!
                    assertEquals("CONFLICT",conflict.status);assertEquals(9,conflict.localQuantity);assertEquals(11,conflict.serverQuantity)
                    state.put("conflictLocal",9).put("conflictServer",11)
                    repo.resolve(outlet,id,menu,false)
                    assertTrue(dao.operations(scope,id).isEmpty());assertEquals(11,dao.entry(scope,id,menu)!!.localQuantity);proof()
                }
                "queueLifecycle"->{
                    assertFalse(peer);assertEquals(OnlineSyncResult.Synced,repo.sync(outlet,id))
                    val header=dao.closing(scope,id)!!
                    val op=repo.lifecycle(outlet,id,"submit","",header.generation,header.revision)
                    state.put("lifecycleOperation",op);assertFalse(dao.lifecycle(scope,id)!!.wasSent);awaitKill()
                }
                "sendLifecycle"->{
                    assertEquals(state.getString("lifecycleOperation"),dao.lifecycle(scope,id)!!.operationId)
                    assertFalse(dao.lifecycle(scope,id)!!.wasSent)
                    repo.sync(outlet,id);fail("Host must kill process while committed lifecycle response is held")
                }
                "recoverLifecycle"->{
                    val op=dao.lifecycle(scope,id)!!;assertTrue(op.wasSent);assertEquals("SENT",op.status)
                    assertEquals(state.getString("lifecycleOperation"),op.operationId)
                    assertEquals(OnlineSyncResult.Synced,repo.sync(outlet,id));assertNull(dao.lifecycle(scope,id))
                    assertEquals("SUBMITTED",dao.closing(scope,id)!!.state)
                    assertEquals(11,dao.entry(scope,id,menu)!!.serverQuantity)
                    assertEquals(3L,dao.closing(scope,id)!!.revision);proof()
                }
                else->error("Unknown owned recovery phase")
            }
        } finally { db.close() }
    }
}
