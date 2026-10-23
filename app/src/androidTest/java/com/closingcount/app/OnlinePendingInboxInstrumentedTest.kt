package com.closingcount.app

import android.content.Context
import android.content.ContextWrapper
import android.content.ContentValues
import android.net.Uri
import android.provider.MediaStore
import android.util.AtomicFile
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.closingcount.app.data.transfer.*
import kotlinx.coroutines.*
import org.json.JSONArray
import org.json.JSONObject
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID

internal fun inboxPacket(backupId:String=UUID.randomUUID().toString()):OnlinePendingBackup {
    fun id(n:Int)="00000000-0000-4000-8000-${n.toString().padStart(12,'0')}"
    return OnlinePendingBackup(backupId,"2026-10-23T00:00:00Z",id(2),"https://closing.example.test",
        PendingBackupAccount(id(3),"rani.qa","Rani QA"),PendingBackupOutlet(id(7),"Outlet QA"),
        listOf(PendingBackupClosing(id(8),"2026-10-23",2,5,"DRAFT",
            listOf(PendingBackupEntry(id(4),"Kopi susu",6,2,8,"PENDING")),
            listOf(PendingBackupQuantity(id(5),id(4),1,0,4,"SENT"),PendingBackupQuantity(id(6),id(4),2,null,8,"QUEUED")),null),
            PendingBackupClosing(id(9),"2026-10-22",2,4,"DRAFT",emptyList(),emptyList(),PendingBackupLifecycle(id(10),"reopen",1,3,"Koreksi angka","AUTH_REQUIRED",true))),
        listOf(PendingBackupAdmin("SETTINGS",id(11),2,"QUEUED",false,JSONObject().put("name","Outlet berikutnya").put("timezone","Asia/Jakarta").put("cutoffMinutes",0).toString()),
            PendingBackupAdmin("TEAM_CREATE",id(12),null,"SENT",true,JSONObject().put("username","dimas.qa").put("displayName","Dimas QA").put("role","EMPLOYEE").toString()),
            PendingBackupAdmin("CATALOG",id(13),4,"EDITING",false,JSONObject().put("ingredientCategories",JSONArray()).put("ingredients",JSONArray()).put("menuCategories",JSONArray()).put("menus",JSONArray()).toString())))
}

