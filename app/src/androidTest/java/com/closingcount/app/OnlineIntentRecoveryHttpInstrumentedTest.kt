package com.closingcount.app

import android.content.Context
import android.content.ContextWrapper
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.closingcount.app.data.local.*
import com.closingcount.app.data.online.*
import com.closingcount.app.data.transfer.*
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class OnlineIntentRecoveryHttpInstrumentedTest {
    private val base=ApplicationProvider.getApplicationContext<Context>()
    private val name="intent-recovery-qa-${UUID.randomUUID()}.db"
    private lateinit var folder:File;private lateinit var context:Context;private lateinit var db:ClosingCountDatabase
    private lateinit var sessions:OnlineSessionStore;private lateinit var repo:OnlineRepository
    private lateinit var inbox:OnlinePendingRecoveryInbox;private lateinit var recovery:OnlineIntentRecovery
    private lateinit var api:OnlineApi;private lateinit var outlet:String;private lateinit var password:String
    private lateinit var owner:OnlineSession;private lateinit var backup:OnlineSession;private lateinit var employee:OnlineSession;private lateinit var colleague:OnlineSession
    private val logins=mutableListOf<OnlineSession>()
    @Before fun setup():Unit=runBlocking {
        val a=InstrumentationRegistry.getArguments();check(a.getString("ccsIntentQa")=="true")
        val origin=requireNotNull(a.getString("ccsQaOrigin"));check(origin=="http://10.0.2.2:4371")
        outlet=OnlineApi.uuid(requireNotNull(a.getString("ccsQaAdminOutlet")));password=requireNotNull(a.getString("ccsQaPassword"))
        api=OnlineApi(OnlineEndpoint(origin,true),4000)
        suspend fun login(key:String)=api.login(requireNotNull(a.getString(key)),password).also{logins+=it}
        owner=login("ccsQaAdmin");backup=login("ccsQaBackupAdmin");employee=login("ccsQaEmployee");colleague=login("ccsQaColleague")
        folder=File(base.cacheDir,"intent-recovery-qa-${UUID.randomUUID()}").apply{mkdirs()}
        context=object:ContextWrapper(base){override fun getNoBackupFilesDir()=folder}
        db=Room.databaseBuilder(base,ClosingCountDatabase::class.java,name).addMigrations(Migration9To10).build()
        sessions=OnlineSessionStore(context,"intent-recovery-qa-${UUID.randomUUID()}")
        repo=OnlineRepository(db.onlineSyncDao(),sessions,OnlineSettingsStore(context),OnlineTeamStore(context),OnlineCatalogStore(context));repo.activate(owner)
        inbox=OnlinePendingRecoveryInbox(context);recovery=OnlineIntentRecovery(inbox,repo)
    }
    @After fun cleanup():Unit=runBlocking {
        if(::api.isInitialized)for(login in logins)runCatching{api.logout(login)}
        if(::sessions.isInitialized)sessions.clear();if(::db.isInitialized)db.close();base.deleteDatabase(name)
        if(::folder.isInitialized){check(folder.parentFile==base.cacheDir&&folder.name.startsWith("intent-recovery-qa-"));folder.deleteRecursively()}
    }
    private suspend fun reject(block:suspend()->Unit){assertNotNull(runCatching{block()}.exceptionOrNull())}
    private fun model(session:OnlineSession=owner,admin:List<PendingBackupAdmin> =emptyList(),closings:List<PendingBackupClosing> =emptyList())=
        OnlinePendingBackup(UUID.randomUUID().toString(),"2026-10-23T00:00:00Z",UUID.randomUUID().toString(),session.origin,
            PendingBackupAccount(session.identity.id,session.identity.username,session.identity.displayName),PendingBackupOutlet(outlet,"QA admin outlet"),closings,admin)
    private fun intent(kind:String,payload:JSONObject,revision:Long?=0)=PendingBackupAdmin(kind,UUID.randomUUID().toString(),if(kind=="TEAM_CREATE")null else revision,"SENT",true,payload.toString())
    private fun accept(model:OnlinePendingBackup)=inbox.accept(OnlinePendingBackupCodec.encode(model)).summary.sha256
    private fun settingPayload(name:String)=JSONObject().put("cutoffMinutes",60).put("timezone","Asia/Jakarta").put("name",name)
    private suspend fun membership(actor:OnlineSession,id:String,role:String,active:Boolean=true) {
        val m=api.member(actor,outlet,id);api.sendTeam(actor,OnlineTeamOperation(actor.scope(outlet),UUID.randomUUID().toString(),"UPDATE_MEMBERSHIP",
            JSONObject().put("accountId",id).put("expectedRevision",m.revision).put("role",role).put("active",active).toString(),"SENT",true),null)
    }
    @Test fun recoveredSettingsUseFreshBaseAndNormalExplicitSyncAndRemainExportable():Unit=runBlocking {
        val before=api.outletSettings(owner,outlet);val source=intent("SETTINGS",settingPayload("Recovered settings QA"))
        val fp=accept(model(admin=listOf(source)));val review=recovery.prepareAdmin(fp,source.operationId)
        assertEquals("READY",review.disposition);assertEquals(before.revision,review.currentRevision)
        val applied=recovery.apply(review.id);val id=requireNotNull(applied.newOperationId);assertNotEquals(source.operationId,id)
        val pending=requireNotNull(repo.pendingSettings(outlet));assertEquals(before.revision,pending.expectedRevision);assertFalse(pending.wasSent)
        assertEquals(before,api.outletSettings(owner,outlet))
        val export=repo.capturePendingBackup(owner,outlet,UUID.randomUUID().toString());assertEquals(id,export.admin.single().operationId)
        assertEquals("Recovered settings QA",JSONObject(export.admin.single().payloadJson).getString("name"))
        val after=repo.syncSettings(outlet);assertEquals(before.revision+1,after.revision);assertEquals("Recovered settings QA",after.name)
        assertNull(repo.pendingSettings(outlet));assertFalse(db.onlineSyncDao().recoveredAdminReference(owner.scope(outlet),source.operationId)!!.active)
        val duplicate=recovery.prepareAdmin(fp,source.operationId);assertEquals("ALREADY_RECOVERED_LOCALLY",duplicate.disposition);assertNull(recovery.apply(duplicate.id).newOperationId)
        assertEquals(mapOf(source.operationId to false,id to true),api.operationStatuses(owner,outlet,listOf(source.operationId,id)))
    }
    @Test fun accountCreationPreservesPayloadAndRequiresNewPasswordOutsideStoredDraft():Unit=runBlocking {
        val username="recovery."+UUID.randomUUID().toString().take(12)
        val source=intent("TEAM_CREATE",JSONObject().put("role","EMPLOYEE").put("displayName","Recovered person QA").put("username",username))
        val fp=accept(model(admin=listOf(source)));val review=recovery.prepareAdmin(fp,source.operationId);assertTrue(review.passwordRequired)
        val id=requireNotNull(recovery.apply(review.id).newOperationId);val before=requireNotNull(repo.pendingTeam(outlet));assertEquals("CREATE_ACCOUNT",before.kind)
        assertEquals(username,JSONObject(before.payload).getString("username"));reject{repo.syncTeam(outlet)};assertFalse(repo.pendingTeam(outlet)!!.wasSent)
        val chosenPassword="New recovery QA!2026";assertFalse(before.payload.contains(chosenPassword));assertFalse(before.payload.contains("password",true))
        val created=repo.syncTeam(outlet,chosenPassword);assertEquals(username,created.member.username);assertEquals("Recovered person QA",created.member.name)
        assertEquals("EMPLOYEE",created.member.role);assertNull(repo.pendingTeam(outlet))
        val stored=db.onlineSyncDao().recoveredAdminReference(owner.scope(outlet),source.operationId)!!
        assertFalse(stored.payloadJson.contains(chosenPassword));assertFalse(stored.payloadJson.contains("password",true))
        assertEquals(mapOf(source.operationId to false,id to true),api.operationStatuses(owner,outlet,listOf(source.operationId,id)))
        assertEquals("ALREADY_RECOVERED_LOCALLY",recovery.prepareAdmin(fp,source.operationId).disposition)
    }
    @Test fun membershipRecoveryRebasesReviewedMemberAndUsesNormalPermissionGuards():Unit=runBlocking {
        val before=api.member(owner,outlet,colleague.identity.id);val desired=if(before.role=="SUPERVISOR")"EMPLOYEE" else "SUPERVISOR"
        try {
            val source=intent("TEAM_UPDATE",JSONObject().put("active",true).put("role",desired).put("accountId",before.id))
            val fp=accept(model(admin=listOf(source)));val review=recovery.prepareAdmin(fp,source.operationId)
            val id=requireNotNull(recovery.apply(review.id).newOperationId);val pending=requireNotNull(repo.pendingTeam(outlet))
            assertEquals(before.revision,JSONObject(pending.payload).getLong("expectedRevision"));assertEquals(before,api.member(owner,outlet,before.id))
            val result=repo.syncTeam(outlet);assertEquals(desired,result.member.role);assertTrue(result.member.active)
            assertEquals(before.id,result.member.id);assertEquals(id,db.onlineSyncDao().recoveredAdminReference(owner.scope(outlet),source.operationId)!!.operationId)
            assertEquals("ALREADY_RECOVERED_LOCALLY",recovery.prepareAdmin(fp,source.operationId).disposition)
        }finally{membership(owner,before.id,before.role,before.active)}
    }
    @Test fun catalogRecoveryRetainsWholeCatalogAsEditableThenFreezesAndPublishesNormally():Unit=runBlocking {
        val before=api.catalog(owner,outlet);val desired=before.catalog.copy(menus=before.catalog.menus.map{it.copy(name="Recovered "+it.name)}).validated()
        val normal=desired.json();val reordered=JSONObject().put("menus",normal.getJSONArray("menus")).put("ingredients",normal.getJSONArray("ingredients"))
            .put("menuCategories",normal.getJSONArray("menuCategories")).put("ingredientCategories",normal.getJSONArray("ingredientCategories"))
        val source=intent("CATALOG",reordered);val fp=accept(model(admin=listOf(source)));val review=recovery.prepareAdmin(fp,source.operationId)
        val id=requireNotNull(recovery.apply(review.id).newOperationId);val editing=requireNotNull(repo.pendingCatalog(outlet))
        assertEquals("EDITING",editing.status);assertEquals(before.revision,editing.expectedRevision);assertEquals(desired,OnlineCatalog.parse(JSONObject(editing.catalogJson)))
        assertEquals(before,api.catalog(owner,outlet));repo.prepareCatalog(outlet,before.revision,desired)
        assertEquals(id,repo.pendingCatalog(outlet)!!.operationId);val published=repo.syncCatalog(outlet)
        assertEquals(before.revision+1,published.revision);assertEquals(desired,published.catalog);assertNull(repo.pendingCatalog(outlet))
        assertEquals("ALREADY_RECOVERED_LOCALLY",recovery.prepareAdmin(fp,source.operationId).disposition)
    }
    private fun lifecycleModel(session:OnlineSession,snapshot:OnlineSnapshot,action:String):OnlinePendingBackup {
        val reason=if(action in setOf("return","reopen"))"Recovered correction QA" else ""
        val life=PendingBackupLifecycle(UUID.randomUUID().toString(),action,snapshot.header.generation,snapshot.header.revision,reason,"SENT",true)
        val closing=PendingBackupClosing(snapshot.header.closingId,JSONObject(snapshot.header.snapshotJson).getString("businessDate"),snapshot.header.generation,
            snapshot.header.revision,snapshot.header.state,emptyList(),emptyList(),life)
        return model(session,closings=listOf(closing))
    }
    @Test fun everyLifecycleActionRecoversIntoNormalQueuedReviewAndExplicitSync():Unit=runBlocking {
        val initial=api.create(owner,outlet,"2026-08-01");val id=initial.header.closingId
        for(action in listOf("submit","return","submit","lock","reopen")) {
            val before=api.fetch(owner,outlet,id);val packet=lifecycleModel(owner,before,action);val original=packet.closings.single().lifecycle!!.operationId
            val fp=accept(packet);val review=recovery.prepareLifecycle(fp,id);assertEquals("READY",review.disposition)
            val freshId=requireNotNull(recovery.apply(review.id).newOperationId);assertNotEquals(original,freshId)
            val local=db.onlineSyncDao().lifecycle(owner.scope(outlet),id)!!;assertEquals(action,local.action);assertEquals("QUEUED",local.status);assertFalse(local.wasSent)
            assertEquals(OnlineSyncResult.Blocked("RECOVERY_REVIEW_PENDING"),repo.syncAutomatically(outlet,id))
            assertEquals(before.header,api.fetch(owner,outlet,id).header)
            assertEquals(OnlineSyncResult.Synced,repo.sync(outlet,id));assertNull(db.onlineSyncDao().lifecycle(owner.scope(outlet),id))
            assertEquals("ALREADY_RECOVERED_LOCALLY",recovery.prepareLifecycle(fp,id).disposition)
            assertEquals(freshId,db.onlineSyncDao().recoveryClaims(owner.scope(outlet),listOf(original)).single().recoveredOperationId)
            assertEquals(mapOf(original to false,freshId to true),api.operationStatuses(owner,outlet,listOf(original,freshId)))
        }
    }
    @Test fun changedRoleBaseAndRevokedSessionDenyWhileRecordedReferenceRemainsReadOnly():Unit=runBlocking {
        val current=api.outletSettings(owner,outlet);val committedId=UUID.randomUUID().toString()
        api.updateSettings(owner,OnlineSettingsOperation(owner.scope(outlet),committedId,current.revision,"Committed QA","Asia/Jakarta",10,"SENT",true))
        val already=intent("SETTINGS",settingPayload("Untrusted changed file payload")).copy(operationId=committedId)
        val recordedFile=accept(model(admin=listOf(already)));val source=intent("SETTINGS",settingPayload("Later reviewed QA"));val fp=accept(model(admin=listOf(source)))
        val old=recovery.prepareAdmin(fp,source.operationId)
        membership(backup,owner.identity.id,"SUPERVISOR")
        try {
            reject{recovery.apply(old.id)};val done=recovery.prepareAdmin(recordedFile,committedId)
            assertEquals("ALREADY_RECORDED",done.disposition);assertNull(recovery.apply(done.id).newOperationId)
            val blocked=recovery.prepareAdmin(fp,source.operationId);assertEquals("BLOCKED_ROLE",blocked.disposition);reject{recovery.apply(blocked.id)}
        }finally{membership(backup,owner.identity.id,"ADMIN")}
        val fresh=recovery.prepareAdmin(fp,source.operationId);val settings=api.outletSettings(backup,outlet)
        api.updateSettings(backup,OnlineSettingsOperation(backup.scope(outlet),UUID.randomUUID().toString(),settings.revision,"Changed after review QA","Asia/Jakarta",20,"SENT",true))
        reject{recovery.apply(fresh.id)};assertNull(repo.pendingSettings(outlet))
        api.logout(owner);reject{recovery.prepareAdmin(fp,source.operationId)};assertEquals(2,inbox.list().size)
    }
    @Test fun cancelledReviewUsernameConflictAndExistingDraftDoNotCreateRecoveryActions():Unit=runBlocking {
        val create=intent("TEAM_CREATE",JSONObject().put("username",employee.identity.username).put("displayName","Existing QA").put("role","EMPLOYEE"))
        val taken=recovery.prepareAdmin(accept(model(admin=listOf(create))),create.operationId);assertEquals("BLOCKED_USERNAME",taken.disposition);reject{recovery.apply(taken.id)}
        val source=intent("SETTINGS",settingPayload("Cancelled QA"));val fp=accept(model(admin=listOf(source)));val cancelled=recovery.prepareAdmin(fp,source.operationId)
        recovery.discard(cancelled.id);reject{recovery.apply(cancelled.id)}
        val stale=recovery.prepareAdmin(fp,source.operationId);val before=api.outletSettings(owner,outlet)
        repo.prepareSettings(outlet,"Existing local QA","Asia/Jakarta",0,before.revision)
        reject{recovery.apply(stale.id)};assertEquals("Existing local QA",repo.pendingSettings(outlet)!!.name)
        assertNull(db.onlineSyncDao().recoveredAdminReference(owner.scope(outlet),source.operationId));assertEquals(before,api.outletSettings(owner,outlet))
    }
    @Test fun wrongActorAndLifecycleStateOrRoleAreExplicitlyBlocked():Unit=runBlocking {
        val initial=api.create(employee,outlet,"2026-08-02");val id=initial.header.closingId
        val employeeFile=accept(lifecycleModel(employee,initial,"lock"));reject{recovery.prepareLifecycle(employeeFile,id)}
        repo.activate(employee,true);val role=recovery.prepareLifecycle(employeeFile,id);assertEquals("BLOCKED_ROLE",role.disposition);reject{recovery.apply(role.id)}
        repo.activate(owner,true);val state=recovery.prepareLifecycle(accept(lifecycleModel(owner,initial,"reopen")),id)
        assertEquals("BLOCKED_STATE",state.disposition);reject{recovery.apply(state.id)}
        assertNull(db.onlineSyncDao().lifecycle(owner.scope(outlet),id));assertNull(db.onlineSyncDao().lifecycle(employee.scope(outlet),id))
        assertEquals("DRAFT",api.fetch(owner,outlet,id).header.state)
    }
}
