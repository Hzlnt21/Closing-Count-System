package com.closingcount.app

import android.content.Context
import android.content.ContextWrapper
import android.content.ContentValues
import android.net.Uri
import android.provider.MediaStore
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.closingcount.app.data.local.*
import com.closingcount.app.data.online.*
import com.closingcount.app.data.transfer.*
import kotlinx.coroutines.*
import org.json.JSONArray
import org.json.JSONObject
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.io.File
import java.security.MessageDigest
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class OnlinePendingExportInstrumentedTest {
    private val base=ApplicationProvider.getApplicationContext<Context>()
    private lateinit var folder:File
    private lateinit var context:Context
    private lateinit var db:ClosingCountDatabase
    private lateinit var settings:OnlineSettingsStore
    private lateinit var team:OnlineTeamStore
    private lateinit var catalog:OnlineCatalogStore
    private lateinit var sessions:OnlineSessionStore
    private lateinit var repo:OnlineRepository
    private lateinit var session:OnlineSession
    private lateinit var transfer:OnlinePendingBackupTransfer
    private val outlet=UUID.randomUUID().toString()
    private val account=UUID.randomUUID().toString()
    private val menu=UUID.randomUUID().toString()
    private val closing=UUID.randomUUID().toString()
    private val uris=mutableListOf<Uri>()
    private val scope get()=session.scope(outlet)
    private fun wrapped(dir:File)=object:ContextWrapper(base){override fun getNoBackupFilesDir()=dir.apply{mkdirs()}}
    @Before fun setup():Unit=runBlocking {
        folder=File(base.cacheDir,"pending-export-qa-${UUID.randomUUID()}").apply{mkdirs()};context=wrapped(folder)
        db=Room.inMemoryDatabaseBuilder(context,ClosingCountDatabase::class.java).build()
        settings=OnlineSettingsStore(context);team=OnlineTeamStore(context);catalog=OnlineCatalogStore(context)
        sessions=OnlineSessionStore(context,"pending-export-qa-${UUID.randomUUID()}")
        repo=OnlineRepository(db.onlineSyncDao(),sessions,settings,team,catalog)
        // Unreachable loopback and expired credentials deliberately prove local capture needs no HTTP.
        session=OnlineSession("http://127.0.0.1:1",OnlineIdentity(account,"rani.qa","Rani QA",listOf(OnlineOutlet(outlet,"Outlet QA","ADMIN"))),"NEVER_EXPORT_SESSION_TOKEN",Instant.EPOCH)
        repo.activate(session,true)
        transfer=OnlinePendingBackupTransfer(base.contentResolver,repo,OnlineBackupDeviceIdentity(context))
        seed(closing,"2026-10-23")
        db.onlineSyncDao().edit(scope,closing,menu,4);db.onlineSyncDao().next(scope,closing);db.onlineSyncDao().edit(scope,closing,menu,8)
        settings.save(OnlineSettingsOperation(scope,UUID.randomUUID().toString(),2,"Outlet berikutnya","Asia/Jakarta",60))
        team.save(OnlineTeamOperation(scope,UUID.randomUUID().toString(),"CREATE_ACCOUNT",JSONObject().put("username","dimas.qa").put("displayName","Dimas QA").put("role","EMPLOYEE").toString(),"SENT",true))
        catalog.save(OnlineCatalogOperation(scope,UUID.randomUUID().toString(),4,OnlineCatalog.empty().json().toString(),"EDITING"))
    }
    @After fun cleanup() {
        for(uri in uris)base.contentResolver.delete(uri,null,null)
        if(::sessions.isInitialized)sessions.clear()
        if(::db.isInitialized)db.close()
        if(::folder.isInitialized){check(folder.parentFile==base.cacheDir&&folder.name.startsWith("pending-export-qa-"));folder.deleteRecursively()}
    }
    private suspend fun seed(id:String,date:String,menus:List<Pair<String,String>> =listOf(menu to "Kopi susu")) {
        val raw=JSONObject().put("id",id).put("outletId",outlet).put("businessDate",date).put("generation",1)
            .put("catalog",JSONObject().put("menus",JSONArray(menus.map{JSONObject().put("id",it.first).put("name",it.second)})))
            .put("historicalActors","MUST_NOT_EXPORT_HISTORICAL_AUTHORS")
        db.onlineSyncDao().pull(OnlineClosingEntity(scope,id,1,0,"DRAFT",raw.toString()),menus.map{OnlineEntryEntity(scope,id,it.first,1,0,0,0,"SYNCED")})
    }
    private fun destination():Uri {
        val values=ContentValues().apply{put(MediaStore.MediaColumns.DISPLAY_NAME,"ccs-pending-qa-${UUID.randomUUID()}.json");put(MediaStore.MediaColumns.MIME_TYPE,"application/json");put(MediaStore.MediaColumns.RELATIVE_PATH,"Download/ClosingCountQA")}
        return requireNotNull(base.contentResolver.insert(MediaStore.Files.getContentUri("external"),values)).also{
            uris+=it
            // Match a system CreateDocument result: a known existing empty target,
            // rather than only an unmaterialized MediaStore database row.
            requireNotNull(base.contentResolver.openOutputStream(it,"wt")).use { }
        }
    }
    private fun bytes(uri:Uri)=requireNotNull(base.contentResolver.openInputStream(uri)).use{it.readBytes()}
    private fun files()=folder.walkTopDown().filter{it.isFile&&it.name.endsWith(".json")}.associate{it.relativeTo(folder).path to it.readBytes().toList()}
    private fun file(group:String):File {
        val hash=MessageDigest.getInstance("SHA-256").digest(scope.toByteArray()).joinToString(""){"%02x".format(it)}
        return File(folder,"online-$group-operations/$hash.json")
    }
    @Test fun completeLocalPacketIsFrozenThroughSaveAndPreservesSourceEvenWithExpiredSession():Unit=runBlocking {
        val before=files();val legacy=db.backupDao().createSnapshot()
        val review=transfer.prepare(outlet);assertEquals(1,review.closings);assertEquals(setOf("SETTINGS","TEAM_CREATE","CATALOG"),review.adminKinds.toSet())
        db.onlineSyncDao().edit(scope,closing,menu,9)
        val queue=db.onlineSyncDao().operations(scope,closing)
        val uri=destination();assertEquals(review,transfer.write(review.id,uri))
        val body=bytes(uri);val model=OnlinePendingBackupCodec.decode(body)
        assertEquals(review.sha256,model.sha256);assertEquals(8,model.model.closings.single().entries.single().localQuantity)
        assertEquals(listOf(4,8),model.model.closings.single().operations.map{it.quantity})
        assertEquals(queue,db.onlineSyncDao().operations(scope,closing));assertEquals(before,files());assertEquals(legacy,db.backupDao().createSnapshot())
        assertFalse(String(body).contains(session.token));assertFalse(String(body).contains("MUST_NOT_EXPORT_HISTORICAL_AUTHORS"))
        File(base.getExternalFilesDir(null),"pending-export-qa.json").writeBytes(body)
        File(base.getExternalFilesDir(null),"pending-export-qa-proof.json").writeText(JSONObject().put("accountId",account).put("outletId",outlet).put("closingId",closing).put("menuId",menu)
            .put("sourceDeviceId",review.sourceDeviceId).put("sha256",review.sha256).put("frozenQuantity",8).put("currentLocalQuantity",9).put("operations",2).put("admin",3).put("legacyUnchanged",true).toString())
    }
    @Test fun cancelledStaleAndDuplicateCallbacksDoNotWriteAgain():Unit=runBlocking {
        val first=transfer.prepare(outlet);transfer.discard(first.id);val empty=destination()
        assertNotNull(runCatching{transfer.write(first.id,empty)}.exceptionOrNull());assertTrue(bytes(empty).isEmpty())
        val second=transfer.prepare(outlet)
        assertNotNull(runCatching{transfer.write(first.id,empty)}.exceptionOrNull());transfer.write(second.id,empty)
        val saved=bytes(empty);assertNotNull(runCatching{transfer.write(second.id,empty)}.exceptionOrNull());assertArrayEquals(saved,bytes(empty))
    }
    @Test fun replacingTheAccountAfterReviewRejectsWriteBeforeOpeningOutput():Unit=runBlocking {
        val review=transfer.prepare(outlet);val uri=destination();val queue=db.onlineSyncDao().operations(scope,closing)
        val other=OnlineSession(session.origin,OnlineIdentity(UUID.randomUUID().toString(),"dimas.qa","Dimas QA",session.identity.outlets),"OTHER_TOKEN",Instant.EPOCH)
        repo.activate(other,true)
        assertNotNull(runCatching{transfer.write(review.id,uri)}.exceptionOrNull());assertTrue(bytes(uri).isEmpty())
        assertEquals(queue,db.onlineSyncDao().operations(scope,closing))
    }
    @Test fun corruptAdministrativeFilesFailWithoutOmittingOrRepairingTheirBytes():Unit=runBlocking {
        val cases=listOf("settings" to {r:JSONObject->r.put("cutoffMinutes","60")},"team" to {r:JSONObject->r.put("wasSent","true")},
            "catalog" to {r:JSONObject->r.put("catalogJson",JSONObject(r.getString("catalogJson")).put("password","MUST_REJECT").toString())},
            "settings" to {r:JSONObject->r.put("scope","different-account")},"team" to {r:JSONObject->r.put("password","MUST_REJECT")})
        for((group,change) in cases) {val f=file(group);val original=f.readBytes();val raw=JSONObject(String(original));change(raw);val corrupt=raw.toString().toByteArray();f.writeBytes(corrupt)
            assertNotNull(runCatching{transfer.prepare(outlet)}.exceptionOrNull());assertArrayEquals(corrupt,f.readBytes());f.writeBytes(original)}
        val f=file("settings");val original=f.readBytes();val corrupt=String(original).replace("\"formatVersion\":1","\"formatVersion\":1,\"formatVersion\":1").toByteArray();f.writeBytes(corrupt)
        assertNotNull(runCatching{transfer.prepare(outlet)}.exceptionOrNull());assertArrayEquals(corrupt,f.readBytes());f.writeBytes(original)
    }
    @Test fun installationIdentityIsStableSeparateAndDoesNotSilentlyRegenerateCorruption() {
        val device=OnlineBackupDeviceIdentity(context);val id=device.get();assertEquals(id,OnlineBackupDeviceIdentity(context).get())
        val other=OnlineBackupDeviceIdentity(wrapped(File(folder,"other-installation"))).get();assertNotEquals(id,other)
        val f=File(folder,"online-backup-device-id");f.writeText("x".repeat(36));assertNotNull(runCatching{device.get()}.exceptionOrNull());assertEquals("x".repeat(36),f.readText())
    }
    @Test fun providerFailureConsumesOnlyTheExportTicketAndPreservesAllPendingWork():Unit=runBlocking {
        val queue=db.onlineSyncDao().operations(scope,closing);val before=files();val review=transfer.prepare(outlet)
        assertNotNull(runCatching{transfer.write(review.id,Uri.parse("content://missing.closing.qa/file"))}.exceptionOrNull())
        assertNotNull(runCatching{transfer.write(review.id,destination())}.exceptionOrNull())
        assertEquals(queue,db.onlineSyncDao().operations(scope,closing));assertEquals(before,files())
    }
    @Test fun concurrentAcknowledgementAndCaptureAlwaysYieldACompleteValidSnapshot():Unit=runBlocking {
        // Finish initial pending edits first, leaving later iterations independent.
        val dao=db.onlineSyncDao();val first=dao.operations(scope,closing).first();dao.acknowledge(scope,closing,first.operationId,1,1,1,4)
        val tail=dao.next(scope,closing)!!;dao.acknowledge(scope,closing,tail.operationId,1,2,2,8)
        repeat(12) { index ->
            val id=UUID.randomUUID().toString();seed(id,LocalDate.of(2026,9,1).plusDays(index.toLong()).toString())
            dao.edit(scope,id,menu,4);val sent=dao.next(scope,id)!!;dao.edit(scope,id,menu,8)
            val gate=CompletableDeferred<Unit>()
            val snapshot=async(Dispatchers.IO){gate.await();transfer.prepare(outlet)}
            val ack=async(Dispatchers.IO){gate.await();dao.acknowledge(scope,id,sent.operationId,1,1,1,4)}
            gate.complete(Unit);val review=snapshot.await();ack.await();val uri=destination();transfer.write(review.id,uri)
            val c=OnlinePendingBackupCodec.decode(bytes(uri)).model.closings.single()
            assertEquals(8,c.entries.single().localQuantity);assertTrue(c.operations.size in 1..2)
            assertEquals(c.entries.single().baseRevision,c.operations.first().expectedRevision)
            val next=dao.next(scope,id)!!;dao.acknowledge(scope,id,next.operationId,1,2,2,8)
        }
    }
    @Test fun fullCachedCatalogIsProjectedToOnlyThePendingMenuAndNoHistoricalPayload():Unit=runBlocking {
        val dao=db.onlineSyncDao();val first=dao.operations(scope,closing).first();dao.acknowledge(scope,closing,first.operationId,1,1,1,4)
        val next=dao.next(scope,closing)!!;dao.acknowledge(scope,closing,next.operationId,1,2,2,8)
        val id=UUID.randomUUID().toString();val menus=(0 until 500).map{UUID.randomUUID().toString() to "Menu $it ${"x".repeat(100)}"}
        seed(id,"2026-08-01",menus);dao.edit(scope,id,menus.first().first,1)
        val captured=dao.capturePendingForBackup(scope).single();assertEquals(1,captured.header.menuNames.size)
        val review=transfer.prepare(outlet);val uri=destination();transfer.write(review.id,uri);val body=bytes(uri)
        assertTrue(body.size<5000);assertFalse(String(body).contains(menus.last().second));assertFalse(String(body).contains("MUST_NOT_EXPORT_HISTORICAL_AUTHORS"))
    }
    @Test fun teamMembershipBaseAndValuesAreCapturedWithoutReusingLivePayload():Unit=runBlocking {
        val original=requireNotNull(team.load(scope));team.remove(scope,original.operationId,true)
        team.save(OnlineTeamOperation(scope,UUID.randomUUID().toString(),"UPDATE_MEMBERSHIP",JSONObject().put("accountId",UUID.randomUUID().toString()).put("expectedRevision",5).put("role","SUPERVISOR").put("active",false).toString()))
        val review=transfer.prepare(outlet);val uri=destination();transfer.write(review.id,uri)
        val member=OnlinePendingBackupCodec.decode(bytes(uri)).model.admin.first{it.kind=="TEAM_UPDATE"}
        assertEquals(5L,member.expectedRevision);assertFalse(JSONObject(member.payloadJson).getBoolean("active"));assertFalse(JSONObject(member.payloadJson).has("expectedRevision"))
        assertNotNull(team.load(scope))
    }
}
