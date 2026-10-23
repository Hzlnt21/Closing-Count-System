package com.closingcount.app

import android.content.Context
import android.content.ContextWrapper
import android.os.Bundle
import android.os.Process
import android.util.Base64
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.closingcount.app.data.local.*
import com.closingcount.app.data.online.*
import com.closingcount.app.data.transfer.*
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID

/** Explicit same-emulator, separate Android-user installations; never a physical device. */
@RunWith(AndroidJUnit4::class)
class OnlineQuantityRecoveryInstallInstrumentedTest {
    @Test fun pendingPacketMovesBetweenIsolatedInstallationsWithoutChangingOriginalQueue():Unit=runBlocking {
        val args=InstrumentationRegistry.getArguments();check(args.getString("ccsRecoveryQa")=="true")
        val mode=requireNotNull(args.getString("ccsInstallMode"));check(mode in setOf("stage","receive","source-verify"))
        val run=requireNotNull(args.getString("ccsInstallRun"));check(UUID.fromString(run).toString()==run)
        val origin=requireNotNull(args.getString("ccsQaOrigin"));check(origin=="http://10.0.2.2:4371")
        val outlet=OnlineApi.uuid(requireNotNull(args.getString("ccsQaOutlet")))
        val api=OnlineApi(OnlineEndpoint(origin,true),4000)
        val session=api.login(requireNotNull(args.getString("ccsQaEmployee")),requireNotNull(args.getString("ccsQaPassword")))
        val base=ApplicationProvider.getApplicationContext<Context>();val folder=File(base.cacheDir,"quantity-install-qa-$run")
        if(mode=="stage")check(!folder.exists());check(folder.mkdirs()||folder.isDirectory)
        val context=object:ContextWrapper(base){override fun getNoBackupFilesDir()=folder}
        val name="ccs-quantity-install-$run.db"
        val db=Room.databaseBuilder(base,ClosingCountDatabase::class.java,name).addMigrations(Migration8To9).build()
        val store=OnlineSessionStore(context,"quantity-install-qa-$run")
        val repo=OnlineRepository(db.onlineSyncDao(),store,OnlineSettingsStore(context),OnlineTeamStore(context),OnlineCatalogStore(context))
        val dao=db.onlineSyncDao();val scope=session.scope(outlet);val metadata=File(folder,"source-proof.json")
        try {
            repo.activate(session,true)
            when(mode) {
                "stage"->{
                    check(Process.myUid()<100000)
                    val initial=api.create(session,outlet,"2026-09-10");val id=initial.header.closingId;val menu=initial.entries.first().menuId
                    dao.pull(initial.header,initial.entries);repo.edit(outlet,id,menu,4)
                    val head=requireNotNull(dao.next(scope,id));api.send(session,head) // Commit succeeded, ACK deliberately lost.
                    repo.edit(outlet,id,menu,8)
                    val packet=OnlinePendingBackupCodec.encode(repo.capturePendingBackup(session,outlet,OnlineBackupDeviceIdentity(context).get()))
                    val decoded=OnlinePendingBackupCodec.decode(packet);val source=decoded.model.closings.single()
                    metadata.writeText(JSONObject().put("uid",Process.myUid()).put("closing",id).put("sha256",decoded.sha256)
                        .put("head",source.operations[0].operationId).put("tail",source.operations[1].operationId).toString())
                    File(base.getExternalFilesDir(null),"quantity-install-pending-qa.json").writeBytes(packet)
                    proof(JSONObject(metadata.readText()).put("mode",mode).put("run",run).put("sourceQueueUnchanged",true))
                }
                "receive"->{
                    check(Process.myUid()>=100000)
                    val encoded=requireNotNull(args.getString("ccsInstallPacket"));require(encoded.length<10000)
                    val packet=Base64.decode(encoded,Base64.DEFAULT);val decoded=OnlinePendingBackupCodec.decode(packet)
                    val source=decoded.model.closings.single();val id=source.id
                    assertTrue(dao.closingIds(scope).isEmpty())
                    val inbox=OnlinePendingRecoveryInbox(context);val accepted=inbox.accept(packet);val recovery=OnlineQuantityRecovery(inbox,repo)
                    val review=recovery.prepare(accepted.summary.sha256,id);assertEquals(4,review.rows.single().serverQuantity);assertEquals(8,review.rows.single().desired)
                    val result=recovery.apply(review.id);val newId=result.newOperations.values.single()
                    assertFalse(newId in source.operations.map{it.operationId})
                    assertEquals(4,api.fetch(session,outlet,id).entries.first().serverQuantity)
                    assertEquals(OnlineSyncResult.Synced,repo.sync(outlet,id));assertEquals(8,api.fetch(session,outlet,id).entries.first().serverQuantity)
                    val original=api.operationStatuses(session,outlet,source.operations.map{it.operationId});assertEquals(listOf(true,false),source.operations.map{original.getValue(it.operationId)})
                    assertEquals(true,api.operationStatuses(session,outlet,listOf(newId))[newId])
                    val again=recovery.prepare(accepted.summary.sha256,id);assertEquals("ALREADY_RECOVERED_LOCALLY",again.rows.single().disposition);assertTrue(recovery.apply(again.id).newOperations.isEmpty())
                    proof(JSONObject().put("mode",mode).put("run",run).put("uid",Process.myUid()).put("sha256",decoded.sha256).put("closing",id)
                        .put("newId",newId).put("newOperationRecorded",true).put("originalHeadRecorded",true).put("originalTailRecorded",false).put("serverQuantity",8).put("noAutomaticSend",true))
                }
                "source-verify"->{
                    val saved=JSONObject(metadata.readText());assertEquals(saved.getInt("uid"),Process.myUid());check(Process.myUid()<100000)
                    val id=saved.getString("closing");val operations=dao.operations(scope,id)
                    assertEquals(listOf(saved.getString("head"),saved.getString("tail")),operations.map{it.operationId})
                    assertEquals(listOf(4,8),operations.map{it.quantity});assertEquals(listOf("SENT","QUEUED"),operations.map{it.status})
                    assertEquals(listOf(0L,null),operations.map{it.expectedRevision});assertEquals(0L,dao.closing(scope,id)!!.revision)
                    assertEquals(0,dao.entries(scope,id).first().serverQuantity);assertEquals(8,dao.entries(scope,id).first().localQuantity)
                    assertTrue(dao.recoveryClaims(scope,operations.map{it.operationId}).isEmpty())
                    assertEquals(8,api.fetch(session,outlet,id).entries.first().serverQuantity)
                    proof(saved.put("mode",mode).put("run",run).put("sourceQueueUnchanged",true).put("serverQuantity",8))
                }
            }
        }finally {
            db.close();runCatching{api.logout(session)}
            if(mode!="stage") {
                store.clear();check(name=="ccs-quantity-install-$run.db");base.deleteDatabase(name)
                check(folder.parentFile==base.cacheDir&&folder.name=="quantity-install-qa-$run");folder.deleteRecursively()
            }
        }
    }
    private fun proof(json:JSONObject){InstrumentationRegistry.getInstrumentation().sendStatus(2,Bundle().apply{putString("ccsCrossInstallProof",json.toString())})}
}
