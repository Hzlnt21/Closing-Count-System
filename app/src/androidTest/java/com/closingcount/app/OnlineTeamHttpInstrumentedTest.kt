package com.closingcount.app
import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.closingcount.app.data.local.ClosingCountDatabase
import com.closingcount.app.data.online.*
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

class OnlineTeamHttpInstrumentedTest{
 @Test fun realCreateRecoveryReviewedConflictAndOwnAccessReceiptAfterRelogin()=runBlocking{
  val args=InstrumentationRegistry.getArguments();org.junit.Assume.assumeTrue("Owned team QA only",args.getString("ccsTeamQa")=="true")
  require(args.getString("ccsQaOrigin")=="http://10.0.2.2:4371")
  val context=ApplicationProvider.getApplicationContext<Context>();val outlet=requireNotNull(args.getString("ccsQaOutlet"));val password=requireNotNull(args.getString("ccsQaPassword"))
  val admin=requireNotNull(args.getString("ccsQaAdmin"));val api=OnlineApi(OnlineEndpoint("http://10.0.2.2:4371",true));val a=api.login(admin,password)
  val name="team-qa-${UUID.randomUUID()}.db";val db=Room.databaseBuilder(context,ClosingCountDatabase::class.java,name).build();val storage=OnlineTeamStore(context);val sessionStore=OnlineSessionStore(context)
  var repo=OnlineRepository(db.onlineSyncDao(),sessionStore,null,storage);val sessions=mutableListOf(a)
  try{
   repo.activate(a);val username=admin.substringBeforeLast('.')+".http-worker"
   repo.prepareTeamCreate(outlet,username,"HTTP worker","EMPLOYEE")
   var guarded=false;try{repo.logout()}catch(_:PendingOnlineDraftsException){guarded=true};assertTrue(guarded)
   var needsPassword=false;try{repo.syncTeam(outlet)}catch(_:TeamPasswordRequiredException){needsPassword=true};assertTrue(needsPassword)
   val queued=repo.pendingTeam(outlet)!!;assertFalse(queued.payload.contains("password",true))
   val sent=queued.copy(status="SENT",wasSent=true);storage.save(sent);val committed=api.sendTeam(a,sent,password)
   repo=OnlineRepository(db.onlineSyncDao(),OnlineSessionStore(context),null,OnlineTeamStore(context));repo.initialize()
   assertEquals(committed,repo.syncTeam(outlet));assertNull(repo.pendingTeam(outlet))
   val member=committed.member;repo.prepareTeamEdit(outlet,member,"SUPERVISOR",false)
   val other=OnlineTeamOperation(a.scope(outlet),UUID.randomUUID().toString(),"UPDATE_MEMBERSHIP",JSONObject().put("accountId",member.id).put("expectedRevision",0).put("role","SUPERVISOR").put("active",true).toString(),"SENT",true)
   api.sendTeam(a,other,null);var conflict=false;try{repo.syncTeam(outlet)}catch(_:OnlineConflictChangedException){conflict=true};assertTrue(conflict)
   assertEquals("CONFLICT",repo.pendingTeam(outlet)!!.status);repo.dismissTeam(outlet)
   repo.prepareTeamEdit(outlet,api.member(a,outlet,member.id),"SUPERVISOR",false);assertEquals(2L,repo.syncTeam(outlet).member.revision)
   val backup=OnlineTeamOperation(a.scope(outlet),UUID.randomUUID().toString(),"CREATE_ACCOUNT",JSONObject().put("username",admin.substringBeforeLast('.')+".backup").put("displayName","Backup Admin").put("role","ADMIN").toString(),"SENT",true)
   api.sendTeam(a,backup,password)
   repo.prepareTeamEdit(outlet,api.member(a,outlet,a.identity.id),"EMPLOYEE",false)
   val own=repo.pendingTeam(outlet)!!.copy(status="SENT",wasSent=true);storage.save(own);api.sendTeam(a,own,null)
   val relogged=api.login(admin,password);sessions.add(relogged);assertTrue(relogged.identity.outlets.none{it.id==outlet})
   val recovered=OnlineRepository(db.onlineSyncDao(),OnlineSessionStore(context),null,OnlineTeamStore(context));recovered.activate(relogged)
   assertEquals(1,recovered.pendingOwnTeamAccessCount());assertEquals(1,recovered.reconcileOwnTeamAccess());assertEquals(0,recovered.pendingOwnTeamAccessCount());assertEquals(0,recovered.pendingCount())
   assertTrue(recovered.logout())
  }finally{storage.load(a.scope(outlet))?.let{storage.remove(it.scope,it.operationId,true)};sessions.forEach{try{api.logout(it)}catch(_:java.io.IOException){}};sessionStore.clear();db.close();context.deleteDatabase(name)}
 }
}
