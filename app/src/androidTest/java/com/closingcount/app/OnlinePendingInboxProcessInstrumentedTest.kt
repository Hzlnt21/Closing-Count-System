package com.closingcount.app

import android.content.Context
import android.content.ContextWrapper
import android.os.Process
import android.util.AtomicFile
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.closingcount.app.data.transfer.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID

/** Run explicitly twice, with an actual force-stop between stage and verify. */
@RunWith(AndroidJUnit4::class)
class OnlinePendingInboxProcessInstrumentedTest {
    @Test fun acceptedInboxSurvivesSeparateInstrumentationProcess() {
        val args=InstrumentationRegistry.getArguments();val mode=requireNotNull(args.getString("ccsInboxMode"))
        check(mode in setOf("stage","verify"));val run=requireNotNull(args.getString("ccsInboxQaRun"));check(UUID.fromString(run).toString()==run)
        val base=ApplicationProvider.getApplicationContext<Context>()
        val folder=File(base.cacheDir,"pending-inbox-process-qa-$run")
        val context=object:ContextWrapper(base){override fun getNoBackupFilesDir()=folder}
        val packet=OnlinePendingBackupCodec.encode(inboxPacket(run));val decoded=OnlinePendingBackupCodec.decode(packet)
        val inbox=OnlinePendingRecoveryInbox(context)
        if(mode=="stage") {
            check(!folder.exists());check(folder.mkdirs())
            assertFalse(inbox.accept(packet).duplicate)
            File(folder,"stage-pid").writeText(Process.myPid().toString())
            val partial=AtomicFile(File(folder,"online-recovery-inbox/${"f".repeat(64)}.json"))
            partial.startWrite().use{it.write("uncommitted partial".toByteArray())}
        } else {
            val oldPid=File(folder,"stage-pid").readText().toInt();assertNotEquals(oldPid,Process.myPid())
            assertEquals(listOf(OnlinePendingRecoveryInbox.summary(decoded)),inbox.list())
            assertEquals(decoded.model,inbox.read(decoded.sha256).model)
            assertTrue(inbox.accept(packet).duplicate)
            val actual=File(folder,"online-recovery-inbox/${decoded.sha256}.json").readBytes();assertArrayEquals(packet,actual)
            File(base.getExternalFilesDir(null),"pending-inbox-process-qa.json").writeBytes(actual)
            File(base.getExternalFilesDir(null),"pending-inbox-process-proof.json").writeText(JSONObject().put("run",run).put("sha256",decoded.sha256).put("bytes",actual.size)
                .put("stagePid",oldPid).put("verifyPid",Process.myPid()).put("duplicate",true).put("uncommittedPartialIgnored",true).toString())
            check(folder.parentFile==base.cacheDir&&folder.name=="pending-inbox-process-qa-$run");folder.deleteRecursively()
        }
    }
}
