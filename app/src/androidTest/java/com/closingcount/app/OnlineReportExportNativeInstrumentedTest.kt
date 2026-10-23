package com.closingcount.app

import android.content.Context
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.view.accessibility.AccessibilityNodeInfo
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.lifecycle.ViewModelProvider
import androidx.core.view.WindowCompat
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso
import androidx.test.platform.app.InstrumentationRegistry
import com.closingcount.app.data.local.BackupSnapshot
import com.closingcount.app.data.online.OnlineSessionStore
import com.closingcount.app.ui.online.OnlineReportExportViewModel
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.*
import org.junit.Assert.*
import java.io.File
import java.security.MessageDigest
import java.util.UUID

class OnlineReportExportNativeInstrumentedTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()
    private val instrumentation=InstrumentationRegistry.getInstrumentation()
    private val app=ApplicationProvider.getApplicationContext<ClosingCountApplication>()
    private lateinit var fixture: ReportExportQaFixture
    private lateinit var exporter: OnlineReportExportViewModel
    private var legacy: BackupSnapshot?=null
    private val code=InstrumentationRegistry.getArguments().getString("ccsExportCase")?:"native-light"
    private val proof=JSONObject().put("case",code).put("passed",false)
    @Before fun setup(): Unit=runBlocking {
        Assume.assumeTrue(InstrumentationRegistry.getArguments().getString("ccsReportExportUiQa")=="true")
        require(code.matches(Regex("[a-z0-9-]{1,40}")))
        OnlineSessionStore(app).clear();app.getSharedPreferences("ccs_online_ui",Context.MODE_PRIVATE).edit().clear().commit()
        fixture=ReportExportQaFixture(app);fixture.seed();legacy=app.database.backupDao().createSnapshot()
        instrumentation.runOnMainSync { exporter=ViewModelProvider(compose.activity)[OnlineReportExportViewModel::class.java] }
    }
    @After fun cleanup(): Unit=runBlocking {
        if(::fixture.isInitialized){
            compose.activityRule.scenario.close()
            runCatching { app.onlineRepository.logout() };fixture.close();OnlineSessionStore(app).clear()
            assertEquals(legacy,app.database.backupDao().createSnapshot())
            proof.put("legacyDataUnchanged",true);save()
        }
    }
    private fun waitFor(test:()->Boolean) { compose.waitUntil(45000){runCatching(test).getOrDefault(false)} }
    private fun tag(value:String){waitFor{compose.onAllNodesWithTag(value).fetchSemanticsNodes().size==1}}
    private fun click(value:String,scroll:Boolean=true){tag(value);val item=compose.onNodeWithTag(value);if(scroll)item.performScrollTo();item.assertIsDisplayed().performClick()}
    private fun nav(page:String){if(compose.onAllNodesWithTag("online.nav.menu").fetchSemanticsNodes().isNotEmpty())click("online.nav.menu",false);click("online.nav.$page",false)}
    private fun capture(scene:String){
        if(instrumentation.uiAutomation.rootInActiveWindow?.packageName?.toString()=="com.closingcount.app")compose.waitForIdle()
        // Compose idle does not include SystemUI's status-bar appearance fade
        // after an external Activity closes. Capture the settled system window.
        instrumentation.uiAutomation.waitForIdle(400,5000)
        val image=instrumentation.uiAutomation.takeScreenshot();File(app.getExternalFilesDir(null),"report-export-$code-$scene.png").outputStream().use { image.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it) };image.recycle()
    }
    private fun save(){File(app.getExternalFilesDir(null),"report-export-$code.json").writeText(proof.toString(2))}
    private fun shell(cmd:String)=ParcelFileDescriptor.AutoCloseInputStream(instrumentation.uiAutomation.executeShellCommand(cmd)).use{it.readBytes()}
    private fun node(predicate:(AccessibilityNodeInfo)->Boolean):AccessibilityNodeInfo?{
        fun find(n:AccessibilityNodeInfo?):AccessibilityNodeInfo?{n?:return null;if(n.isVisibleToUser&&predicate(n))return n
            for(i in 0 until n.childCount)find(n.getChild(i))?.let{return it};return null}
        return find(instrumentation.uiAutomation.rootInActiveWindow)
    }
    private fun systemClick(predicate:(AccessibilityNodeInfo)->Boolean){waitFor{node(predicate)!=null};var target=requireNotNull(node(predicate))
        while(!target.isClickable)target=target.parent?:error("No clickable system parent")
        check(target.isEnabled&&target.performAction(AccessibilityNodeInfo.ACTION_CLICK))}
    private fun picker(){waitFor{instrumentation.uiAutomation.rootInActiveWindow?.packageName?.toString()?.contains("documentsui")==true}}
    private fun cancelPicker(){
        picker();shell("input keyevent 4")
        waitFor{instrumentation.uiAutomation.rootInActiveWindow?.packageName?.toString()=="com.closingcount.app"}
    }
    private fun saveAs(name:String){
        require(name.matches(Regex("ccs-qa-report-[a-f0-9-]+\\.xlsx")))
        picker()
        waitFor{node{it.className?.toString()?.endsWith("EditText")==true}!=null}
        check(requireNotNull(node{it.className?.toString()?.endsWith("EditText")==true}).performAction(AccessibilityNodeInfo.ACTION_SET_TEXT,
            Bundle().apply{putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,name)}))
        systemClick{it.isEnabled&&it.text?.toString()?.lowercase() in listOf("save","simpan")}
    }
    @Test fun exportsSelectedLockedGenerationThroughRealPickerAndRechecksExpiredAccess(): Unit=runBlocking {
        try{
            compose.onNodeWithText("Closing bersama").performClick();tag("online.username")
            compose.onNodeWithTag("online.username").performTextReplacement(fixture.username)
            compose.onNodeWithTag("online.password").performTextReplacement(fixture.password);Espresso.closeSoftKeyboard();click("online.login")
            waitFor{compose.onAllNodesWithText("Belum ada closing tersimpan",substring=true).fetchSemanticsNodes().isNotEmpty()}
            nav("Riwayat");click("online.history.detail.${fixture.closingId}")
            tag("online.report.1");waitFor{!compose.onNodeWithTag("online.report.1").fetchSemanticsNode().config.contains(SemanticsProperties.Disabled)}
            click("online.report.1");tag("online.report.export")
            compose.onNodeWithTag("online.report.quantity.${fixture.menuId}").performScrollTo().assertTextEquals("4");capture("frozen-four")
            click("online.report.export");picker();capture("picker");cancelPicker()
            waitFor{!exporter.state.value.busy};assertNull(exporter.state.value.message)
            click("online.report.export");picker()
            val request=requireNotNull(exporter.state.value.request)
            // ActivityScenario.recreate waits for the previous lifecycle state and
            // can block while an external picker owns the foreground. Request the
            // actual Activity recreation on main, then let the picker finish.
            val originalActivity=compose.activity
            instrumentation.runOnMainSync{originalActivity.recreate()};picker()
            assertEquals(request,exporter.state.value.request)
            instrumentation.runOnMainSync{assertFalse(exporter.claim(request.id))}
            val filename="ccs-qa-report-${UUID.randomUUID()}.xlsx"
            saveAs(filename)
            waitFor{!exporter.state.value.busy&&exporter.state.value.message?.contains("berhasil disimpan")==true}
            waitFor{compose.activity!==originalActivity}
            instrumentation.runOnMainSync {
                val activity=compose.activity
                val dark=activity.resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK==android.content.res.Configuration.UI_MODE_NIGHT_YES
                assertEquals(!dark,WindowCompat.getInsetsController(activity.window,activity.window.decorView).isAppearanceLightStatusBars)
            }
            val bytes=shell("cat /sdcard/Download/$filename")
            assertTrue(bytes.size>1000&&bytes[0]=='P'.code.toByte()&&bytes[1]=='K'.code.toByte())
            File(app.getExternalFilesDir(null),"report-export-$code-source.json").writeText(fixture.raw)
            File(app.getExternalFilesDir(null),"report-export-$code-current.json").writeText(fixture.api.fetch(fixture.employee,fixture.outlet,fixture.closingId).header.snapshotJson)
            proof.put("filename",filename).put("sha256",MessageDigest.getInstance("SHA-256").digest(bytes).joinToString(""){"%02x".format(it)})
                .put("closingId",fixture.closingId).put("reportId",fixture.report.id).put("menuId",fixture.menuId).put("pickerRecreatedWithoutRelaunch",true)
            capture("saved")
            click("online.report.export");picker()
            fixture.api.logout(requireNotNull(app.onlineRepository.session()))
            val denied="ccs-qa-report-${UUID.randomUUID()}.xlsx";saveAs(denied)
            waitFor{!exporter.state.value.busy&&exporter.state.value.needsLogin}
            compose.onNodeWithTag("online.report.export.login").performScrollTo().assertIsDisplayed()
            val empty=shell("cat /sdcard/Download/$denied");assertEquals(0,empty.size)
            compose.onNodeWithTag("online.report.quantity.${fixture.menuId}").performScrollTo().assertTextEquals("4")
            capture("expired-access")
            val c=app.resources.configuration
            proof.put("passed",true).put("expiredPlaceholder",denied).put("expiredBytes",0).put("fontScale",c.fontScale).put("widthDp",c.screenWidthDp).put("heightDp",c.screenHeightDp)
                .put("dark",c.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK==android.content.res.Configuration.UI_MODE_NIGHT_YES)
                .put("physicalPhoneAccessed",false).put("publicAcceptance",false);save()
        }catch(error:Throwable){capture("failure");save();throw error}
    }
}