@RunWith(AndroidJUnit4::class)
class OnlinePendingInboxInstrumentedTest {
    private val base=ApplicationProvider.getApplicationContext<Context>()
    private lateinit var folder:File
    private lateinit var context:Context
    private lateinit var inbox:OnlinePendingRecoveryInbox
    private lateinit var transfer:OnlinePendingRecoveryTransfer
    private val uris=mutableListOf<Uri>()
    @Before fun setup() {
        folder=File(base.cacheDir,"pending-inbox-qa-${UUID.randomUUID()}").apply{mkdirs()}
        context=object:ContextWrapper(base){override fun getNoBackupFilesDir()=folder}
        inbox=OnlinePendingRecoveryInbox(context);transfer=OnlinePendingRecoveryTransfer(base.contentResolver,inbox)
        File(folder,"existing-session-sentinel").writeText("unchanged-session")
        File(folder,"existing-draft-sentinel").writeText("unchanged-draft")
    }
    @After fun cleanup() {
        for(uri in uris)base.contentResolver.delete(uri,null,null)
        assertEquals("unchanged-session",File(folder,"existing-session-sentinel").readText())
        assertEquals("unchanged-draft",File(folder,"existing-draft-sentinel").readText())
        check(folder.parentFile==base.cacheDir&&folder.name.startsWith("pending-inbox-qa-"));folder.deleteRecursively()
    }
    private fun bytes()=OnlinePendingBackupCodec.encode(inboxPacket())
    private fun fail(block:()->Unit){assertNotNull(runCatching(block).exceptionOrNull())}
    private suspend fun failSuspend(block:suspend()->Unit){assertNotNull(runCatching{block()}.exceptionOrNull())}
    private fun file(hash:String)=File(folder,"online-recovery-inbox/$hash.json")
    private fun source(bytes:ByteArray):Uri {
        val values=ContentValues().apply{put(MediaStore.MediaColumns.DISPLAY_NAME,"ccs-inbox-qa-${UUID.randomUUID()}.json");put(MediaStore.MediaColumns.MIME_TYPE,"application/json");put(MediaStore.MediaColumns.RELATIVE_PATH,"Download/ClosingCountQA")}
        return requireNotNull(base.contentResolver.insert(MediaStore.Files.getContentUri("external"),values)).also{uri->uris+=uri;rewrite(uri,bytes)}
    }
    private fun rewrite(uri:Uri,bytes:ByteArray){requireNotNull(base.contentResolver.openOutputStream(uri,"wt")).use{it.write(bytes)}}
    @Test fun previewAndCancellationNeverAcceptAndStaleCallbacksCannotAccept():Unit=runBlocking {
        val old=transfer.prepare(source(bytes()));assertTrue(inbox.list().isEmpty())
        transfer.discard(old.id);failSuspend{transfer.accept(old.id)};assertTrue(inbox.list().isEmpty())
        val next=transfer.prepare(source(bytes()));failSuspend{transfer.accept(old.id)}
        assertEquals(next.summary,transfer.accept(next.id).summary);failSuspend{transfer.accept(next.id)}
    }
    @Test fun providerRewriteAfterPreviewKeepsOriginalBytesAndMetadata():Unit=runBlocking {
        val original=bytes();val uri=source(original);val review=transfer.prepare(uri)
        rewrite(uri,bytes());val accepted=transfer.accept(review.id)
        assertFalse(accepted.duplicate);assertEquals(review.summary,accepted.summary)
        assertArrayEquals(original,file(review.summary.sha256).readBytes())
        assertEquals(OnlinePendingBackupCodec.decode(original).model,inbox.read(review.summary.sha256).model)
    }
    @Test fun duplicateFingerprintIsIdempotentAcrossFreshControllersAndAllPayloadsSurvive():Unit=runBlocking {
        val raw=bytes();val first=inbox.accept(raw)
        val reopened=OnlinePendingRecoveryInbox(context)
        assertEquals(listOf(first.summary),reopened.list());assertTrue(reopened.accept(raw).duplicate)
        assertEquals(OnlinePendingBackupCodec.decode(raw).model,reopened.read(first.summary.sha256).model)
        assertEquals(listOf("SETTINGS","TEAM_CREATE","CATALOG"),first.summary.adminKinds)
        assertEquals("2026-10-22",first.summary.firstDate);assertEquals("2026-10-23",first.summary.lastDate)
        val original=file(first.summary.sha256).readBytes();raw.fill(0);assertArrayEquals(original,file(first.summary.sha256).readBytes())
    }
    @Test fun malformedAndMissingProviderCannotClearPreviouslyAcceptedRecovery():Unit=runBlocking {
        val accepted=inbox.accept(bytes());val original=file(accepted.summary.sha256).readBytes()
        failSuspend{transfer.prepare(source("{broken".toByteArray()))}
        val uri=source(bytes());base.contentResolver.delete(uri,null,null);uris.remove(uri)
        failSuspend{transfer.prepare(uri)}
        assertEquals(listOf(accepted.summary),inbox.list());assertArrayEquals(original,file(accepted.summary.sha256).readBytes())
        fail{inbox.read("../escape")};fail{inbox.accept(ByteArray(OnlinePendingBackupCodec.MAX_BYTES+1))}
    }
    @Test fun corruptAcceptedBytesAreNotSilentlyReplacedByDuplicateSource() {
        val raw=bytes();val accepted=inbox.accept(raw);val target=file(accepted.summary.sha256)
        target.writeBytes(bytes()) // Valid packet under the wrong fingerprint must fail too.
        val corrupt=target.readBytes();fail{inbox.read(accepted.summary.sha256)};fail{inbox.accept(raw)};fail{inbox.list()}
        assertArrayEquals(corrupt,target.readBytes())
    }
    @Test fun interruptedAtomicReplacementAndAbandonedNewFilePreserveAcceptedBytes() {
        val raw=bytes();val accepted=inbox.accept(raw);val target=file(accepted.summary.sha256)
        val atomic=AtomicFile(target);val output=atomic.startWrite();output.write("partial".toByteArray());output.close()
        assertArrayEquals(raw,target.readBytes());assertEquals(listOf(accepted.summary),OnlinePendingRecoveryInbox(context).list())
        atomic.failWrite(null)
        val abandoned=AtomicFile(file("a".repeat(64)));val stream=abandoned.startWrite();stream.write("partial".toByteArray());stream.close()
        assertEquals(listOf(accepted.summary),inbox.list())
        abandoned.failWrite(null);assertArrayEquals(raw,target.readBytes())
    }
    @Test fun concurrentAcceptanceAcrossInstancesStoresOneFingerprint():Unit=runBlocking {
        val raw=bytes();val results=coroutineScope{(1..8).map{async(Dispatchers.IO){OnlinePendingRecoveryInbox(context).accept(raw)}}.awaitAll()}
        assertEquals(1,results.count{!it.duplicate});assertEquals(7,results.count{it.duplicate});assertEquals(1,inbox.list().size)
    }
    @Test fun fullInboxRejectsNewPacketAndKeepsAllExistingFilesButAllowsDuplicates() {
        val originals=(1..OnlinePendingRecoveryInbox.MAX_FILES).map{bytes().also{inbox.accept(it)}}
        val before=inbox.list();fail{inbox.accept(bytes())};assertEquals(before,inbox.list())
        assertTrue(inbox.accept(originals.first()).duplicate)
        for(raw in originals)assertArrayEquals(raw,file(OnlinePendingBackupCodec.decode(raw).sha256).readBytes())
    }
    @Test fun totalByteCapacityIncludesAbandonedAtomicFilesWithoutDeletingThem() {
        val existing=inbox.accept(bytes());val before=file(existing.summary.sha256).readBytes()
        val abandoned=File(folder,"online-recovery-inbox/${"b".repeat(64)}.json.new")
        java.io.RandomAccessFile(abandoned,"rw").use{it.setLength(OnlinePendingRecoveryInbox.MAX_TOTAL_BYTES)}
        fail{inbox.accept(bytes())};assertTrue(abandoned.exists())
        assertEquals(listOf(existing.summary),inbox.list());assertArrayEquals(before,file(existing.summary.sha256).readBytes())
    }
}
