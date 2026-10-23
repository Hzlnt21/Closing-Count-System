package com.closingcount.app

import android.content.Context
import android.content.ContextWrapper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.closingcount.app.data.transfer.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class OnlinePendingInboxManagementInstrumentedTest {
    private val base=ApplicationProvider.getApplicationContext<Context>()
    private lateinit var folder:File;private lateinit var context:Context;private lateinit var inbox:OnlinePendingRecoveryInbox
    @Before fun setup(){folder=File(base.cacheDir,"inbox-management-qa-${UUID.randomUUID()}").apply{mkdirs()}
        context=object:ContextWrapper(base){override fun getNoBackupFilesDir()=folder};inbox=OnlinePendingRecoveryInbox(context)
        File(folder,"existing-draft-sentinel").writeText("preserve");File(folder,"existing-session-sentinel").writeText("preserve")}
    @After fun cleanup(){assertEquals("preserve",File(folder,"existing-draft-sentinel").readText());assertEquals("preserve",File(folder,"existing-session-sentinel").readText())
        check(folder.parentFile==base.cacheDir&&folder.name.startsWith("inbox-management-qa-"));folder.deleteRecursively()}
    private fun accept()=inbox.accept(OnlinePendingBackupCodec.encode(inboxPacket())).summary
    @Test fun inventoryShowsCorruptAndIncompleteFilesWithoutHidingValidRecovery(){
        val good=accept();val bad=File(folder,"online-recovery-inbox/${"b".repeat(64)}.json").apply{writeText("broken")}
        File(folder,"online-recovery-inbox/${"c".repeat(64)}.json.new").writeText("partial")
        val rows=inbox.inventory();assertEquals(3,rows.size);assertEquals(good,rows.single{it.fingerprint==good.sha256}.summary)
        assertEquals("UNREADABLE",rows.single{it.fingerprint=="b".repeat(64)}.problem);assertEquals("INCOMPLETE",rows.single{it.fingerprint=="c".repeat(64)}.problem)
        assertEquals("broken",bad.readText());assertNotNull(runCatching{inbox.list()}.exceptionOrNull())
    }
    @Test fun cancelledAndStaleRemovalCannotDeleteChangedOrOtherFiles(){
        val first=accept();val second=accept();val cancelled=inbox.prepareRemoval(first.sha256);inbox.cancelRemoval(cancelled.id)
        assertNotNull(runCatching{inbox.remove(cancelled.id)}.exceptionOrNull())
        val stale=inbox.prepareRemoval(first.sha256);val changed=File(folder,"online-recovery-inbox/${first.sha256}.json");changed.appendText("changed")
        assertNotNull(runCatching{inbox.remove(stale.id)}.exceptionOrNull());assertTrue(changed.exists());assertEquals(second,inbox.read(second.sha256).let{OnlinePendingRecoveryInbox.summary(it)})
    }
    @Test fun explicitOneUseRemovalDeletesOnlySelectedPrivateCopy(){
        val first=accept();val second=accept();val review=inbox.prepareRemoval(first.sha256)
        assertEquals(first,review.summary);assertEquals(first.sha256,inbox.remove(review.id));assertNotNull(runCatching{inbox.remove(review.id)}.exceptionOrNull())
        assertEquals(listOf(second),inbox.list())
        assertNotNull(runCatching{inbox.prepareRemoval("../escape")}.exceptionOrNull())
    }
    @Test fun unreadableAndAbandonedCopiesCanBeRemovedWithoutRepairingTheirPayload(){
        val good=accept();val corrupt="d".repeat(64);val partial="e".repeat(64)
        File(folder,"online-recovery-inbox/$corrupt.json").writeText("broken")
        File(folder,"online-recovery-inbox/$partial.json.new").writeText("partial")
        for(hash in listOf(corrupt,partial)){val review=inbox.prepareRemoval(hash);assertNull(review.summary);inbox.remove(review.id)}
        assertEquals(listOf(good),inbox.list());assertEquals(1,inbox.inventory().size)
    }
}
