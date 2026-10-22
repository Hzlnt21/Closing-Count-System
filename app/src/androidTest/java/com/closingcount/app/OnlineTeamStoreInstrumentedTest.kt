package com.closingcount.app
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.closingcount.app.data.online.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.security.MessageDigest
import java.util.UUID

class OnlineTeamStoreInstrumentedTest{
 private val context=ApplicationProvider.getApplicationContext<Context>()
 private fun op()=OnlineTeamOperation("https://qa.example.test|${UUID.randomUUID()}|${UUID.randomUUID()}",UUID.randomUUID().toString(),"CREATE_ACCOUNT",JSONObject().put("username","qa.worker").put("displayName","QA worker").put("role","EMPLOYEE").toString())
 private fun rejected(block:()->Unit){var denied=false;try{block()}catch(_:IllegalArgumentException){denied=true}catch(_:IllegalStateException){denied=true};assertTrue(denied)}
 @Test fun passwordFieldsAreRejectedAndOnlyNonSecretMetadataIsPersisted(){
  val store=OnlineTeamStore(context);val op=op()
  rejected{store.save(op.copy(payload=JSONObject(op.payload).put("password","MustNeverPersist!2026").toString()))}
  store.save(op);try{
   val hash=MessageDigest.getInstance("SHA-256").digest(op.scope.toByteArray()).joinToString(""){"%02x".format(it)}
   val raw=File(context.noBackupFilesDir,"online-team-operations/$hash.json").readText()
   assertFalse(raw.contains("password",true));assertTrue(raw.contains("qa.worker"));assertEquals(op,store.load(op.scope))
  }finally{store.remove(op.scope,op.operationId,true)}
 }
 @Test fun sentCommandSurvivesReopenAndCannotBeReplacedDiscardedOrReattributed(){
  val store=OnlineTeamStore(context);val op=op();store.save(op)
  try{val sent=op.copy(status="SENT",wasSent=true);store.save(sent)
   assertEquals(sent,OnlineTeamStore(context).load(op.scope));rejected{store.remove(op.scope,op.operationId)}
   rejected{store.save(sent.copy(payload=JSONObject(op.payload).put("role","ADMIN").toString()))};rejected{store.save(op)}
   assertEquals(1,store.forAccount(op.scope.substringBeforeLast('|')+"|").size);assertTrue(store.forAccount("https://other.example.test|").isEmpty())
   store.save(sent.copy(status="AUTH_REQUIRED",error="OUTLET_FORBIDDEN"));rejected{store.remove(op.scope,op.operationId)}
  }finally{store.remove(op.scope,op.operationId,true)}
 }
 @Test fun definitiveConflictCanBeDismissedWhileLegacyBackupRecoveryPreservesPending(){
  val store=OnlineTeamStore(context);val op=op();store.save(op)
  val hash=MessageDigest.getInstance("SHA-256").digest(op.scope.toByteArray()).joinToString(""){"%02x".format(it)}
  val base=File(context.noBackupFilesDir,"online-team-operations/$hash.json")
  try{assertTrue(base.renameTo(File(base.path+".bak")));assertEquals(listOf(op),store.forAccount(op.scope.substringBeforeLast('|')+"|"))
   store.save(op.copy(status="CONFLICT",error="MEMBERSHIP_CONFLICT"));store.remove(op.scope,op.operationId);assertNull(store.load(op.scope))
  }finally{store.remove(op.scope,op.operationId,true)}
 }
}
