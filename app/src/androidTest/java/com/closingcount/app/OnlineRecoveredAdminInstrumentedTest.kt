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
import java.time.Instant
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class OnlineRecoveredAdminInstrumentedTest {
    private val base=ApplicationProvider.getApplicationContext<Context>()
    private val name="recovered-admin-qa-${UUID.randomUUID()}.db"
    private val outlet=UUID.randomUUID().toString();private val account=UUID.randomUUID().toString()
    private val session=OnlineSession("https://closing.example.test",OnlineIdentity(account,"rani.qa","Rani QA",listOf(OnlineOutlet(outlet,"Outlet QA","ADMIN"))),"NEVER_EXPORT_RECOVERY_QA_TOKEN",Instant.EPOCH)
    private val scope get()=session.scope(outlet)
    private lateinit var folder:File;private lateinit var context:Context;private lateinit var db:ClosingCountDatabase
    private lateinit var settings:OnlineSettingsStore;private lateinit var team:OnlineTeamStore;private lateinit var catalog:OnlineCatalogStore
    private lateinit var sessions:OnlineSessionStore;private lateinit var repo:OnlineRepository;private lateinit var overlay:OnlineRecoveredAdminStore
    private fun open()=Room.databaseBuilder(base,ClosingCountDatabase::class.java,name).addMigrations(Migration9To10).build()
    private fun connect(){overlay=OnlineRecoveredAdminStore(db.onlineSyncDao(),settings,team,catalog);repo=OnlineRepository(db.onlineSyncDao(),sessions,settings,team,catalog)}
    @Before fun setup():Unit=runBlocking {
        folder=File(base.cacheDir,"recovered-admin-qa-${UUID.randomUUID()}").apply{mkdirs()}
        context=object:ContextWrapper(base){override fun getNoBackupFilesDir()=folder}
        settings=OnlineSettingsStore(context);team=OnlineTeamStore(context);catalog=OnlineCatalogStore(context)
        sessions=OnlineSessionStore(context,"recovered-admin-qa-${UUID.randomUUID()}");db=open();connect();repo.activate(session)
    }
    @After fun cleanup() {
        if(::sessions.isInitialized)sessions.clear();if(::db.isInitialized)db.close();base.deleteDatabase(name)
        if(::folder.isInitialized){check(folder.parentFile==base.cacheDir&&folder.name.startsWith("recovered-admin-qa-"));folder.deleteRecursively()}
    }
    private suspend fun reject(block:suspend()->Unit){assertNotNull(runCatching{block()}.exceptionOrNull())}
    private fun row(kind:String,payload:String,revision:Long?=2)=OnlineRecoveredAdminEntity(scope,UUID.randomUUID().toString(),"a".repeat(64),
        when(kind){"TEAM_CREATE","TEAM_UPDATE"->"TEAM";else->kind},kind,UUID.randomUUID().toString(),if(kind=="TEAM_CREATE")null else revision,payload,
        if(kind=="CATALOG")"EDITING" else "QUEUED",false,null,true,"2026-10-23T00:00:00Z")
    private fun settingsRow()=row("SETTINGS",JSONObject().put("cutoffMinutes",60).put("timezone","Asia/Jakarta").put("name","Recovered QA").toString())
    private fun teamRow()=row("TEAM_CREATE",JSONObject().put("role","EMPLOYEE").put("displayName","Dimas QA").put("username","dimas.qa").toString())
    private fun catalogRow():OnlineRecoveredAdminEntity {
        val raw=JSONObject().put("menus",JSONArray()).put("ingredients",JSONArray()).put("menuCategories",JSONArray()).put("ingredientCategories",JSONArray())
        return row("CATALOG",raw.toString())
    }
    @Test fun threeRecoveredTypesUseNormalPendingViewsAndCompleteOfflineExportWithoutSecrets():Unit=runBlocking {
        val rows=listOf(settingsRow(),teamRow(),catalogRow());rows.forEach{overlay.attach(it)}
        assertEquals(3,repo.pendingCount());assertEquals("Recovered QA",repo.pendingSettings(outlet)!!.name)
        assertEquals("CREATE_ACCOUNT",repo.pendingTeam(outlet)!!.kind);assertEquals("EDITING",repo.pendingCatalog(outlet)!!.status)
        val packet=repo.capturePendingBackup(session,outlet,UUID.randomUUID().toString());val bytes=OnlinePendingBackupCodec.encode(packet)
        val restored=OnlinePendingBackupCodec.decode(bytes).model
        assertEquals(setOf("SETTINGS","TEAM_CREATE","CATALOG"),restored.admin.map{it.kind}.toSet())
        assertEquals(rows.map{it.operationId}.toSet(),restored.admin.map{it.operationId}.toSet())
        for(row in rows)assertFalse(String(bytes,Charsets.UTF_8).contains(row.originalOperationId))
        assertFalse(String(bytes,Charsets.UTF_8).contains(session.token));assertFalse(String(bytes,Charsets.UTF_8).contains("\"password\""))
        assertNull(settings.load(scope));assertNull(team.load(scope));assertNull(catalog.load(scope))
    }
    @Test fun originalReferenceAndGroupGuardsDoNotReplaceAnExistingAction():Unit=runBlocking {
        val first=settingsRow();overlay.attach(first)
        reject{overlay.attach(first.copy(operationId=UUID.randomUUID().toString()))}
        reject{overlay.attach(settingsRow())};assertEquals(first.operationId,overlay.settings(scope)!!.operationId)
        reject{db.onlineSyncDao().requireQuantityReferenceScope(scope,listOf(first.originalOperationId))}
        assertEquals(first.originalOperationId,db.onlineSyncDao().recoveredAdminReference(scope,first.originalOperationId)!!.originalOperationId)
    }
    @Test fun frozenPayloadAndSentMonotonicityMatchNormalTeamWorkflow():Unit=runBlocking {
        val target=UUID.randomUUID().toString();val first=row("TEAM_UPDATE",JSONObject().put("active",true).put("role","SUPERVISOR").put("accountId",target).toString(),7)
        overlay.attach(first);val before=requireNotNull(repo.pendingTeam(outlet));assertEquals(7L,JSONObject(before.payload).getLong("expectedRevision"))
        overlay.save(before.copy(status="SENT",wasSent=true));val sent=requireNotNull(overlay.team(scope))
        reject{overlay.save(sent.copy(status="QUEUED",wasSent=false))}
        reject{overlay.save(sent.copy(payload=JSONObject(sent.payload).put("role","ADMIN").toString()))}
        assertEquals(sent,overlay.team(scope));reject{overlay.remove(scope,"TEAM",sent.operationId)}
        overlay.remove(scope,"TEAM",sent.operationId,true);assertNull(overlay.team(scope))
        assertFalse(db.onlineSyncDao().recoveredAdminReference(scope,first.originalOperationId)!!.active)
    }
    @Test fun editingCatalogNormalizesAndFreezesWhileRetainingEveryValue():Unit=runBlocking {
        val first=catalogRow();overlay.attach(first)
        val changed=OnlineCatalog.empty().copy(menuCategories=listOf(CatalogCategory(UUID.randomUUID().toString(),"Minuman QA",5)))
        overlay.save(requireNotNull(overlay.catalog(scope)).copy(catalogJson=changed.json().toString()))
        assertEquals(changed.validated(),OnlineCatalog.parse(JSONObject(overlay.catalog(scope)!!.catalogJson)))
        val ready=overlay.catalog(scope)!!.copy(status="QUEUED");overlay.save(ready)
        reject{overlay.save(ready.copy(catalogJson=OnlineCatalog.empty().json().toString()))}
        reject{overlay.save(ready.copy(status="EDITING"))}
        assertEquals(ready,overlay.catalog(scope))
    }
    @Test fun legacyFileConflictIsExplicitAndNeverRepairsOrOverwritesEitherSource():Unit=runBlocking {
        val row=settingsRow();overlay.attach(row)
        settings.save(OnlineSettingsOperation(scope,UUID.randomUUID().toString(),2,"Independent QA","Asia/Jakarta",0))
        val files=File(folder,"online-settings-operations").listFiles()!!.associate{it.name to it.readBytes().toList()}
        reject{overlay.settings(scope)};reject{overlay.capture(scope)};reject{repo.pendingCount()}
        assertEquals(files,File(folder,"online-settings-operations").listFiles()!!.associate{it.name to it.readBytes().toList()})
        assertEquals(row.operationId,db.onlineSyncDao().recoveredAdminReference(scope,row.originalOperationId)!!.operationId)
    }
    @Test fun archiveAndProvenanceSurviveDatabaseRestartWithoutRequeue():Unit=runBlocking {
        val first=settingsRow();overlay.attach(first)
        overlay.save(overlay.settings(scope)!!.copy(status="SENT",wasSent=true))
        overlay.remove(scope,"SETTINGS",first.operationId,true);assertEquals(0,repo.pendingCount())
        db.close();db=open();connect();repo.initialize()
        assertNull(repo.pendingSettings(outlet));assertEquals(0,repo.pendingCount())
        val stored=requireNotNull(db.onlineSyncDao().recoveredAdminReference(scope,first.originalOperationId));assertFalse(stored.active);assertTrue(stored.wasSent)
        reject{overlay.attach(first.copy(operationId=UUID.randomUUID().toString()))}
    }
    @Test fun lifecycleDraftAndProvenanceRollbackTogetherOnClaimFailure():Unit=runBlocking {
        val id=UUID.randomUUID().toString();val menu=UUID.randomUUID().toString();val original=UUID.randomUUID().toString()
        val header=OnlineClosingEntity(scope,id,1,3,"LOCKED",JSONObject().put("businessDate","2026-10-23").toString())
        val entries=listOf(OnlineEntryEntity(scope,id,menu,1,4,1,4,"SYNCED"));val dao=db.onlineSyncDao()
        db.openHelper.writableDatabase.execSQL("CREATE TRIGGER qa_lifecycle_claim_fail BEFORE INSERT ON online_recovery_claims BEGIN SELECT RAISE(ABORT,'Explicit QA claim failure'); END")
        reject{dao.recoverLifecycle(header,entries,"2026-10-23",original,"b".repeat(64),"reopen","Alasan QA","ADMIN")}
        assertNull(dao.closing(scope,id));assertNull(dao.lifecycle(scope,id));assertTrue(dao.recoveryClaims(scope,listOf(original)).isEmpty())
        db.openHelper.writableDatabase.execSQL("DROP TRIGGER qa_lifecycle_claim_fail")
        val newId=dao.recoverLifecycle(header,entries,"2026-10-23",original,"b".repeat(64),"reopen","Alasan QA","ADMIN")
        assertNotEquals(original,newId);assertEquals(newId,dao.lifecycle(scope,id)!!.operationId);assertEquals("QUEUED",dao.lifecycle(scope,id)!!.status)
        assertEquals("Alasan QA",dao.lifecycle(scope,id)!!.reason)
    }
    @Test fun credentialsAndWrongMetadataAreRejectedBeforeAnyRecoveredRowExists():Unit=runBlocking {
        val bad=teamRow().let{it.copy(payloadJson=JSONObject(it.payloadJson).put("password","DO_NOT_STORE_QA_PASSWORD").toString())}
        reject{overlay.attach(bad)};reject{overlay.attach(teamRow().copy(group="SETTINGS"))};reject{overlay.attach(settingsRow().copy(fingerprint="invalid"))}
        assertTrue(db.onlineSyncDao().recoveredAdminForAccount(session.origin+"|"+account+"|").isEmpty())
    }
    @Test fun actualVersion9MigrationPreservesQuantityProvenanceAndLegacyMetadata():Unit=runBlocking {
        db.close();base.deleteDatabase(name)
        val schema=JSONObject(InstrumentationRegistry.getInstrumentation().context.assets.open("com.closingcount.app.data.local.ClosingCountDatabase/9.json").bufferedReader().use{it.readText()}).getJSONObject("database")
        val old=SQLiteDatabase.openOrCreateDatabase(base.getDatabasePath(name),null);val entities=schema.getJSONArray("entities")
        for(i in 0 until entities.length()){val e=entities.getJSONObject(i);old.execSQL(e.getString("createSql").replace("\${TABLE_NAME}",e.getString("tableName")))
            val indexes=e.optJSONArray("indices")?:JSONArray();for(j in 0 until indexes.length())old.execSQL(indexes.getJSONObject(j).getString("createSql").replace("\${TABLE_NAME}",e.getString("tableName")))}
        val setup=schema.getJSONArray("setupQueries");for(i in 0 until setup.length())old.execSQL(setup.getString(i))
        val original=UUID.randomUUID().toString();val closing=UUID.randomUUID().toString();val generated=UUID.randomUUID().toString()
        old.execSQL("INSERT INTO app_metadata VALUES ('recovery-v9','preserve')")
        old.execSQL("INSERT INTO online_recovery_claims VALUES (?,?,?,?,?,?)",arrayOf(scope,original,"c".repeat(64),closing,generated,"2026-10-23T00:00:00Z"))
        old.version=9;old.close();db=open();connect()
        assertEquals(10,db.openHelper.readableDatabase.version);assertEquals("preserve",db.backupDao().getMetadata().single().value)
        assertEquals(generated,db.onlineSyncDao().recoveryClaims(scope,listOf(original)).single().recoveredOperationId)
        assertTrue(db.onlineSyncDao().recoveredAdminForAccount(session.origin+"|"+account+"|").isEmpty())
    }
    @Test fun legacyStatusUpdatesKeepTheirExistingValidationAndExactPayload():Unit=runBlocking {
        val old=OnlineSettingsOperation(scope,UUID.randomUUID().toString(),2," Legacy QA ","Asia/Jakarta",0)
        settings.save(old);assertEquals(old,overlay.settings(scope))
        overlay.save(old.copy(status="SENT",wasSent=true))
        val sent=requireNotNull(settings.load(scope));assertEquals(" Legacy QA ",sent.name);assertTrue(sent.wasSent)
        val files=File(folder,"online-settings-operations").listFiles()!!.associate{it.name to it.readBytes().toList()}
        // Strict external export still refuses noncanonical input instead of silently changing it.
        reject{repo.capturePendingBackup(session,outlet,UUID.randomUUID().toString())}
        assertEquals(files,File(folder,"online-settings-operations").listFiles()!!.associate{it.name to it.readBytes().toList()})
    }
}
