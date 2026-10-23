package com.closingcount.app

import android.app.Application
import android.content.Context
import android.content.ContextWrapper
import android.content.ContentValues
import android.net.Uri
import android.provider.MediaStore
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.closingcount.app.data.local.*
import com.closingcount.app.data.online.*
import com.closingcount.app.data.transfer.*
import com.closingcount.app.ui.online.OnlinePendingTransferViewModel
import kotlinx.coroutines.*
import org.json.JSONArray
import org.json.JSONObject
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.io.File
import java.time.Instant
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class OnlinePendingTransferViewModelInstrumentedTest {
    private val base=ApplicationProvider.getApplicationContext<Application>()
    private lateinit var folder:File;private lateinit var context:Context;private lateinit var db:ClosingCountDatabase
    private lateinit var repo:OnlineRepository;private lateinit var sessions:OnlineSessionStore;private lateinit var vm:OnlinePendingTransferViewModel
    private val outlet=UUID.randomUUID().toString();private val actor=UUID.randomUUID().toString();private val closing=UUID.randomUUID().toString();private val menu=UUID.randomUUID().toString()
    private val session=OnlineSession("https://closing.example.test",OnlineIdentity(actor,"rani.qa","Rani QA",listOf(OnlineOutlet(outlet,"Outlet QA","ADMIN"))),"NEVER_EXPORT_VM_QA_TOKEN",Instant.EPOCH)
    private val uris=mutableListOf<Uri>()
    private fun main(block:()->Unit)=InstrumentationRegistry.getInstrumentation().runOnMainSync(block)
    private suspend fun idle(){withTimeout(10000){while(vm.state.value.busy)delay(15)}}
    @Before fun setup():Unit=runBlocking {
        folder=File(base.cacheDir,"pending-vm-qa-${UUID.randomUUID()}").apply{mkdirs()};context=object:ContextWrapper(base){override fun getNoBackupFilesDir()=folder}
        db=Room.inMemoryDatabaseBuilder(base,ClosingCountDatabase::class.java).build();sessions=OnlineSessionStore(context,"pending-vm-qa-${UUID.randomUUID()}")
        repo=OnlineRepository(db.onlineSyncDao(),sessions,OnlineSettingsStore(context),OnlineTeamStore(context),OnlineCatalogStore(context));repo.activate(session)
        val raw=JSONObject().put("id",closing).put("outletId",outlet).put("businessDate","2026-10-23").put("generation",1)
            .put("catalog",JSONObject().put("menus",JSONArray().put(JSONObject().put("id",menu).put("name","Kopi QA"))))
        db.onlineSyncDao().pull(OnlineClosingEntity(session.scope(outlet),closing,1,0,"DRAFT",raw.toString()),listOf(OnlineEntryEntity(session.scope(outlet),closing,menu,1,0,0,0,"SYNCED")))
        repo.edit(outlet,closing,menu,4)
        main{vm=OnlinePendingTransferViewModel(base,context,repo,true);vm.open(outlet)};idle()
    }
    @After fun cleanup():Unit=runBlocking {
        if(::vm.isInitialized){idle();main{vm.close()}}
        for(uri in uris)base.contentResolver.delete(uri,null,null)
        if(::sessions.isInitialized)sessions.clear();if(::db.isInitialized)db.close()
        if(::folder.isInitialized){check(folder.parentFile==base.cacheDir&&folder.name.startsWith("pending-vm-qa-"));folder.deleteRecursively()}
    }
    private fun source(bytes:ByteArray):Uri {
        val values=ContentValues().apply{put(MediaStore.MediaColumns.DISPLAY_NAME,"pending-vm-qa-${UUID.randomUUID()}.json");put(MediaStore.MediaColumns.MIME_TYPE,"application/json");put(MediaStore.MediaColumns.RELATIVE_PATH,"Download/ClosingCountQA")}
        return requireNotNull(base.contentResolver.insert(MediaStore.Files.getContentUri("external"),values)).also{uri->uris+=uri;rewrite(uri,bytes)}
    }
    private fun rewrite(uri:Uri,bytes:ByteArray){requireNotNull(base.contentResolver.openOutputStream(uri,"wt")).use{it.write(bytes)}}
    private fun read(uri:Uri)=requireNotNull(base.contentResolver.openInputStream(uri)).use{it.readBytes()}
    @Test fun providerRewritePreviewAcceptBackAndRemovalKeepLiveDraftAndSession():Unit=runBlocking {
        val original=OnlinePendingBackupCodec.encode(inboxPacket());val decoded=OnlinePendingBackupCodec.decode(original);val uri=source(original)
        main{assertTrue(vm.beginOpen());vm.finishOpen(uri)};idle();val review=requireNotNull(vm.state.value.importing)
        assertTrue(vm.state.value.entries.isEmpty());rewrite(uri,OnlinePendingBackupCodec.encode(inboxPacket()))
        main{vm.acceptImport(review.id)};idle();assertEquals(decoded.model,vm.state.value.model);assertEquals(decoded.sha256,vm.state.value.file!!.sha256)
        main{vm.back()};idle();assertNull(vm.state.value.file);assertEquals(1,vm.state.value.entries.size)
        main{vm.prepareRemoval(decoded.sha256)};idle();val removal=requireNotNull(vm.state.value.removal)
        main{vm.remove(removal.id)};idle();assertTrue(vm.state.value.entries.isEmpty())
        assertTrue(repo.session()===session);assertEquals(4,db.onlineSyncDao().entries(session.scope(outlet),closing).single().localQuantity)
        assertEquals(1,db.onlineSyncDao().operations(session.scope(outlet),closing).size)
    }
    @Test fun cancelledAndDuplicatePickerCallbacksNeverWriteAnUnselectedTarget():Unit=runBlocking {
        main{assertTrue(vm.beginOpen());vm.finishOpen(null)};idle();assertTrue(vm.state.value.entries.isEmpty())
        main{vm.prepareExport()};idle();val review=requireNotNull(vm.state.value.export)
        val unused=source("untouched".toByteArray());main{vm.finishSave(unused)};idle();assertEquals("untouched",String(read(unused)))
        val selected=source(byteArrayOf());main{assertNotNull(vm.beginSave(review.id));vm.finishSave(selected)};idle()
        val saved=OnlinePendingBackupCodec.decode(read(selected));assertEquals(actor,saved.model.account.id);assertEquals(4,saved.model.closings.single().entries.single().localQuantity)
        main{vm.finishSave(unused)};idle();assertEquals("untouched",String(read(unused)));assertEquals(1,db.onlineSyncDao().operations(session.scope(outlet),closing).size)
    }
    @Test fun accountSwitchAfterSavePickerLaunchFailsBeforeOutputStreamOpens():Unit=runBlocking {
        main{vm.prepareExport()};idle();val review=requireNotNull(vm.state.value.export);val target=source("preserve".toByteArray())
        main{assertNotNull(vm.beginSave(review.id))}
        val other=OnlineSession(session.origin,OnlineIdentity(UUID.randomUUID().toString(),"dimas.qa","Dimas QA",listOf(OnlineOutlet(outlet,"Outlet QA","EMPLOYEE"))),"OTHER_VM_QA_TOKEN",Instant.EPOCH)
        repo.activate(other,true);main{vm.finishSave(target)};idle();assertTrue(vm.state.value.failed);assertEquals("preserve",String(read(target)))
        assertEquals(1,db.onlineSyncDao().operations(session.scope(outlet),closing).size);assertTrue(repo.session()===other)
    }
}
