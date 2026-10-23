package com.closingcount.app

import android.content.Context
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.view.accessibility.AccessibilityNodeInfo
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.closingcount.app.data.online.*
import com.closingcount.app.data.transfer.*
import com.closingcount.app.ui.online.OnlinePendingTransferViewModel
import com.closingcount.app.ui.online.OnlineViewModel
import com.closingcount.app.data.local.BackupSnapshot
import kotlinx.coroutines.Job
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.*
import org.junit.Assert.*
import java.io.File
import java.security.MessageDigest
import java.util.UUID

/** Production MainActivity/Compose routes and real DocumentsUI; explicit owned QA endpoint only. */
class OnlinePendingTransferNativeInstrumentedTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()
    private val instrumentation=InstrumentationRegistry.getInstrumentation()
    private val app=ApplicationProvider.getApplicationContext<ClosingCountApplication>()
    private lateinit var api:OnlineApi;private lateinit var session:OnlineSession;private lateinit var vm:OnlinePendingTransferViewModel
    private lateinit var files:LocalTransferQaFiles;private lateinit var outlet:String;private lateinit var id:String;private lateinit var menu:String
    private lateinit var source:OnlinePendingBackup;private lateinit var fingerprint:String
    private var oldSession:OnlineSession?=null;private var oldPrefs:Map<String,Any?> =emptyMap();private var baseline=""
    private val code=InstrumentationRegistry.getArguments().getString("ccsPendingUiCase")?:"native-light"
    private val date=InstrumentationRegistry.getArguments().getString("ccsPendingUiDate")?:"2026-07-01"
    private val proof=JSONObject().put("case",code).put("passed",false)
    private val preferences by lazy{app.getSharedPreferences("ccs_online_ui",Context.MODE_PRIVATE)}
    private val sessionStore by lazy{OnlineSessionStore(app)}
    private var ready=false
    private var ownsScope=false
    private val jobs=mutableListOf<Job>()
    private var oldLocal:BackupSnapshot?=null
    private val deviceFiles=mutableMapOf<String,ByteArray>()
    @Before fun setup():Unit=runBlocking {
        val args=InstrumentationRegistry.getArguments();check(args.getString("ccsPendingUiQa")=="true")
        check(args.getString("ccsQaOrigin")=="http://10.0.2.2:4371");check(code.matches(Regex("[a-z0-9-]{1,40}")))
        check(date.matches(Regex("2026-07-(0[1-9]|[12][0-9]|3[01])")))
        outlet=OnlineApi.uuid(requireNotNull(args.getString("ccsQaOutlet")))
        oldSession=sessionStore.load();oldPrefs=preferences.all.toMap();check(oldPrefs.values.all{it is String})
        baseline=digestTables();oldLocal=app.database.backupDao().createSnapshot();ready=true
        for(suffix in listOf("",".bak",".new")){val f=File(app.noBackupFilesDir,"online-backup-device-id"+suffix);if(f.exists())deviceFiles[f.name]=f.readBytes()}
        api=OnlineApi(OnlineEndpoint("http://10.0.2.2:4371",true),4000)
        session=api.login(requireNotNull(args.getString("ccsQaEmployee")),requireNotNull(args.getString("ccsQaPassword")))
        app.onlineRepository.activate(session,true)
        val initial=api.create(session,outlet,date);id=initial.header.closingId;menu=initial.entries.first().menuId
        for(table in tables)app.database.openHelper.readableDatabase.query("SELECT COUNT(*) FROM $table WHERE scope=?",arrayOf(session.scope(outlet))).use{cursor->check(cursor.moveToFirst()&&cursor.getLong(0)==0L)}
        ownsScope=true
        val original=UUID.randomUUID().toString();val tail=UUID.randomUUID().toString()
        app.database.onlineSyncDao().recoverQuantities(initial.header,initial.entries,date,mapOf(menu to 4),mapOf(original to menu),emptySet(),"a".repeat(64))
        val menuName=JSONObject(initial.header.snapshotJson).getJSONObject("catalog").getJSONArray("menus").let{raw->(0 until raw.length()).map{raw.getJSONObject(it)}.first{it.getString("id")==menu}.getString("name")}
        source=OnlinePendingBackup(UUID.randomUUID().toString(),"2026-10-23T00:00:00Z",UUID.randomUUID().toString(),session.origin,
            PendingBackupAccount(session.identity.id,session.identity.username,session.identity.displayName),PendingBackupOutlet(outlet,"Cadangan outlet QA"),
            listOf(PendingBackupClosing(id,date,1,0,"DRAFT",listOf(PendingBackupEntry(menu,menuName,0,0,8,"PENDING")),
                listOf(PendingBackupQuantity(original,menu,1,0,4,"SENT"),PendingBackupQuantity(tail,menu,1,null,8,"QUEUED")),null)),emptyList())
        fingerprint=OnlinePendingBackupCodec.decode(OnlinePendingBackupCodec.encode(source)).sha256
        preferences.edit().clear().putString("scope",session.scope(outlet)).putString("outlet",outlet).putString("date",date).commit()
        files=LocalTransferQaFiles(app)
        instrumentation.runOnMainSync{
            vm=ViewModelProvider(compose.activity)[OnlinePendingTransferViewModel::class.java]
            listOf(vm,ViewModelProvider(compose.activity)[OnlineViewModel::class.java]).forEach{model->model.viewModelScope.coroutineContext[Job]?.let(jobs::add)}
        }
        proof.put("outlet",outlet).put("actor",session.identity.id).put("closing",id).put("menu",menu).put("reviewSha256",fingerprint)
            .put("baselineSha256",baseline).put("physicalPhoneAccessed",false)
    }
    @After fun cleanup():Unit=runBlocking {
        if(ready){
            compose.activityRule.scenario.close()
            jobs.forEach{it.join()}
            if(::session.isInitialized){runCatching{api.logout(session)}
                if(ownsScope){val scope=session.scope(outlet)
                    app.database.runInTransaction {for(table in tables)app.database.openHelper.writableDatabase.execSQL("DELETE FROM $table WHERE scope=?",arrayOf(scope))}
                }
            }
            if(::fingerprint.isInitialized){val inbox=OnlinePendingRecoveryInbox(app);if(inbox.inventory().any{it.fingerprint==fingerprint})inbox.remove(inbox.prepareRemoval(fingerprint).id)}
            for(suffix in listOf("",".bak",".new")){val f=File(app.noBackupFilesDir,"online-backup-device-id"+suffix);val bytes=deviceFiles[f.name];if(bytes!=null)f.writeBytes(bytes)else if(f.exists())check(f.delete())}
            if(oldSession==null)sessionStore.clear() else sessionStore.save(requireNotNull(oldSession));app.onlineRepository.initialize()
            preferences.edit().clear().apply{oldPrefs.forEach{(key,value)->putString(key,value as String)}}.commit()
            assertEquals(baseline,digestTables());assertEquals(oldLocal,app.database.backupDao().createSnapshot())
            proof.put("existingDatabaseRestored",true).put("legacyDatabaseUnchanged",true).put("afterSha256",digestTables());save()
        }
        if(::files.isInitialized)files.close()
    }
    private val tables=listOf("online_entries","online_operations","online_lifecycle_operations","online_recovery_claims","online_recovered_admin","online_closings")
    private fun digestTables():String {
        val text=StringBuilder()
        for(table in tables){text.append(table);app.database.openHelper.readableDatabase.query("SELECT * FROM $table ORDER BY rowid").use{c->while(c.moveToNext()){for(i in 0 until c.columnCount)text.append(if(c.isNull(i))"<null>" else c.getString(i)).append('|');text.append('\n')}}}
        return sha(text.toString().toByteArray())
    }
    private fun sha(bytes:ByteArray)=MessageDigest.getInstance("SHA-256").digest(bytes).joinToString(""){"%02x".format(it)}
    private fun save(){File(app.getExternalFilesDir(null),"pending-ui-$code.json").writeText(proof.toString(2))}
    private fun waitFor(check:()->Boolean){compose.waitUntil(45000){runCatching(check).getOrDefault(false)}}
    private fun click(tag:String,scroll:Boolean=true){
        waitFor{compose.onAllNodesWithTag(tag).fetchSemanticsNodes().singleOrNull()?.config?.contains(SemanticsProperties.Disabled)==false}
        val n=compose.onNodeWithTag(tag);if(scroll)n.performScrollTo();n.assertIsDisplayed().performClick()
    }
    private fun nav(page:String){if(compose.onAllNodesWithTag("online.nav.menu").fetchSemanticsNodes().isNotEmpty())click("online.nav.menu",false);click("online.nav.$page",false)}
    private fun capture(scene:String){
        proof.put("stage",scene);save()
        if(instrumentation.uiAutomation.rootInActiveWindow?.packageName?.toString()=="com.closingcount.app")compose.waitForIdle()
        instrumentation.uiAutomation.waitForIdle(300,5000)
        var image:android.graphics.Bitmap?=null
        repeat(5){if(image==null){image=instrumentation.uiAutomation.takeScreenshot();if(image==null)Thread.sleep(250)}}
        val captured=requireNotNull(image){"QA screenshot unavailable"}
        File(app.getExternalFilesDir(null),"pending-ui-$code-$scene.png").outputStream().use{check(captured.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it))};captured.recycle()
    }
    private fun shell(command:String)=ParcelFileDescriptor.AutoCloseInputStream(instrumentation.uiAutomation.executeShellCommand(command)).use{it.readBytes()}
    private fun node(predicate:(AccessibilityNodeInfo)->Boolean):AccessibilityNodeInfo? {
        fun find(n:AccessibilityNodeInfo?):AccessibilityNodeInfo?{n?:return null;if(predicate(n))return n;for(i in 0 until n.childCount)find(n.getChild(i))?.let{return it};return null}
        return find(instrumentation.uiAutomation.rootInActiveWindow)
    }
    private fun systemClick(predicate:(AccessibilityNodeInfo)->Boolean){waitFor{node(predicate)!=null};var target=requireNotNull(node(predicate));while(!target.isClickable)target=target.parent?:error("No clickable system parent");check(target.isEnabled&&target.performAction(AccessibilityNodeInfo.ACTION_CLICK))}
    private fun picker(){waitFor{instrumentation.uiAutomation.rootInActiveWindow?.packageName?.toString()?.contains("documentsui")==true}}
    private fun cancelPicker(){repeat(5){if(instrumentation.uiAutomation.rootInActiveWindow?.packageName?.toString()?.contains("documentsui")==true){shell("input keyevent 4");Thread.sleep(200)}}}
    private fun choose(name:String){picker()
        if(node{it.text?.toString()==name}==null){systemClick{it.contentDescription?.toString() in listOf("Show roots","Show navigation drawer","Tampilkan akar","Buka panel navigasi")};systemClick{it.text?.toString() in listOf("Downloads","Download","Unduhan")}}
        repeat(10){if(node{it.text?.toString()==name}!=null){systemClick{it.text?.toString()==name};return};node{it.isScrollable}?.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD);Thread.sleep(150)}
        error("Owned pending document not visible")
    }
    @Test fun productionPickerPreviewRecreateFrozenAcceptAndExplicitRecoveredSync():Unit=runBlocking {
        try {
            val(uri,filename)=files.create("pending-native",OnlinePendingBackupCodec.encode(source))
            val(_,invalid)=files.create("pending-invalid","not a pending backup".toByteArray())
            compose.onNodeWithText("Closing bersama").performClick();waitFor{compose.onAllNodesWithTag("online.nav.Akun").fetchSemanticsNodes().isNotEmpty()||compose.onAllNodesWithTag("online.nav.menu").fetchSemanticsNodes().isNotEmpty()}
            nav("Akun");click("online.pending.open");waitFor{vm.state.value.opened&&!vm.state.value.busy};capture("inbox")
            assertEquals(0,api.fetch(session,outlet,id).entries.first().serverQuantity)
            click("online.pending.import");picker();capture("picker");cancelPicker();waitFor{!vm.state.value.waitingOpen};assertNull(vm.state.value.importing)
            click("online.pending.export");waitFor{vm.state.value.export!=null&&!vm.state.value.busy};capture("export-review")
            click("online.pending.export.save");picker()
            val exported="ccs-qa-pending-export-${UUID.randomUUID()}.json"
            waitFor{node{it.className?.toString()?.endsWith("EditText")==true}!=null}
            check(requireNotNull(node{it.className?.toString()?.endsWith("EditText")==true}).performAction(AccessibilityNodeInfo.ACTION_SET_TEXT,Bundle().apply{putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,exported)}))
            systemClick{it.isEnabled&&it.text?.toString()?.lowercase() in listOf("save","simpan")}
            waitFor{!vm.state.value.busy&&vm.state.value.message?.startsWith("Cadangan draft berhasil") ==true}
            val bytes=shell("cat /sdcard/Download/$exported");val encoded=OnlinePendingBackupCodec.decode(bytes)
            assertEquals(4,encoded.model.closings.single().entries.single().localQuantity);assertEquals(session.identity.id,encoded.model.account.id)
            proof.put("exportFilename",exported).put("exportSha256",sha(bytes));capture("exported")
            click("online.pending.back",false);nav("Input")
            click("online.sync");waitFor{runBlocking{api.fetch(session,outlet,id).entries.first().serverQuantity==4}}
            nav("Akun");click("online.pending.open");waitFor{!vm.state.value.busy}
            click("online.pending.import");choose(invalid);waitFor{!vm.state.value.busy&&vm.state.value.failed};capture("invalid")
            click("online.pending.import");choose(filename);waitFor{vm.state.value.importing!=null&&!vm.state.value.busy}
            val reviewed=requireNotNull(vm.state.value.importing);assertEquals(fingerprint,reviewed.summary.sha256);capture("import-review")
            compose.activityRule.scenario.recreate();waitFor{vm.state.value.importing==reviewed};capture("recreated")
            files.rewrite(uri,"provider changed after preview".toByteArray());click("online.pending.import.accept")
            waitFor{vm.state.value.model!=null&&!vm.state.value.busy};assertEquals(source,vm.state.value.model);capture("accepted")
            click("online.pending.compare.$id");waitFor{vm.state.value.quantity!=null&&!vm.state.value.busy}
            assertEquals(4,vm.state.value.quantity!!.rows.single().serverQuantity);assertEquals(8,vm.state.value.quantity!!.rows.single().desired)
            capture("quantity-review");click("online.pending.apply.quantity");waitFor{vm.state.value.destination!=null&&!vm.state.value.busy}
            assertEquals(4,api.fetch(session,outlet,id).entries.first().serverQuantity)
            click("online.pending.continue");waitFor{compose.onAllNodesWithTag("online.pending.page").fetchSemanticsNodes().isEmpty()}
            compose.activityRule.scenario.recreate();compose.waitForIdle();assertEquals(4,api.fetch(session,outlet,id).entries.first().serverQuantity);capture("held-draft")
            click("online.sync");waitFor{runBlocking{api.fetch(session,outlet,id).entries.first().serverQuantity==8}}
            capture("synced");proof.put("activityReviewRetained",true).put("frozenProviderPayloadAccepted",true).put("automaticSyncHeldOnResume",true).put("explicitFinalQuantity",8).put("passed",true);save()
        }catch(error:Throwable){proof.put("failureClass",error.javaClass.simpleName);runCatching{capture("failure")};save();throw error}
    }
}
