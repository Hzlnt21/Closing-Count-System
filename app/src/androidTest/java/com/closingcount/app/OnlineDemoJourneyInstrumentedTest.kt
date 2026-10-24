package com.closingcount.app

import android.content.ContentUris
import android.graphics.Bitmap
import android.os.ParcelFileDescriptor
import android.os.Process
import android.os.SystemClock
import android.provider.MediaStore
import android.view.accessibility.AccessibilityNodeInfo
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.closingcount.app.data.online.*
import com.closingcount.app.data.transfer.*
import com.closingcount.app.ui.online.*
import kotlinx.coroutines.Job
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.*
import org.junit.Assert.*
import java.io.File
import java.security.MessageDigest
import java.time.LocalDate
import java.util.UUID

/** Two guarded temporary Android-user installations, real DEMO runtime, production UI mutations.
 * Host phases: A prime, B work, stop service, A offline, restart service, A resolve/submit,
 * A supervisor, A admin, stop service, B offline, reset service, B old draft, A new login.
 * No repository save/sync/activate/prepare or API mutation is called by this driver.
 */
class OnlineDemoJourneyInstrumentedTest {
    @get:Rule val compose=createEmptyComposeRule()
    private val instrument=InstrumentationRegistry.getInstrumentation()
    private val app=ApplicationProvider.getApplicationContext<ClosingCountApplication>()
    private val args=InstrumentationRegistry.getArguments()
    private lateinit var scenario:ActivityScenario<MainActivity>
    private lateinit var online:OnlineViewModel
    private lateinit var pending:OnlinePendingTransferViewModel
    private lateinit var catalog:OnlineCatalogViewModel
    private lateinit var exporter:OnlineReportExportViewModel
    private lateinit var api:OnlineApi
    private lateinit var phase:String;private lateinit var run:String;private lateinit var outlet:String;private lateinit var closing:String
    private lateinit var capp:String;private lateinit var latte:String;private lateinit var cocoa:String;private lateinit var date:String
    private lateinit var saved:JSONObject;private lateinit var marker:File;private lateinit var evidence:File
    private var user=0;private var order=emptyList<String>();private val jobs=linkedSetOf<Job>()
    private val proof=JSONObject().put("passed",false).put("physicalPhoneAccessed",false).put("mutationsThroughProductionUi",true)
    private fun arg(name:String)=requireNotNull(args.getString("ccsDemo$name"))
    private fun sha(b:ByteArray)=MessageDigest.getInstance("SHA-256").digest(b).joinToString(""){"%02x".format(it)}
    private fun legacy()=runBlocking{sha(BackupJsonCodec.encode(app.database.backupDao().createSnapshot(),0))}
    @Before fun guard() {
        check(arg("UiQa")=="true"&&BuildConfig.DEBUG&&BuildConfig.ONLINE_API_ORIGIN=="http://10.0.2.2:4372")
        run=arg("Run");check(UUID.fromString(run).toString()==run);user=arg("User").toInt();check(user>0&&Process.myUid()/100000==user)
        phase=arg("Phase");val installation=arg("Installation");check(installation in listOf("a","b"))
        order=if(installation=="a")listOf("rani-prime","rani-offline","rani-resolve","supervisor","admin","new-generation") else listOf("dimas-work","dimas-offline","after-reset")
        check(phase in order);outlet=OnlineApi.uuid(arg("Outlet"));closing=OnlineApi.uuid(arg("Closing"))
        capp=OnlineApi.uuid(arg("Cappuccino"));latte=OnlineApi.uuid(arg("Latte"));cocoa=OnlineApi.uuid(arg("Cocoa"));date=arg("Date");check(LocalDate.parse(date).toString()==date)
        api=OnlineApi(OnlineEndpoint("http://10.0.2.2:4372",true),4000);runBlocking{app.onlineRepository.initialize()}
        marker=File(app.noBackupFilesDir,"native-demo-$run.json");evidence=File(app.filesDir,"native-demo-$run").apply{mkdirs()}
        if(phase==order.first()) {
            check(!marker.exists());assertNull(app.onlineRepository.session())
            saved=JSONObject().put("run",run).put("user",user).put("installation",installation).put("next",0).put("legacy",legacy())
        }else{saved=JSONObject(marker.readText());assertEquals(run,saved.getString("run"));assertEquals(user,saved.getInt("user"));assertEquals(installation,saved.getString("installation"));assertEquals(saved.getString("legacy"),legacy())}
        assertEquals(order.indexOf(phase),saved.getInt("next"))
        scenario=ActivityScenario.launch(MainActivity::class.java)
        waitFor{exists("online.username")||exists("online.nav.Akun")||visible("Closing bersama")}
        if(!exists("online.username")&&!exists("online.nav.Akun"))clickText("Closing bersama")
        scenario.onActivity{a->val models=ViewModelProvider(a);online=models[OnlineViewModel::class.java];pending=models[OnlinePendingTransferViewModel::class.java];catalog=models[OnlineCatalogViewModel::class.java];exporter=models[OnlineReportExportViewModel::class.java]
            listOf(online,pending,catalog,exporter).forEach{it.viewModelScope.coroutineContext[Job]?.let(jobs::add)}}
        waitFor{online.state.value.initialized}
        proof.put("run",run).put("phase",phase).put("user",user).put("uid",Process.myUid()).put("pid",Process.myPid()).put("installation",installation).put("legacyBefore",saved.getString("legacy"))
    }
    @After fun close()=runBlocking {
        if(::scenario.isInitialized){scenario.close();jobs.forEach{it.join()}}
        if(::saved.isInitialized){assertEquals(saved.getString("legacy"),legacy());proof.put("legacyAfter",legacy());writeProof()}
    }
    private fun waitFor(check:()->Boolean){compose.waitUntil(45000){runCatching(check).getOrDefault(false)}}
    private fun exists(tag:String)=compose.onAllNodesWithTag(tag).fetchSemanticsNodes().size==1
    private fun visible(value:String)=compose.onAllNodesWithText(value,substring=true).fetchSemanticsNodes().isNotEmpty()
    private fun click(tag:String,scroll:Boolean=true){waitFor{exists(tag)};val n=compose.onNodeWithTag(tag);if(scroll)n.performScrollTo();n.assertIsDisplayed().assertIsEnabled().performClick()}
    private fun clickText(value:String,scroll:Boolean=true){waitFor{visible(value)};val node=compose.onNodeWithText(value);if(scroll)node.performScrollTo();node.assertIsDisplayed().assertIsEnabled().performClick()}
    private fun shell(value:String)=ParcelFileDescriptor.AutoCloseInputStream(instrument.uiAutomation.executeShellCommand(value)).use{it.readBytes()}
    private fun imeShown():Boolean {
        var visible=false
        scenario.onActivity { visible=ViewCompat.getRootWindowInsets(it.window.decorView)?.isVisible(WindowInsetsCompat.Type.ime())==true }
        return visible
    }
    private fun dismissIme() {
        // InputMethodService's view flag can remain true after its window is
        // hidden. The system's show request also catches a late IME opening
        // before the Activity has received its visible-insets callback.
        var hiddenSince=0L;var backRequested=false
        waitFor {
            val now=SystemClock.uptimeMillis()
            val requested=Regex("mInputShown=true").containsMatchIn(shell("dumpsys input_method").toString(Charsets.UTF_8))
            if(requested) {
                if(!backRequested)shell("input keyevent 4")
                backRequested=true;hiddenSince=0
            } else {
                backRequested=false
                if(imeShown())hiddenSince=0 else if(hiddenSince==0L)hiddenSince=now
            }
            hiddenSince!=0L&&now-hiddenSince>=750
        }
    }
    private fun fill(tag:String,value:String){compose.onNodeWithTag(tag).performScrollTo().performTextReplacement(value);compose.waitForIdle();dismissIme()}
    private fun nav(page:String){if(exists("online.nav.menu"))click("online.nav.menu",false);click("online.nav.$page",false)}
    private fun snap(scene:String){compose.waitForIdle();val image=requireNotNull(instrument.uiAutomation.takeScreenshot());File(evidence,"$phase-$scene.png").outputStream().use{check(image.compress(Bitmap.CompressFormat.PNG,100,it))};image.recycle()}
    private fun writeProof(){if(::evidence.isInitialized)File(evidence,"$phase-proof.json").writeText(proof.toString(2))}
    private fun persist(){marker.writeText(saved.toString(2))}
    private fun session()=requireNotNull(app.onlineRepository.session())
    private fun login(key:String) {
        waitFor{exists("online.username")};fill("online.username",arg(key+"Username"));fill("online.password",arg(key+"Password"));click("online.login")
        waitFor{!online.state.value.loginVisible&&!online.state.value.working};assertEquals(arg(key+"Actor"),session().identity.id)
    }
    private fun logout(){nav("Akun");clickText("Keluar akun");waitFor{online.state.value.loginVisible};assertNull(app.onlineRepository.session())}
    private fun openCurrent(onlineRequired:Boolean=true) {
        nav("Ringkasan");waitFor{online.state.value.outletId==outlet&&online.state.value.date==date&&!online.state.value.working}
        if(online.state.value.closingId==null)click("online.open")
        waitFor{online.state.value.closingId==closing&&online.state.value.menus.size==2};nav("Input")
        if(onlineRequired)synced()
    }
    private fun synced(){waitFor{online.state.value.pending==0&&online.state.value.syncVerified&&!online.state.value.syncing&&!online.state.value.working}}
    private fun quantity(menu:String,value:Int){fill("online.qty.$menu",value.toString());click("online.save");waitFor{!online.state.value.working&&!online.state.value.dirty}}
    private fun row(menu:String)=online.state.value.menus.single{it.id==menu}
    private fun remote()=runBlocking{api.fetch(session(),outlet,closing)}
    private fun operation():JSONObject {
        val result=JSONArray();app.database.openHelper.readableDatabase.query("SELECT operationId,scope,closingId,menuId,generation,expectedRevision,quantity,status FROM online_operations WHERE scope=? AND closingId=?",arrayOf(session().scope(outlet),closing)).use{c->while(c.moveToNext()){val r=JSONObject();for(i in 0 until c.columnCount)r.put(c.getColumnName(i),if(c.isNull(i))JSONObject.NULL else c.getString(i));result.put(r)}}
        assertEquals(1,result.length());return result.getJSONObject(0)
    }
    private fun action(value:String,reason:String?=null){nav("Review");synced();click("online.action.$value");if(reason!=null)fill("online.lifecycle.reason",reason);click("online.lifecycle.confirm",false);synced()}
    private fun picker(){waitFor{instrument.uiAutomation.rootInActiveWindow?.packageName?.toString()?.contains("documentsui")==true}}
    private fun node(predicate:(AccessibilityNodeInfo)->Boolean):AccessibilityNodeInfo? {
        fun find(n:AccessibilityNodeInfo?):AccessibilityNodeInfo?{n?:return null;if(n.isVisibleToUser&&predicate(n))return n;for(i in 0 until n.childCount)find(n.getChild(i))?.let{return it};return null};return find(instrument.uiAutomation.rootInActiveWindow)
    }
    private fun saveAs(name:String){picker();waitFor{node{it.className?.toString()?.endsWith("EditText")==true}!=null}
        check(requireNotNull(node{it.className?.toString()?.endsWith("EditText")==true}).performAction(AccessibilityNodeInfo.ACTION_SET_TEXT,android.os.Bundle().apply{putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,name)}))
        waitFor{node{it.isEnabled&&it.text?.toString()?.lowercase() in listOf("save","simpan")}!=null};var target=requireNotNull(node{it.isEnabled&&it.text?.toString()?.lowercase() in listOf("save","simpan")});while(!target.isClickable)target=target.parent?:error("Save parent unavailable");check(target.performAction(AccessibilityNodeInfo.ACTION_CLICK))
    }
    private fun savedBytes(name:String):ByteArray {
        // Read-only test oracle after the ordinary SAF write. Never changes the
        // application's normal export permission or server authorization path.
        instrument.uiAutomation.adoptShellPermissionIdentity()
        try {
            var bytes:ByteArray?=null
            waitFor{app.contentResolver.query(MediaStore.Downloads.EXTERNAL_CONTENT_URI,arrayOf(MediaStore.Downloads._ID),MediaStore.Downloads.DISPLAY_NAME+"=?",arrayOf(name),null)?.use{c->if(c.moveToFirst()){val uri=ContentUris.withAppendedId(MediaStore.Downloads.EXTERNAL_CONTENT_URI,c.getLong(0));bytes=app.contentResolver.openInputStream(uri)?.use{it.readBytes()}}};bytes!=null}
            return requireNotNull(bytes)
        }finally{instrument.uiAutomation.dropShellPermissionIdentity()}
    }
    @Test fun phasedNativeDemoUsesRealSharedClosingAndPreservesOldDraft() {
        try {
            when(phase) {
                "rani-prime"->{login("Rani");openCurrent();assertEquals(3,row(capp).quantity);assertEquals(5,row(latte).quantity);quantity(capp,4);synced();assertEquals(4,remote().entries.single{it.menuId==capp}.serverQuantity);snap("contribution");saved.put("actor",session().identity.id)}
                "dimas-work"->{login("Dimas");openCurrent();assertEquals(4,row(capp).quantity);quantity(latte,6);synced();assertEquals(4,row(capp).quantity);snap("merged-team");quantity(capp,5);synced();assertEquals(5,remote().entries.single{it.menuId==capp}.serverQuantity);snap("latest-team-value");saved.put("actor",session().identity.id)}
                "rani-offline","dimas-offline"->{openCurrent(false);val menu=if(phase=="rani-offline")capp else latte;val desired=if(phase=="rani-offline")8 else 9
                    quantity(menu,desired);waitFor{online.state.value.pending==1};val op=operation();saved.put("offlineOperation",op);persist();snap("offline-draft");scenario.recreate();compose.waitForIdle();waitFor{online.state.value.pending==1};assertEquals(op.getString("operationId"),operation().getString("operationId"));assertEquals(desired,row(menu).quantity);snap("recreated-offline");proof.put("offlineOperation",op).put("activityRecreatedWithDraft",true)}
                "rani-resolve"->{openCurrent(false);waitFor{row(capp).status=="CONFLICT"};assertEquals(5,row(capp).serverQuantity);assertEquals(8,row(capp).quantity)
                    compose.onAllNodes(hasText("Tinjau ",substring=true) and hasClickAction()).onFirst().performScrollTo().performClick();waitFor{visible("Tentukan angka yang benar")};snap("conflict-review")
                    compose.onNodeWithText("Terapkan pilihan").assertIsNotEnabled();clickText("Kirim ulang draft saya: 8",false);clickText("Terapkan pilihan",false);synced();assertEquals(8,row(capp).quantity);assertEquals(6,row(latte).quantity);snap("resolved-explicitly")
                    click("online.sync");synced();action("submit");assertEquals("SUBMITTED",online.state.value.closingState);assertFalse(exists("online.action.lock"));snap("submitted");proof.put("rejectedOriginalId",saved.getJSONObject("offlineOperation").getString("operationId"))}
                "supervisor"->{openCurrent();assertEquals("SUBMITTED",online.state.value.closingState);logout();login("Arif");openCurrent();nav("Review");snap("supervisor-review");action("lock");assertEquals("LOCKED",online.state.value.closingState);snap("locked")
                    val report=runBlocking{api.report(session(),outlet,closing,1)};saved.put("reportSha256",sha(report.toByteArray()));persist();File(evidence,"$phase-frozen-report.json").writeText(report)
                    nav("Riwayat");click("online.history.detail.$closing");click("online.report.1");waitFor{online.state.value.frozenReport!=null};compose.onNodeWithTag("online.report.quantity.$capp").performScrollTo().assertTextEquals("8");snap("frozen-report")
                    click("online.report.export");val filename="ccs-demo-report-$run.xlsx";saveAs(filename);waitFor{!exporter.state.value.busy&&exporter.state.value.message?.contains("berhasil disimpan")==true};val bytes=savedBytes(filename);assertTrue(bytes.size>1000);File(evidence,"$phase-report.xlsx").writeBytes(bytes);proof.put("reportSha256",saved.getString("reportSha256")).put("workbookSha256",sha(bytes));snap("report-saved")
                    click("online.report.back");click("online.history.open");synced();action("reopen","Koreksi jumlah setelah review demo");nav("Input");quantity(capp,9);synced();assertEquals(2L,online.state.value.generation);assertEquals(6,row(latte).quantity);assertEquals(report,runBlocking{api.report(session(),outlet,closing,1)});snap("reopened-edited")}
                "admin"->{openCurrent();logout();login("Dewi");nav("Akun");click("online.catalog.open");waitFor{catalog.state.value.version!=null&&!catalog.state.value.busy};click("online.catalog.start")
                    compose.onNodeWithTag("online.catalog.list").performScrollToNode(hasTestTag("online.catalog.edit.$capp"));click("online.catalog.edit.$capp",false);fill("online.catalog.form.name","Cappuccino Baru")
                    compose.onNodeWithTag("online.catalog.recipe.list").performScrollToNode(hasTestTag("online.catalog.recipe.$cocoa"));click("online.catalog.recipe.$cocoa");click("online.catalog.form.save",false);waitFor{!exists("online.catalog.form.save")}
                    compose.onNodeWithTag("online.catalog.list").performScrollToNode(hasTestTag("online.catalog.publish"));click("online.catalog.publish",false);snap("catalog-review");click("online.catalog.publish.confirm",false);waitFor{catalog.state.value.pending==null&&!catalog.state.value.busy};snap("catalog-published");click("online.catalog.close",false)
                    val current=runBlocking{api.catalog(session(),outlet)};assertEquals(2L,current.revision);assertFalse(current.catalog.menus.single{it.id==capp}.ingredientIds.contains(cocoa))
                    val report=runBlocking{api.report(session(),outlet,closing,1)};assertEquals(saved.getString("reportSha256"),sha(report.toByteArray()));nav("Riwayat");click("online.history.detail.$closing");click("online.report.1");waitFor{online.state.value.frozenReport!=null};compose.onNodeWithTag("online.report.quantity.$capp").performScrollTo().assertTextEquals("8");snap("old-report-preserved");click("online.report.back");click("online.history.back");logout()}
                "after-reset"->{openCurrent(false);waitFor{online.state.value.authRequired};val op=operation();assertEquals(saved.getJSONObject("offlineOperation").getString("operationId"),op.getString("operationId"));assertEquals(saved.getString("actor"),session().identity.id);assertEquals(9,row(latte).quantity);snap("old-draft-access-required")
                    nav("Akun");click("online.pending.open");waitFor{pending.state.value.opened&&!pending.state.value.busy};click("online.pending.export");waitFor{pending.state.value.export!=null&&!pending.state.value.busy};snap("old-draft-backup-review");click("online.pending.export.save");val filename="ccs-demo-pending-$run.json";saveAs(filename);waitFor{!pending.state.value.busy&&pending.state.value.message?.contains("berhasil disimpan")==true}
                    val bytes=savedBytes(filename);val decoded=OnlinePendingBackupCodec.decode(bytes);assertEquals(outlet,decoded.model.outlet.id);assertEquals(saved.getString("actor"),decoded.model.account.id);assertEquals("http://10.0.2.2:4372",decoded.model.origin);assertEquals(op.getString("operationId"),decoded.model.closings.single().operations.single().operationId)
                    File(evidence,"$phase-pending-backup.json").writeBytes(bytes);proof.put("oldOperationAfterReset",op).put("backupSha256",sha(bytes));snap("old-draft-backed-up")}
                "new-generation"->{assertNull(app.onlineRepository.session());login("NextRani");waitFor{online.state.value.outletId==arg("NextOutlet")};nav("Ringkasan");click("online.open");waitFor{online.state.value.closingId==arg("NextClosing")};nav("Input");synced();assertEquals(3,row(arg("NextCappuccino")).quantity);assertEquals(5,row(arg("NextLatte")).quantity);assertNotEquals(saved.getString("actor"),session().identity.id);snap("fresh-generation");proof.put("newActor",session().identity.id).put("newOutlet",online.state.value.outletId);logout()}
            }
            saved.put("next",order.indexOf(phase)+1);persist();proof.put("passed",true).put("outlet",outlet).put("closing",closing).put("state",online.state.value.closingState).put("revision",online.state.value.revision).put("generation",online.state.value.generation).put("pending",online.state.value.pending);writeProof()
        }catch(e:Throwable){runCatching{snap("failure")};writeProof();throw e}
    }
}
