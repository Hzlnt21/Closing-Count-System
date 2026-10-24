package com.closingcount.app

import android.graphics.Bitmap
import android.os.ParcelFileDescriptor
import android.os.Process
import android.os.SystemClock
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowCompat
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.closingcount.app.data.online.*
import com.closingcount.app.data.transfer.BackupJsonCodec
import com.closingcount.app.ui.online.OnlineViewModel
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

/** Read-only closing presentation; old APK is an explicit failing control. */
class OnlineDemoOrderingInstrumentedTest {
    @get:Rule val compose=createEmptyComposeRule()
    private val instrumentation=InstrumentationRegistry.getInstrumentation()
    private val args=InstrumentationRegistry.getArguments()
    private val app=ApplicationProvider.getApplicationContext<ClosingCountApplication>()
    private lateinit var scenario:ActivityScenario<MainActivity>
    private lateinit var online:OnlineViewModel
    private lateinit var output:File
    private lateinit var legacyBefore:String
    private var job:Job?=null
    private val proof=JSONObject().put("passed",false).put("physicalPhoneAccessed",false)
    private fun arg(key:String)=requireNotNull(args.getString("ccsOrder$key"))
    private fun hash(bytes:ByteArray)=MessageDigest.getInstance("SHA-256").digest(bytes).joinToString(""){"%02x".format(it)}
    private fun legacy()=runBlocking{hash(BackupJsonCodec.encode(app.database.backupDao().createSnapshot(),0))}
    private fun save(){if(::output.isInitialized)File(output,"proof.json").writeText(proof.toString(2))}
    private fun waitFor(block:()->Boolean){compose.waitUntil(45000){runCatching(block).getOrDefault(false)}}
    private fun exists(tag:String)=compose.onAllNodesWithTag(tag).fetchSemanticsNodes().size==1
    private fun click(tag:String,scroll:Boolean=true){waitFor{exists(tag)};val node=compose.onNodeWithTag(tag);if(scroll)node.performScrollTo();node.assertIsDisplayed().assertIsEnabled().performClick()}
    private fun nav(page:String){if(exists("online.nav.menu"))click("online.nav.menu",false);click("online.nav.$page",false)}
    private fun shell(command:String)=ParcelFileDescriptor.AutoCloseInputStream(instrumentation.uiAutomation.executeShellCommand(command)).use{it.readBytes()}
    private fun fill(tag:String,value:String){
        compose.onNodeWithTag(tag).performScrollTo().performTextReplacement(value);compose.waitForIdle()
        // Target this Activity's IME, not global dumpsys state from another user.
        // Avoid shell Back events while another window may own focus.
        var hiddenSince=0L
        waitFor {
            val now=SystemClock.uptimeMillis()
            var visible=false
            scenario.onActivity{
                WindowCompat.getInsetsController(it.window,it.window.decorView).hide(WindowInsetsCompat.Type.ime())
                visible=ViewCompat.getRootWindowInsets(it.window.decorView)?.isVisible(WindowInsetsCompat.Type.ime())==true
            }
            if(visible)hiddenSince=0 else if(hiddenSince==0L)hiddenSince=now
            hiddenSince!=0L&&now-hiddenSince>=750
        }
    }
    private fun snap(name:String){compose.waitForIdle();instrumentation.waitForIdleSync();val bitmap=requireNotNull(instrumentation.uiAutomation.takeScreenshot());File(output,"$name.png").outputStream().use{check(bitmap.compress(Bitmap.CompressFormat.PNG,100,it))};bitmap.recycle()}
    private fun above(first:String,second:String){assertTrue("Displayed catalog order",compose.onNodeWithTag(first).fetchSemanticsNode().positionInRoot.y<compose.onNodeWithTag(second).fetchSemanticsNode().positionInRoot.y)}
    @Before fun guard(){
        check(arg("Qa")=="true"&&BuildConfig.DEBUG&&BuildConfig.ONLINE_API_ORIGIN=="http://10.0.2.2:4372")
        val user=arg("User").toInt();check(user>0&&Process.myUid()/100000==user)
        val run=arg("Run");check(UUID.fromString(run).toString()==run)
        check(arg("Case") in listOf("control","fixed"));LocalDate.parse(arg("Date"))
        val version=app.packageManager.getPackageInfo(app.packageName,0).versionName;assertEquals(arg("Version"),version)
        runBlocking{app.onlineRepository.initialize()};assertNull(app.onlineRepository.session());legacyBefore=legacy()
        output=File(app.filesDir,"native-order-$run").apply{check(!exists());mkdirs()}
        proof.put("run",run).put("case",arg("Case")).put("user",user).put("uid",Process.myUid()).put("pid",Process.myPid()).put("version",version).put("legacyBefore",legacyBefore)
        scenario=ActivityScenario.launch(MainActivity::class.java)
        waitFor{exists("online.username")||compose.onAllNodesWithText("Closing bersama").fetchSemanticsNodes().isNotEmpty()}
        if(!exists("online.username"))compose.onNodeWithText("Closing bersama").performScrollTo().performClick()
        scenario.onActivity{online=ViewModelProvider(it)[OnlineViewModel::class.java];job=online.viewModelScope.coroutineContext[Job]}
        waitFor{online.state.value.initialized&&exists("online.username")}
    }
    @After fun close()=runBlocking{
        if(::scenario.isInitialized){
            if(::online.isInitialized&&!online.state.value.loginVisible){
                nav("Akun");compose.onNodeWithText("Keluar akun").performScrollTo().performClick();waitFor{online.state.value.loginVisible};proof.put("uiLogout",true)
            }
            scenario.close();job?.join()
        }
        if(::legacyBefore.isInitialized){assertEquals(legacyBefore,legacy());proof.put("legacyAfter",legacy());save()}
    }
    @Test fun configuredOrderIsConsistentAcrossInputReviewAndFrozenReport(){
        val capp=OnlineApi.uuid(arg("Cappuccino"));val latte=OnlineApi.uuid(arg("Latte"));val outlet=OnlineApi.uuid(arg("Outlet"))
        try {
            fill("online.username",arg("Username"));fill("online.password",arg("Password"));click("online.login")
            waitFor{!online.state.value.loginVisible&&!online.state.value.working};assertEquals(arg("Actor"),app.onlineRepository.session()?.identity?.id)
            nav("Ringkasan");waitFor{online.state.value.outletId==outlet&&online.state.value.date==arg("Date")};click("online.open")
            waitFor{online.state.value.closingId==arg("Current")&&online.state.value.syncVerified};nav("Input")
            val api=OnlineApi(OnlineEndpoint("http://10.0.2.2:4372",true));val session=requireNotNull(app.onlineRepository.session())
            val catalog=runBlocking{api.catalog(session,outlet)}.catalog
            assertEquals(listOf(latte,capp),catalog.menus.map{it.id}) // known UUID-order counterexample, not a random witness
            val observed=online.state.value.menus.map{it.id};proof.put("rawMenuOrder",JSONArray(catalog.menus.map{it.id})).put("inputOrder",JSONArray(observed)).put("expectedOrder",JSONArray(listOf(capp,latte)));snap("input")
            assertEquals("Configured menu order",listOf(capp,latte),observed)
            above("online.qty.$capp","online.qty.$latte");assertEquals(3,online.state.value.menus.single{it.id==capp}.quantity);assertEquals(5,online.state.value.menus.single{it.id==latte}.quantity)
            nav("Review");assertEquals(listOf("Cappuccino","Latte"),online.state.value.reviewMenus.map{it.name})
            assertEquals(listOf("Biji kopi","Fresh milk","Powder cokelat"),online.state.value.ingredients.map{it.name});snap("review")
            nav("Riwayat");click("online.history.detail.${arg("History")}");click("online.report.1");waitFor{online.state.value.frozenReport!=null}
            above("online.report.quantity.$capp","online.report.quantity.$latte")
            compose.onNodeWithTag("online.report.quantity.$capp").performScrollTo().assertTextEquals("6")
            compose.onNodeWithTag("online.report.quantity.$latte").assertTextEquals("4");snap("report-menus")
            val ingredientIds=listOf(arg("Coffee"),arg("Milk"),arg("Cocoa"))
            above("online.report.ingredient.${ingredientIds[0]}","online.report.ingredient.${ingredientIds[1]}")
            above("online.report.ingredient.${ingredientIds[1]}","online.report.ingredient.${ingredientIds[2]}")
            ingredientIds.zip(listOf("10","10","6")).forEach{(id,total)->compose.onNodeWithTag("online.report.ingredient.$id").assertTextEquals(total)}
            compose.onNodeWithTag("online.report.ingredient.${ingredientIds.last()}").performScrollTo();snap("report-ingredients")
            proof.put("frozenReportSha256",hash(requireNotNull(online.state.value.frozenReport).toByteArray())).put("pagesVerified",JSONArray(listOf("input","review","frozenReport"))).put("passed",true);save()
        }catch(error:Throwable){runCatching{snap("failure")};save();throw error}
    }
}
