package com.closingcount.app

import android.content.Context
import android.graphics.Bitmap
import android.os.Process
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso
import androidx.test.platform.app.InstrumentationRegistry
import com.closingcount.app.data.online.*
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Actual native UI phases; only the host may terminate the owned emulator process. */
class OnlineNativeRecoveryInstrumentedTest{
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()
    private val context=ApplicationProvider.getApplicationContext<Context>() as ClosingCountApplication
    private val repo get()=context.onlineRepository
    private val dao get()=context.database.onlineSyncDao()
    private lateinit var outlet:String;private lateinit var password:String;private lateinit var employee:String;private lateinit var admin:String
    private lateinit var phase:String;private lateinit var file:File;private lateinit var state:JSONObject
    private fun waitText(text:String){compose.waitUntil(45000){compose.onAllNodesWithText(text,substring=true).fetchSemanticsNodes().isNotEmpty()}}
    private fun ready(tag:String){compose.waitUntil(45000){val nodes=compose.onAllNodesWithTag(tag).fetchSemanticsNodes();nodes.size==1&&!nodes[0].config.contains(SemanticsProperties.Disabled)}}
    private fun click(tag:String,scroll:Boolean=true){ready(tag);if(scroll)compose.onNodeWithTag(tag).performScrollTo();compose.onNodeWithTag(tag).performClick()}
    private fun fill(tag:String,value:String){ready(tag);compose.onNodeWithTag(tag).performScrollTo().performTextReplacement(value);Espresso.closeSoftKeyboard()}
    private fun catalogClick(tag:String){compose.onNodeWithTag("online.catalog.list").performScrollToNode(hasTestTag(tag));click(tag,false)}
    private fun enter(){compose.onNodeWithText("Closing bersama").performClick()}
    private fun login(user:String){waitText("Selamat datang kembali");fill("online.username",user);fill("online.password",password);click("online.login");waitText("Belum ada closing tersimpan")}
    private fun account(){click("online.nav.Akun",false)}
    private fun proof(waiting:Boolean=false){state.put("phase",phase).put("pid",Process.myPid()).put("outlet",outlet).put("package",context.packageName).put("waitingForHostKill",waiting);file.writeText(state.toString(2))}
    private fun killBoundary(){proof(true);assertFalse("Host must terminate the owned process",CountDownLatch(1).await(100,TimeUnit.SECONDS));fail("Host kill was not observed")}
    private fun capture(name:String){compose.waitForIdle();val bitmap=InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot();File(context.getExternalFilesDir(null),"native-recovery-$name.png").outputStream().use{bitmap.compress(Bitmap.CompressFormat.PNG,100,it)};bitmap.recycle()}
    private fun header()=runBlocking{dao.closing(requireNotNull(repo.session()).scope(outlet),state.getString("closingId"))!!}
    private fun rememberSession(){val session=requireNotNull(repo.session());assertEquals(state.getString("accountId"),session.identity.id)}
    @Test fun ownedNativeProcessPhase(){
        val args=InstrumentationRegistry.getArguments();org.junit.Assume.assumeTrue("Owned native recovery only",args.getString("ccsNativeRecoveryQa")=="true")
        require(args.getString("ccsQaOrigin")=="http://10.0.2.2:4371")
        outlet=requireNotNull(args.getString("ccsQaOutlet"));OnlineApi.uuid(outlet)
        password=requireNotNull(args.getString("ccsQaPassword"));employee=requireNotNull(args.getString("ccsQaEmployee"));admin=requireNotNull(args.getString("ccsQaAdmin"));phase=requireNotNull(args.getString("ccsNativeRecoveryPhase"))
        file=File(context.filesDir,"ccs-native-recovery-$outlet.json");state=if(file.exists())JSONObject(file.readText())else JSONObject()
        if(phase=="quantitySend"){
            check(!file.exists()){ "Use a fresh owned fixture for the seed phase" }
            OnlineSessionStore(context).clear();context.getSharedPreferences("ccs_online_ui",Context.MODE_PRIVATE).edit().clear().commit()
            enter();login(employee);click("online.open");waitText("Lanjutkan input");click("online.nav.Input",false)
            val session=requireNotNull(repo.session());val scope=session.scope(outlet)
            val closing=runBlocking{dao.closingIds(scope).single()};val menu=runBlocking{dao.entries(scope,closing).first().menuId}
            state.put("closingId",closing).put("menuId",menu).put("accountId",session.identity.id)
            fill("online.qty.$menu","5");click("online.save")
            compose.waitUntil(15000){runBlocking{dao.operations(scope,closing).singleOrNull()?.status=="SENT"}}
            val op=runBlocking{dao.operations(scope,closing).single()};state.put("quantityOperation",op.operationId).put("quantityBeforeKill",op.quantity)
            killBoundary()
        }
        check(file.exists());assertEquals(outlet,state.getString("outlet"));enter()
        // Every continuation uses the persisted session loaded by the real screen's ViewModel.
        ready("online.nav.Akun");rememberSession()
        when(phase){
            "quantityRecover"->{
                waitText("Lanjutkan input");click("online.nav.Input",false);waitText("Perangkat ini tersinkron")
                val menu=state.getString("menuId");compose.onNodeWithTag("online.qty.$menu").performScrollTo().assertTextContains("5")
                val session=repo.session()!!;val scope=session.scope(outlet)
                assertTrue(runBlocking{dao.operations(scope,state.getString("closingId")).isEmpty()})
                assertEquals(1L,runBlocking{dao.entry(scope,state.getString("closingId"),menu)!!.serverRevision});capture("quantity-recovered");proof()
            }
            "lifecycleSend"->{
                waitText("Lanjutkan input");click("online.nav.Review",false);waitText("Perangkat ini tersinkron");click("online.action.submit");click("online.lifecycle.confirm",false)
                compose.waitUntil(15000){runBlocking{dao.lifecycle(repo.session()!!.scope(outlet),state.getString("closingId"))?.wasSent==true}}
                val op=runBlocking{dao.lifecycle(repo.session()!!.scope(outlet),state.getString("closingId"))!!};state.put("lifecycleOperation",op.operationId);killBoundary()
            }
            "lifecycleRecover"->{
                waitText("Lihat jumlah menu");click("online.nav.Review",false);waitText("Dikirim untuk review")
                compose.waitUntil(15000){runBlocking{dao.lifecycle(repo.session()!!.scope(outlet),state.getString("closingId"))==null}}
                assertEquals("SUBMITTED",header().state);assertEquals(2L,header().revision);capture("lifecycle-recovered");proof()
            }
            "settingsSend"->{
                account();compose.onNodeWithText("Keluar akun").performScrollTo().performClick();login(admin)
                state.put("accountId",requireNotNull(repo.session()).identity.id);account();click("online.settings.open");waitText("Versi pengaturan 0")
                fill("online.settings.name","Recovery Outlet");fill("online.settings.cutoff","03:00");click("online.settings.save");click("online.settings.confirm",false)
                compose.waitUntil(15000){runBlocking{repo.pendingSettings(outlet)?.wasSent==true}}
                state.put("settingsOperation",runBlocking{repo.pendingSettings(outlet)!!.operationId});killBoundary()
            }
            "settingsRecover"->{
                account();click("online.settings.open");ready("online.settings.retry")
                val op=runBlocking{repo.pendingSettings(outlet)!!};assertEquals(state.getString("settingsOperation"),op.operationId);assertEquals("SENT",op.status)
                click("online.settings.retry");waitText("Pengaturan terkonfirmasi");assertNull(runBlocking{repo.pendingSettings(outlet)})
                compose.onNodeWithTag("online.settings.name").performScrollTo().assertTextContains("Recovery Outlet");capture("settings-recovered");proof()
            }
            "teamSend"->{
                account();click("online.team.open");ready("online.team.create");click("online.team.create")
                val username=admin.substringBeforeLast('.')+".recovery";state.put("createdUsername",username)
                fill("online.team.username",username);fill("online.team.name","Recovery Member");fill("online.team.password",password);click("online.team.create.confirm",false)
                compose.waitUntil(15000){runBlocking{repo.pendingTeam(outlet)?.wasSent==true}}
                state.put("teamOperation",runBlocking{repo.pendingTeam(outlet)!!.operationId});killBoundary()
            }
            "teamRecover"->{
                account();click("online.team.open");ready("online.team.retry")
                val op=runBlocking{repo.pendingTeam(outlet)!!};assertEquals(state.getString("teamOperation"),op.operationId);assertEquals("SENT",op.status)
                compose.onNodeWithTag("online.team.password").assertDoesNotExist();click("online.team.retry");waitText("Tindakan tim terkonfirmasi")
                assertNull(runBlocking{repo.pendingTeam(outlet)});waitText("Recovery Member");compose.onNodeWithText("Recovery Member").performScrollTo();capture("team-recovered");proof()
            }
            "catalogDraft"->{
                account();click("online.catalog.open");waitText("Versi server 1");catalogClick("online.catalog.start");waitText("Draft perangkat tersimpan")
                catalogClick("online.catalog.edit.${state.getString("menuId")}");fill("online.catalog.form.name","Recovery Menu");click("online.catalog.form.save",false)
                compose.waitUntil(15000){compose.onAllNodesWithTag("online.catalog.form.save").fetchSemanticsNodes().isEmpty()}
                val op=runBlocking{repo.pendingCatalog(outlet)!!};assertEquals("EDITING",op.status);assertTrue(op.catalogJson.contains("Recovery Menu"));state.put("catalogOperation",op.operationId);killBoundary()
            }
            "catalogSend"->{
                account();click("online.catalog.open");waitText("Draft perangkat tersimpan");ready("online.catalog.publish")
                val op=runBlocking{repo.pendingCatalog(outlet)!!};assertEquals("EDITING",op.status);assertEquals(state.getString("catalogOperation"),op.operationId);assertTrue(op.catalogJson.contains("Recovery Menu"))
                catalogClick("online.catalog.search");fill("online.catalog.search","Recovery Menu");capture("catalog-draft-recovered")
                catalogClick("online.catalog.publish");click("online.catalog.publish.confirm",false)
                compose.waitUntil(15000){runBlocking{repo.pendingCatalog(outlet)?.wasSent==true}}
                assertEquals(op.operationId,runBlocking{repo.pendingCatalog(outlet)!!.operationId});killBoundary()
            }
            "catalogRecover"->{
                account();click("online.catalog.open");ready("online.catalog.retry")
                val op=runBlocking{repo.pendingCatalog(outlet)!!};assertEquals(state.getString("catalogOperation"),op.operationId);assertEquals("SENT",op.status)
                catalogClick("online.catalog.retry");compose.onNodeWithTag("online.catalog.list").performScrollToIndex(0);waitText("Publikasi katalog terkonfirmasi")
                assertNull(runBlocking{repo.pendingCatalog(outlet)});waitText("Versi server 2");capture("catalog-recovered")
                val session=repo.session()!!;val api=OnlineApi(OnlineEndpoint("http://10.0.2.2:4371",true))
                runBlocking{val old=JSONObject(api.fetch(session,outlet,state.getString("closingId")).header.snapshotJson)
                    assertEquals(0,old.getInt("cutoffMinutes"));assertFalse(old.getJSONObject("catalog").toString().contains("Recovery Menu"))
                    val newer=api.create(session,outlet,"2026-04-01");state.put("futureClosingId",newer.header.closingId)
                    val fresh=JSONObject(newer.header.snapshotJson);assertEquals(180,fresh.getInt("cutoffMinutes"));assertTrue(fresh.getJSONObject("catalog").toString().contains("Recovery Menu"))}
                proof()
            }
            else->error("Unknown native recovery phase")
        }
    }
}
