package com.closingcount.app

import android.content.Context
import android.content.ContextWrapper
import android.database.sqlite.SQLiteDatabase
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
class OnlineQuantityRecoveryHttpInstrumentedTest {
    private val base=ApplicationProvider.getApplicationContext<Context>()
    private lateinit var folder:File
    private lateinit var context:Context
    private lateinit var db:ClosingCountDatabase
    private lateinit var store:OnlineSessionStore
    private lateinit var repo:OnlineRepository
    private lateinit var api:OnlineApi
    private lateinit var inbox:OnlinePendingRecoveryInbox
    private lateinit var recovery:OnlineQuantityRecovery
    private lateinit var outlet:String
    private lateinit var employee:String
    private lateinit var colleague:String
    private lateinit var admin:String
    private lateinit var password:String
    private val sessions=mutableListOf<OnlineSession>()
    private val name="ccs-quantity-recovery-${UUID.randomUUID()}.db"
    @Before fun setup()=runBlocking {
        val a=InstrumentationRegistry.getArguments();check(a.getString("ccsRecoveryQa")=="true")
        val origin=requireNotNull(a.getString("ccsQaOrigin"));check(origin=="http://10.0.2.2:4371")
        outlet=OnlineApi.uuid(requireNotNull(a.getString("ccsQaOutlet")));employee=requireNotNull(a.getString("ccsQaEmployee"))
        colleague=requireNotNull(a.getString("ccsQaColleague"));admin=requireNotNull(a.getString("ccsQaAdmin"));password=requireNotNull(a.getString("ccsQaPassword"))
        folder=File(base.cacheDir,"quantity-recovery-qa-${UUID.randomUUID()}").apply{mkdirs()}
        context=object:ContextWrapper(base){override fun getNoBackupFilesDir()=folder}
        api=OnlineApi(OnlineEndpoint(origin,true),4000);db=open();store=OnlineSessionStore(context,"quantity-recovery-qa-${UUID.randomUUID()}")
        repo=OnlineRepository(db.onlineSyncDao(),store);inbox=OnlinePendingRecoveryInbox(context);recovery=OnlineQuantityRecovery(inbox,repo)
    }
    private fun open()=Room.databaseBuilder(base,ClosingCountDatabase::class.java,name).addMigrations(Migration8To9,Migration9To10).build()
    private suspend fun login(user:String=employee)=api.login(user,password).also{sessions+=it}
    @After fun cleanup():Unit=runBlocking {
        if(::api.isInitialized)for(session in sessions)runCatching{api.logout(session)}
        if(::store.isInitialized)store.clear()
        if(::db.isInitialized)db.close();base.deleteDatabase(name)
        if(::folder.isInitialized){check(folder.parentFile==base.cacheDir&&folder.name.startsWith("quantity-recovery-qa-"));folder.deleteRecursively()}
    }
    private suspend fun reject(block:suspend()->Unit){assertNotNull(runCatching{block()}.exceptionOrNull())}
    private fun packet(session:OnlineSession,snapshot:OnlineSnapshot,quantity:Int=8,ids:List<String> =listOf(UUID.randomUUID().toString())):OnlinePendingBackup {
        val raw=JSONObject(snapshot.header.snapshotJson);val first=snapshot.entries.first();val menu=raw.getJSONObject("catalog").getJSONArray("menus").let{menus->
            (0 until menus.length()).map{menus.getJSONObject(it)}.first{it.getString("id")==first.menuId}}
        return OnlinePendingBackup(UUID.randomUUID().toString(),"2026-10-23T00:00:00Z",UUID.randomUUID().toString(),session.origin,
            PendingBackupAccount(session.identity.id,session.identity.username,session.identity.displayName),PendingBackupOutlet(outlet,"QA"),
            listOf(PendingBackupClosing(snapshot.header.closingId,raw.getString("businessDate"),snapshot.header.generation,snapshot.header.revision,snapshot.header.state,
                listOf(PendingBackupEntry(first.menuId,menu.getString("name"),first.serverQuantity,first.serverRevision,quantity,"PENDING")),
                ids.mapIndexed{i,id->PendingBackupQuantity(id,first.menuId,snapshot.header.generation,if(i==0)first.serverRevision else null,quantity,if(i==0)"SENT" else "QUEUED")},null)),emptyList())
    }
    private fun accept(model:OnlinePendingBackup)=inbox.accept(OnlinePendingBackupCodec.encode(model)).summary.sha256
    private suspend fun editRemote(session:OnlineSession,snapshot:OnlineSnapshot,quantity:Int):String {
        val id=UUID.randomUUID().toString();val e=snapshot.entries.first()
        api.send(session,OnlineOperationEntity(operationId=id,scope=session.scope(outlet),closingId=snapshot.header.closingId,
            menuId=e.menuId,generation=snapshot.header.generation,expectedRevision=e.serverRevision,quantity=quantity,status="SENT",errorCode=null));return id
    }
    @Test fun acknowledgedHeadKeepsDependentTailAndNewDraftDoesNotSendOrRelabelHistory():Unit=runBlocking {
        val a=login();val b=login(colleague);repo.activate(a)
        val initial=api.create(a,outlet,"2026-09-01");val id=initial.header.closingId
        val head=editRemote(a,initial,4);editRemote(b,api.fetch(b,outlet,id),6)
        val tail=UUID.randomUUID().toString();val rawModel=packet(a,initial,8,listOf(head,tail))
        val model=rawModel.copy(closings=rawModel.closings.map{c->c.copy(operations=c.operations.mapIndexed{i,op->if(i==0)op.copy(quantity=4) else op})});val fp=accept(model)
        val before=api.fetch(a,outlet,id);val review=recovery.prepare(fp,id)
        assertEquals(6,review.rows.single().serverQuantity);assertEquals(0,review.rows.single().fileBase);assertEquals("PROPOSAL",review.rows.single().disposition)
        assertNull(db.onlineSyncDao().closing(a.scope(outlet),id)) // preview did not pull into live cache
        val applied=recovery.apply(review.id);val newId=applied.newOperations.values.single();assertFalse(newId in listOf(head,tail))
        assertEquals(before.header.snapshotJson,api.fetch(a,outlet,id).header.snapshotJson)
        assertEquals(before.header.snapshotJson,db.onlineSyncDao().closing(a.scope(outlet),id)!!.snapshotJson)
        val operation=db.onlineSyncDao().operations(a.scope(outlet),id).single();assertEquals(2L,operation.expectedRevision);assertEquals("QUEUED",operation.status)
        reject{recovery.apply(review.id)}
        assertEquals(OnlineSyncResult.Synced,repo.sync(outlet,id));assertEquals(8,api.fetch(a,outlet,id).entries.first().serverQuantity)
        assertEquals(mapOf(head to true,tail to false,newId to true),api.operationStatuses(a,outlet,listOf(head,tail,newId)))
    }
    @Test fun alreadyRecordedReferencesSkipEvenWhenFileDesiredPayloadWasAltered():Unit=runBlocking {
        val a=login();repo.activate(a);val initial=api.create(a,outlet,"2026-09-02");val old=editRemote(a,initial,4)
        val owner=login(admin)
        for(action in listOf("submit","lock")) {
            val snapshot=api.fetch(owner,outlet,initial.header.closingId)
            api.lifecycle(owner,OnlineLifecycleEntity(owner.scope(outlet),initial.header.closingId,UUID.randomUUID().toString(),action,
                snapshot.header.generation,snapshot.header.revision,"","SENT",true,null))
        }
        val fp=accept(packet(a,initial,9,listOf(old)));val review=recovery.prepare(fp,initial.header.closingId)
        assertEquals("ALREADY_RECORDED",review.rows.single().disposition);assertEquals("LOCKED",review.serverState)
        assertTrue(recovery.apply(review.id).newOperations.isEmpty());assertTrue(db.onlineSyncDao().closingIds(a.scope(outlet)).isEmpty())
        assertEquals(4,api.fetch(a,outlet,initial.header.closingId).entries.first().serverQuantity)
    }
    @Test fun changedReviewRejectsThenPostApplyServerRaceUsesNormalRevisionConflict():Unit=runBlocking {
        val a=login();val b=login(colleague);repo.activate(a);val initial=api.create(a,outlet,"2026-09-03");val id=initial.header.closingId
        val fp=accept(packet(a,initial));val stale=recovery.prepare(fp,id);editRemote(b,initial,2)
        reject{recovery.apply(stale.id)};assertTrue(db.onlineSyncDao().operations(a.scope(outlet),id).isEmpty())
        val fresh=recovery.prepare(fp,id);recovery.apply(fresh.id);editRemote(b,api.fetch(b,outlet,id),3)
        assertEquals(OnlineSyncResult.Blocked("PULL_CONFLICT"),repo.sync(outlet,id))
        assertEquals(3,api.fetch(a,outlet,id).entries.first().serverQuantity);assertEquals(8,db.onlineSyncDao().entries(a.scope(outlet),id).first().localQuantity)
    }
    @Test fun localPendingAndSameDateConflictBlockAndDurableClaimsPreventSecondRecovery():Unit=runBlocking {
        val a=login();repo.activate(a);val initial=api.create(a,outlet,"2026-09-04");val id=initial.header.closingId;val model=packet(a,initial);val fp=accept(model)
        db.onlineSyncDao().pull(initial.header,initial.entries);repo.edit(outlet,id,initial.entries.first().menuId,1)
        reject{recovery.prepare(fp,id)};assertEquals(1,db.onlineSyncDao().operations(a.scope(outlet),id).size)
        assertEquals(OnlineSyncResult.Synced,repo.sync(outlet,id))
        val review=recovery.prepare(fp,id);val newId=recovery.apply(review.id).newOperations.values.single()
        val other=OnlineQuantityRecovery(inbox,repo)
        val readOnlyDuplicate=other.prepare(fp,id);assertEquals("ALREADY_RECOVERED_LOCALLY",readOnlyDuplicate.rows.single().disposition)
        assertTrue(other.apply(readOnlyDuplicate.id).newOperations.isEmpty())
        reject{other.prepare(accept(packet(a,initial,9)),id)}
        assertEquals(OnlineSyncResult.Synced,repo.sync(outlet,id));db.close();db=open();repo=OnlineRepository(db.onlineSyncDao(),store);repo.initialize();recovery=OnlineQuantityRecovery(inbox,repo)
        val duplicate=recovery.prepare(fp,id);assertEquals("ALREADY_RECOVERED_LOCALLY",duplicate.rows.single().disposition);assertTrue(recovery.apply(duplicate.id).newOperations.isEmpty())
        assertEquals(newId,db.onlineSyncDao().recoveryClaims(a.scope(outlet),model.closings.single().operations.map{it.operationId}).single().recoveredOperationId)
        val fakeId=UUID.randomUUID().toString();val raw=JSONObject(initial.header.snapshotJson).put("id",fakeId)
        db.onlineSyncDao().pull(initial.header.copy(closingId=fakeId,snapshotJson=raw.toString()),initial.entries.map{it.copy(closingId=fakeId)})
        reject{recovery.prepare(accept(packet(a,initial,9)),id)}
    }
    @Test fun identityDateMenuAndSessionScopeAreCheckedWithoutCreatingLiveData():Unit=runBlocking {
        val a=login();val b=login(colleague);val initial=api.create(a,outlet,"2026-09-05");val id=initial.header.closingId;val model=packet(a,initial)
        repo.activate(b);reject{recovery.prepare(accept(model),id)};repo.activate(a)
        reject{recovery.prepare(accept(model.copy(origin="https://other.example.test")),id)}
        reject{recovery.prepare(accept(model.copy(outlet=model.outlet.copy(id=UUID.randomUUID().toString()))),id)}
        reject{recovery.prepare(accept(model.copy(closings=model.closings.map{it.copy(date="2026-08-05")})),id)}
        val menu=UUID.randomUUID().toString();val missing=model.copy(closings=model.closings.map{c->c.copy(entries=c.entries.map{it.copy(menuId=menu)},operations=c.operations.map{it.copy(menuId=menu)})})
        reject{recovery.prepare(accept(missing),id)}
        val ready=recovery.prepare(accept(model),id);repo.activate(b);reject{recovery.apply(ready.id)}
        assertTrue(db.onlineSyncDao().closingIds(a.scope(outlet)).isEmpty());assertTrue(db.onlineSyncDao().closingIds(b.scope(outlet)).isEmpty())
    }
    @Test fun claimWriteFailureRollsBackCacheDraftAndProvenanceTogether():Unit=runBlocking {
        val a=login();repo.activate(a);val initial=api.create(a,outlet,"2026-09-06");val id=initial.header.closingId;val model=packet(a,initial);val fp=accept(model)
        val review=recovery.prepare(fp,id)
        db.openHelper.writableDatabase.execSQL("CREATE TRIGGER qa_recovery_fail BEFORE INSERT ON online_recovery_claims BEGIN SELECT RAISE(ABORT,'Explicit QA claim failure'); END")
        reject{recovery.apply(review.id)}
        assertNull(db.onlineSyncDao().closing(a.scope(outlet),id));assertTrue(db.onlineSyncDao().operations(a.scope(outlet),id).isEmpty())
        assertTrue(db.onlineSyncDao().recoveryClaims(a.scope(outlet),model.closings.single().operations.map{it.operationId}).isEmpty())
        db.openHelper.writableDatabase.execSQL("DROP TRIGGER qa_recovery_fail")
        assertEquals(1,recovery.apply(recovery.prepare(fp,id).id).newOperations.size)
    }
    @Test fun freshMembershipAndRevokedSessionDenyApplyWithoutErasingInbox():Unit=runBlocking {
        val a=login();val owner=login(admin);repo.activate(a);val initial=api.create(a,outlet,"2026-09-07");val id=initial.header.closingId;val fp=accept(packet(a,initial))
        val review=recovery.prepare(fp,id)
        suspend fun membership(active:Boolean){val member=api.member(owner,outlet,a.identity.id)
            val op=OnlineTeamOperation(owner.scope(outlet),UUID.randomUUID().toString(),"UPDATE_MEMBERSHIP",JSONObject().put("accountId",member.id).put("expectedRevision",member.revision).put("role","EMPLOYEE").put("active",active).toString(),"SENT",true)
            api.sendTeam(owner,op,null)
        }
        membership(false);try{reject{recovery.apply(review.id)}}finally{membership(true)}
        val fresh=recovery.prepare(fp,id);api.logout(a);reject{recovery.apply(fresh.id)}
        assertEquals(1,inbox.list().size);assertTrue(db.onlineSyncDao().closingIds(a.scope(outlet)).isEmpty())
    }
    @Test fun statusResponsesRejectWrongScopeOrderTypesAndExcessFields() {
        val ids=(1..100).map{UUID.randomUUID().toString()}
        fun valid()=JSONObject().put("outletId",outlet).put("items",JSONArray(ids.map{JSONObject().put("operationId",it).put("recorded",false)}))
        assertEquals(100,OnlineApi.parseOperationStatuses(valid(),outlet,ids).size)
        val bad=mutableListOf(valid().put("outletId",UUID.randomUUID().toString()),valid().put("extra",true),valid().put("items",JSONArray()))
        bad+=valid().also{it.getJSONArray("items").getJSONObject(0).put("recorded","false")}
        bad+=valid().also{it.getJSONArray("items").getJSONObject(0).put("operationId",ids[1])}
        bad+=valid().also{it.getJSONArray("items").getJSONObject(0).put("receipt",JSONObject())}
        for(value in bad)assertNotNull(runCatching{OnlineApi.parseOperationStatuses(value,outlet,ids)}.exceptionOrNull())
    }
    @Test fun actualVersion8MigrationKeepsLegacyAndPendingRowsAndAddsEmptyProvenance():Unit=runBlocking {
        db.close();val schema=JSONObject(InstrumentationRegistry.getInstrumentation().context.assets.open("com.closingcount.app.data.local.ClosingCountDatabase/8.json").bufferedReader().use{it.readText()}).getJSONObject("database")
        val old=SQLiteDatabase.openOrCreateDatabase(base.getDatabasePath(name),null)
        val entities=schema.getJSONArray("entities")
        for(i in 0 until entities.length()){val e=entities.getJSONObject(i);old.execSQL(e.getString("createSql").replace("\${TABLE_NAME}",e.getString("tableName")))
            val indexes=e.optJSONArray("indices")?:JSONArray();for(j in 0 until indexes.length())old.execSQL(indexes.getJSONObject(j).getString("createSql").replace("\${TABLE_NAME}",e.getString("tableName")))}
        val setup=schema.getJSONArray("setupQueries");for(i in 0 until setup.length())old.execSQL(setup.getString(i))
        old.execSQL("INSERT INTO app_metadata VALUES ('migration-recovery','preserve')")
        old.execSQL("INSERT INTO online_operations VALUES (1,'old-operation','qa-scope','closing','menu',1,0,8,'SENT',NULL)")
        old.version=8;old.close();db=open()
        assertEquals(10,db.openHelper.readableDatabase.version);assertEquals("preserve",db.backupDao().getMetadata().single().value)
        assertEquals(8,db.onlineSyncDao().operations("qa-scope","closing").single().quantity)
        assertTrue(db.onlineSyncDao().recoveryClaims("qa-scope",listOf("old-operation")).isEmpty())
    }
    @Test fun moreThan100OriginalIdsAreQueriedInBatchesAndKeptAsProvenance():Unit=runBlocking {
        val a=login();repo.activate(a);val initial=api.create(a,outlet,"2026-09-08");val ids=(1..150).map{UUID.randomUUID().toString()}
        val fp=accept(packet(a,initial,8,ids));val review=recovery.prepare(fp,initial.header.closingId)
        val generated=recovery.apply(review.id).newOperations.values.single();assertFalse(generated in ids)
        assertEquals(150,db.onlineSyncDao().recoveryClaims(a.scope(outlet),ids).size)
        assertEquals(0,api.fetch(a,outlet,initial.header.closingId).entries.first().serverQuantity)
    }
    @Test fun generationChangeRequiresFreshReviewAndRebaseUsesCurrentGeneration():Unit=runBlocking {
        val a=login();val owner=login(admin);repo.activate(a);val initial=api.create(a,outlet,"2026-09-09");val id=initial.header.closingId
        val fp=accept(packet(a,initial));val stale=recovery.prepare(fp,id)
        for(action in listOf("submit","lock","reopen")) {
            val snapshot=api.fetch(owner,outlet,id)
            api.lifecycle(owner,OnlineLifecycleEntity(owner.scope(outlet),id,UUID.randomUUID().toString(),action,snapshot.header.generation,
                snapshot.header.revision,if(action=="reopen")"QA deliberate correction" else "","SENT",true,null))
        }
        reject{recovery.apply(stale.id)}
        val fresh=recovery.prepare(fp,id);assertEquals(1L,fresh.fileGeneration);assertEquals(2L,fresh.serverGeneration)
        assertEquals(listOf(1L),fresh.rows.single().sourceGenerations)
        recovery.apply(fresh.id);assertEquals(2L,db.onlineSyncDao().operations(a.scope(outlet),id).single().generation)
        assertEquals(0,api.fetch(a,outlet,id).entries.first().serverQuantity)
    }
}
