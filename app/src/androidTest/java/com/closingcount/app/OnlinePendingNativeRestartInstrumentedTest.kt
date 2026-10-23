package com.closingcount.app

import android.os.Bundle
import android.os.Process
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.viewModelScope
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.closingcount.app.data.online.*
import com.closingcount.app.data.transfer.*
import com.closingcount.app.ui.online.OnlinePendingTransferViewModel
import com.closingcount.app.ui.online.OnlineViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.*
import org.junit.Assert.*
import java.io.File
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Host force-stops between invocations; only a new owned secondary Android user is allowed. */
class OnlinePendingNativeRestartInstrumentedTest {
    @get:Rule val compose=createEmptyComposeRule()
    private val instrumentation=InstrumentationRegistry.getInstrumentation()
    private val app=ApplicationProvider.getApplicationContext<ClosingCountApplication>()
    private lateinit var scenario:ActivityScenario<MainActivity>
    private lateinit var pending:OnlinePendingTransferViewModel
    private lateinit var online:OnlineViewModel
    private lateinit var api:OnlineApi;private lateinit var session:OnlineSession
    private lateinit var marker:File;private lateinit var saved:JSONObject
    private lateinit var run:String;private lateinit var mode:String;private lateinit var outlet:String
    private val jobs=mutableListOf<Job>()
    private val proof=JSONObject().put("passed",false).put("physicalPhoneAccessed",false)
    @Before fun guard():Unit=runBlocking {
        val args=InstrumentationRegistry.getArguments()
        check(args.getString("ccsNativeStopQa")=="true")
        run=requireNotNull(args.getString("ccsNativeStopRun"));check(UUID.fromString(run).toString()==run)
        mode=requireNotNull(args.getString("ccsNativeStopMode"));check(mode in setOf("stage-review","stage-draft","verify"))
        val user=requireNotNull(args.getString("ccsNativeStopUser")).toInt();check(user>0&&Process.myUid()/100000==user)
        check(args.getString("ccsQaOrigin")=="http://10.0.2.2:4371")
        outlet=OnlineApi.uuid(requireNotNull(args.getString("ccsQaOutlet")))
        api=OnlineApi(OnlineEndpoint("http://10.0.2.2:4371",true),4000)
        marker=File(app.noBackupFilesDir,"native-pending-stop-qa-$run.json")
        app.onlineRepository.initialize()
        if(mode=="stage-review") {
            check(!marker.exists());assertNull(app.onlineRepository.session());assertTrue(OnlinePendingRecoveryInbox(app).inventory().isEmpty())
            session=api.login(requireNotNull(args.getString("ccsQaEmployee")),requireNotNull(args.getString("ccsQaPassword")))
            app.onlineRepository.activate(session,true)
            assertTrue(app.database.onlineSyncDao().closingIds(session.scope(outlet)).isEmpty())
            saved=JSONObject().put("run",run).put("outlet",outlet).put("actor",session.identity.id)
        } else {
            saved=JSONObject(marker.readText());check(saved.getString("run")==run&&saved.getString("outlet")==outlet)
            session=requireNotNull(app.onlineRepository.session())
            assertEquals(saved.getString("actor"),session.identity.id);assertEquals("http://10.0.2.2:4371",session.origin)
            assertEquals(requireNotNull(args.getString("ccsQaEmployee")),session.identity.username)
            assertEquals(saved.getString("legacySha256"),legacyHash())
        }
        scenario=ActivityScenario.launch(MainActivity::class.java)
        scenario.onActivity {activity->
            pending=ViewModelProvider(activity)[OnlinePendingTransferViewModel::class.java]
            online=ViewModelProvider(activity)[OnlineViewModel::class.java]
            listOf(pending,online).forEach{it.viewModelScope.coroutineContext[Job]?.let(jobs::add)}
        }
        compose.onNodeWithText("Closing bersama").assertIsDisplayed()
        assertFalse(pending.state.value.opened);assertNull(pending.state.value.quantity);assertNull(pending.state.value.importing)
        if(mode=="stage-review")saved.put("legacySha256",legacyHash())
        proof.put("run",run).put("mode",mode).put("uid",Process.myUid()).put("pid",Process.myPid()).put("user",user)
            .put("outlet",outlet).put("actor",session.identity.id).put("freshPrivateAuthority",true)
    }
    @After fun close():Unit=runBlocking {
        if(::scenario.isInitialized){scenario.close();jobs.forEach{it.join()}}
        // Persistent test state intentionally remains only in the owned secondary installation.
        // The host validates/removes that exact user after the final invocation or a failure.
    }
    private fun sha(bytes:ByteArray)=MessageDigest.getInstance("SHA-256").digest(bytes).joinToString(""){"%02x".format(it)}
    private suspend fun legacyHash()=sha(BackupJsonCodec.encode(app.database.backupDao().createSnapshot(),0))
    private fun waitFor(check:()->Boolean){compose.waitUntil(45000){runCatching(check).getOrDefault(false)}}
    private fun click(tag:String,scroll:Boolean=true){
        if(scroll&&compose.onAllNodesWithTag("online.pending.page").fetchSemanticsNodes().isNotEmpty())compose.onNodeWithTag("online.pending.page").performScrollToNode(hasTestTag(tag))
        waitFor{compose.onAllNodesWithTag(tag).fetchSemanticsNodes().size==1}
        val node=compose.onNodeWithTag(tag);if(scroll)node.performScrollTo();node.assertIsEnabled().assertIsDisplayed().performClick()
    }
    private fun nav(page:String){if(compose.onAllNodesWithTag("online.nav.menu").fetchSemanticsNodes().isNotEmpty())click("online.nav.menu",false);click("online.nav.$page",false)}
    private fun enterOnline(){compose.onNodeWithText("Closing bersama").performClick();waitFor{online.state.value.initialized&&online.state.value.identity!=null}}
    private fun inbox(){nav("Akun");click("online.pending.open");waitFor{pending.state.value.opened&&!pending.state.value.busy}}
    private fun inspect(){click("online.pending.inbox.${saved.getString("fingerprint")}");waitFor{pending.state.value.model!=null&&!pending.state.value.busy};click("online.pending.compare.${saved.getString("closing")}");waitFor{pending.state.value.quantity!=null&&!pending.state.value.busy}}
    private fun capture(scene:String){
        compose.waitForIdle();var image:android.graphics.Bitmap?=null
        repeat(5){if(image==null){image=instrumentation.uiAutomation.takeScreenshot();if(image==null)Thread.sleep(250)}}
        val bitmap=requireNotNull(image)
        File(app.filesDir,"native-pending-stop-$mode-$scene.png").outputStream().use{check(bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it))};bitmap.recycle()
    }
    private fun finish(){
        assertEquals(Lifecycle.State.RESUMED,scenario.state)
        proof.put("passed",true).put("waitingForHostStop",mode!="verify").put("activityResumed",true)
        File(app.filesDir,"native-pending-stop-$mode.json").writeText(proof.toString(2))
        instrumentation.sendStatus(2,Bundle().apply{putString("ccsNativeStopProof",proof.toString())})
    }
    @Test fun acceptedInboxAndHeldDraftSurviveActualHostProcessStops():Unit=runBlocking {
        val dao=app.database.onlineSyncDao();val scope=session.scope(outlet)
        if(mode=="stage-review") {
            val initial=api.create(session,outlet,"2026-07-05");val id=initial.header.closingId;val menu=initial.entries.first().menuId
            val original=UUID.randomUUID().toString();val tail=UUID.randomUUID().toString()
            dao.recoverQuantities(initial.header,initial.entries,"2026-07-05",mapOf(menu to 4),mapOf(original to menu),emptySet(),"b".repeat(64))
            assertEquals(OnlineSyncResult.Synced,app.onlineRepository.sync(outlet,id))
            val name=JSONObject(initial.header.snapshotJson).getJSONObject("catalog").getJSONArray("menus").let{rows->(0 until rows.length()).map{rows.getJSONObject(it)}.first{it.getString("id")==menu}.getString("name")}
            val packet=OnlinePendingBackup(UUID.randomUUID().toString(),"2026-10-23T00:00:00Z",UUID.randomUUID().toString(),session.origin,
                PendingBackupAccount(session.identity.id,session.identity.username,session.identity.displayName),PendingBackupOutlet(outlet,"Outlet restart QA"),
                listOf(PendingBackupClosing(id,"2026-07-05",1,0,"DRAFT",listOf(PendingBackupEntry(menu,name,0,0,8,"PENDING")),
                    listOf(PendingBackupQuantity(original,menu,1,0,4,"SENT"),PendingBackupQuantity(tail,menu,1,null,8,"QUEUED")),null)),emptyList())
            val accepted=OnlinePendingRecoveryInbox(app).accept(OnlinePendingBackupCodec.encode(packet))
            app.getSharedPreferences("ccs_online_ui",android.content.Context.MODE_PRIVATE).edit().clear()
                .putString("scope",scope).putString("outlet",outlet).putString("date","2026-07-05").commit()
            instrumentation.runOnMainSync{online.selectOutlet(outlet)}
            saved.put("closing",id).put("menu",menu).put("fingerprint",accepted.summary.sha256).put("oldTail",tail)
            enterOnline();inbox();inspect()
            val review=requireNotNull(pending.state.value.quantity);assertEquals(4,review.rows.single().serverQuantity);assertEquals(8,review.rows.single().desired)
            saved.put("reviewTicket",review.id).put("reviewPid",Process.myPid());marker.writeText(saved.toString())
            assertTrue(dao.operations(scope,id).isEmpty());capture("review")
        } else {
            val id=saved.getString("closing");val menu=saved.getString("menu")
            assertNotEquals(saved.getInt("reviewPid"),Process.myPid())
            assertEquals(saved.getString("fingerprint"),OnlinePendingRecoveryInbox(app).list().single().sha256)
            assertEquals(4,api.fetch(session,outlet,id).entries.first{it.menuId==menu}.serverQuantity)
            enterOnline()
            if(mode=="stage-draft") {
                inbox();inspect();val review=requireNotNull(pending.state.value.quantity)
                assertNotEquals(saved.getString("reviewTicket"),review.id)
                click("online.pending.apply.quantity");waitFor{pending.state.value.destination!=null&&!pending.state.value.busy}
                click("online.pending.continue");waitFor{!pending.state.value.opened&&online.state.value.closingId==id}
                val operation=dao.operations(scope,id).single();assertEquals(8,operation.quantity);assertEquals("QUEUED",operation.status)
                assertEquals(4,api.fetch(session,outlet,id).entries.first{it.menuId==menu}.serverQuantity)
                saved.put("draftId",operation.operationId).put("draftPid",Process.myPid());marker.writeText(saved.toString());capture("held-draft")
                proof.put("freshReviewRequired",true).put("noAutomaticSend",true).put("newOperation",operation.operationId)
            } else {
                assertNotEquals(saved.getInt("draftPid"),Process.myPid());nav("Input")
                waitFor{online.state.value.closingId==id&&!online.state.value.syncing}
                val operation=dao.operations(scope,id).single();assertEquals(saved.getString("draftId"),operation.operationId);assertEquals("QUEUED",operation.status)
                assertEquals(8,dao.entries(scope,id).first{it.menuId==menu}.localQuantity)
                assertEquals(4,api.fetch(session,outlet,id).entries.first{it.menuId==menu}.serverQuantity);capture("held-after-stop")
                click("online.sync");waitFor{runBlocking{dao.operations(scope,id).isEmpty()&&!online.state.value.syncing&&api.fetch(session,outlet,id).entries.first{it.menuId==menu}.serverQuantity==8}}
                assertTrue(dao.operations(scope,id).isEmpty());assertTrue(dao.recoveryClaims(scope,listOf(saved.getString("oldTail"))).isNotEmpty())
                inbox();inspect();assertTrue(pending.state.value.quantity!!.rows.all{it.disposition=="ALREADY_RECOVERED_LOCALLY"});capture("already-recovered")
                proof.put("heldDraftSurvivedProcessStop",true).put("explicitFinalQuantity",8).put("provenanceSurvived",true).put("localQueueAcknowledged",true)
                api.logout(session)
            }
            proof.put("closing",id).put("menu",menu).put("fingerprint",saved.getString("fingerprint"))
        }
        proof.put("closing",saved.getString("closing")).put("menu",saved.getString("menu")).put("fingerprint",saved.getString("fingerprint"))
        assertEquals(saved.getString("legacySha256"),legacyHash());proof.put("legacyUnchanged",true);finish()
        if(mode!="verify"){
            CountDownLatch(1).await(50,TimeUnit.SECONDS)
            fail("Host did not stop the process while native review/draft was live")
        }
    }
}
