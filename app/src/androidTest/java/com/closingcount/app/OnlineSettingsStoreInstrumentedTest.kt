package com.closingcount.app

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.closingcount.app.data.online.*
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.security.MessageDigest
import java.util.UUID

class OnlineSettingsStoreInstrumentedTest {
    private val context=ApplicationProvider.getApplicationContext<Context>()
    private fun op()=OnlineSettingsOperation("https://qa.example.test|${UUID.randomUUID()}|${UUID.randomUUID()}",UUID.randomUUID().toString(),0,"Kedai QA","Asia/Jakarta",0)
    private fun rejected(block:()->Unit){var denied=false;try{block()}catch(_:IllegalArgumentException){denied=true}catch(_:IllegalStateException){denied=true};assertTrue(denied)}
    @Test fun sentPayloadSurvivesStoreReopenAndCannotBeChangedOrDiscarded(){
        val store=OnlineSettingsStore(context);val initial=op();store.save(initial)
        try{
            rejected{store.save(initial.copy(name="Changed silently"))}
            rejected{store.save(initial.copy(status="SENT"))}
            val sent=initial.copy(status="SENT",wasSent=true);store.save(sent)
            assertEquals(sent,OnlineSettingsStore(context).load(sent.scope))
            rejected{store.remove(sent.scope,sent.operationId)};rejected{store.save(initial)}
            assertEquals(1,store.pendingForAccount(initial.scope.substringBeforeLast('|')+"|"))
            assertNull(store.load(initial.scope+"-other"))
        }finally{store.remove(initial.scope,initial.operationId,confirmed=true)}
    }
    @Test fun definitiveRejectionCanBeDismissedButAuthUncertaintyCannot(){
        val store=OnlineSettingsStore(context);val initial=op();store.save(initial)
        try{
            store.save(initial.copy(status="AUTH_REQUIRED",error="SESSION_REQUIRED"))
            rejected{store.remove(initial.scope,initial.operationId)}
            store.save(initial.copy(status="QUEUED"));store.save(initial.copy(status="CONFLICT",error="SETTINGS_CONFLICT"))
            store.remove(initial.scope,initial.operationId);assertNull(store.load(initial.scope))
        }finally{store.remove(initial.scope,initial.operationId,confirmed=true)}
    }
    @Test fun backupRecoveryCountsPendingIntentAndUncommittedNewFileDoesNotReplaceIt(){
        val store=OnlineSettingsStore(context);val initial=op();store.save(initial)
        val hash=MessageDigest.getInstance("SHA-256").digest(initial.scope.toByteArray()).joinToString(""){"%02x".format(it)}
        val base=File(context.noBackupFilesDir,"online-settings-operations/$hash.json")
        try{
            assertTrue(base.renameTo(File(base.path+".bak")))
            assertEquals(1,store.pendingForAccount(initial.scope.substringBeforeLast('|')+"|"));assertEquals(initial,store.load(initial.scope))
            File(base.path+".new").writeText("incomplete write")
            assertEquals(initial,OnlineSettingsStore(context).load(initial.scope))
        }finally{store.remove(initial.scope,initial.operationId,confirmed=true)}
    }
}
