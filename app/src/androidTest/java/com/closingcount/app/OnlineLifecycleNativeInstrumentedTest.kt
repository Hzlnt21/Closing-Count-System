package com.closingcount.app

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso
import androidx.test.platform.app.InstrumentationRegistry
import com.closingcount.app.data.online.*
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.io.File

class OnlineLifecycleNativeInstrumentedTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()
    private val context=ApplicationProvider.getApplicationContext<Context>()
    private lateinit var employee:String
    private lateinit var supervisor:String
    private lateinit var password:String
    private lateinit var outlet:String
    @Before fun setup(){
        val args=InstrumentationRegistry.getArguments()
        org.junit.Assume.assumeTrue("Explicit owned QA only",args.getString("ccsLifecycleUiQa")=="true")
        require(args.getString("ccsQaOrigin")=="http://10.0.2.2:4371")
        employee=requireNotNull(args.getString("ccsQaEmployee"));supervisor=requireNotNull(args.getString("ccsQaSupervisor"))
        password=requireNotNull(args.getString("ccsQaPassword"));outlet=requireNotNull(args.getString("ccsQaOutlet"))
        OnlineSessionStore(context).clear();context.getSharedPreferences("ccs_online_ui",Context.MODE_PRIVATE).edit().clear().commit()
    }
    private fun waitText(value:String){compose.waitUntil(45000){compose.onAllNodesWithText(value,substring=true).fetchSemanticsNodes().isNotEmpty()}}
    private fun clickTag(tag:String){compose.onNodeWithTag(tag).performScrollTo().performClick()}
    private fun login(user:String){
        waitText("Selamat datang kembali");compose.onNodeWithTag("online.username").performTextReplacement(user)
        compose.onNodeWithTag("online.password").performTextInput(password);Espresso.closeSoftKeyboard();clickTag("online.login")
        waitText("Belum ada closing tersimpan");clickTag("online.open");waitText("Perangkat ini tersinkron")
    }
    private fun capture(name:String){
        compose.waitForIdle();val bitmap=InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
        File(context.getExternalFilesDir(null),"native-lifecycle-$name.png").outputStream().use{bitmap.compress(Bitmap.CompressFormat.PNG,100,it)};bitmap.recycle()
    }
    private fun action(action:String,reason:String?=null){
        clickTag("online.action.$action")
        if(reason!=null){
            compose.onNodeWithTag("online.lifecycle.confirm").assertIsNotEnabled()
            compose.onNodeWithTag("online.lifecycle.reason").performTextInput(reason);Espresso.closeSoftKeyboard()
            capture("$action-reason")
        }
        compose.onNodeWithTag("online.lifecycle.confirm").performClick();waitText("Perangkat ini tersinkron")
    }
    @Test fun employeeSubmitAndSupervisorReturnLockReopenThroughNativeScreens(){
        compose.onNodeWithText("Closing bersama").performClick();login(employee)
        compose.onNodeWithTag("online.nav.Input").performClick()
        val matcher=SemanticsMatcher("quantity input"){it.config.getOrNull(SemanticsProperties.TestTag)?.startsWith("online.qty.")==true}
        compose.waitUntil(45000){compose.onAllNodes(matcher).fetchSemanticsNodes().isNotEmpty()}
        val tag=compose.onAllNodes(matcher).fetchSemanticsNodes().first().config[SemanticsProperties.TestTag]
        val menu=tag.removePrefix("online.qty.")
        compose.onNodeWithTag(tag).performScrollTo().performTextReplacement("8");Espresso.closeSoftKeyboard();clickTag("online.save")
        waitText("Perangkat ini tersinkron");compose.onNodeWithTag("online.nav.Review").performClick();action("submit")
        waitText("Menunggu pemeriksaan supervisor");compose.onNodeWithTag("online.action.lock").assertDoesNotExist();capture("employee-submitted")
        compose.onNodeWithTag("online.nav.Akun").performClick();compose.onNodeWithText("Keluar akun").performScrollTo().performClick()
        login(supervisor);compose.onNodeWithTag("online.nav.Review").performClick();waitText("Dikirim oleh Dummy rani");capture("supervisor-review")
        action("return","Periksa rekap outlet")
        compose.onNodeWithTag("online.action.submit").assertExists();action("submit");action("lock")
        waitText("Dikunci oleh Dummy arif");capture("locked")
        val session=(context as ClosingCountApplication).onlineRepository.session()!!
        val api=OnlineApi(OnlineEndpoint("http://10.0.2.2:4371",true))
        val current=runBlocking{api.create(session,outlet,api.businessDate(session,outlet))}
        val report=runBlocking{api.report(session,outlet,current.header.closingId,2)}
        val frozen=JSONObject(report).getJSONObject("closing").getJSONArray("entries")
        assertEquals(8,(0 until frozen.length()).map{frozen.getJSONObject(it)}.first{it.getString("menuId")==menu}.getInt("quantity"))
        action("reopen","Koreksi jumlah setelah review")
        compose.onNodeWithTag("online.nav.Input").performClick();compose.onNodeWithTag(tag).performScrollTo().performTextReplacement("9")
        Espresso.closeSoftKeyboard();clickTag("online.save");waitText("Perangkat ini tersinkron");capture("reopened-edited")
        val latest=runBlocking{api.fetch(session,outlet,current.header.closingId)}
        assertEquals("DRAFT",latest.header.state);assertEquals(3L,latest.header.generation)
        assertEquals(9,latest.entries.first{it.menuId==menu}.serverQuantity)
        assertEquals(report,runBlocking{api.report(session,outlet,current.header.closingId,2)})
        compose.onNodeWithTag("online.nav.Akun").performClick();compose.onNodeWithText("Keluar akun").performScrollTo().performClick();waitText("Selamat datang kembali")
    }
}
