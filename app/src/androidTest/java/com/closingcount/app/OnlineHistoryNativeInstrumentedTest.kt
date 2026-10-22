package com.closingcount.app

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso
import androidx.test.platform.app.InstrumentationRegistry
import com.closingcount.app.data.online.*
import com.closingcount.app.data.local.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.util.UUID

class OnlineHistoryNativeInstrumentedTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()
    private val context=ApplicationProvider.getApplicationContext<Context>()
    private lateinit var employee:String;private lateinit var password:String;private lateinit var outlet:String
    private lateinit var closingId:String;private lateinit var menuId:String
    @Before fun seed()=runBlocking {
        val args=InstrumentationRegistry.getArguments()
        org.junit.Assume.assumeTrue("Owned history UI QA only",args.getString("ccsHistoryUiQa")=="true")
        require(args.getString("ccsQaOrigin")=="http://10.0.2.2:4371")
        employee=requireNotNull(args.getString("ccsQaEmployee"));password=requireNotNull(args.getString("ccsQaPassword"));outlet=requireNotNull(args.getString("ccsQaOutlet"))
        OnlineSessionStore(context).clear();context.getSharedPreferences("ccs_online_ui",Context.MODE_PRIVATE).edit().clear().commit()
        val api=OnlineApi(OnlineEndpoint("http://10.0.2.2:4371",true));val a=api.login(employee,password)
        val s=api.login(requireNotNull(args.getString("ccsQaSupervisor")),password)
        try {
            for(day in 1..21)api.create(a,outlet,"2026-09-"+day.toString().padStart(2,'0'))
            val initial=api.create(a,outlet,"2026-09-21");closingId=initial.header.closingId;menuId=initial.entries.first().menuId
            suspend fun quantity(value:Int){val snapshot=api.fetch(a,outlet,closingId);val row=snapshot.entries.first{it.menuId==menuId}
                api.send(a,OnlineOperationEntity(operationId=UUID.randomUUID().toString(),scope=a.scope(outlet),closingId=closingId,menuId=menuId,
                    generation=snapshot.header.generation,expectedRevision=row.serverRevision,quantity=value,status="SENT",errorCode=null))}
            suspend fun act(session:OnlineSession,action:String,reason:String=""){
                val snapshot=api.fetch(session,outlet,closingId)
                api.lifecycle(session,OnlineLifecycleEntity(session.scope(outlet),closingId,UUID.randomUUID().toString(),action,
                    snapshot.header.generation,snapshot.header.revision,reason,"SENT",true,null))}
            quantity(4);act(a,"submit");act(s,"lock");act(s,"reopen","Koreksi dari supervisor");quantity(6)
        } finally {api.logout(a);api.logout(s)}
    }
    private fun waitText(text:String){compose.waitUntil(45000){compose.onAllNodesWithText(text,substring=true).fetchSemanticsNodes().isNotEmpty()}}
    private fun click(tag:String){compose.onNodeWithTag(tag).performScrollTo().performClick()}
    private fun capture(name:String){compose.waitForIdle();val bitmap=InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
        File(context.getExternalFilesDir(null),"native-history-$name.png").outputStream().use{bitmap.compress(Bitmap.CompressFormat.PNG,100,it)};bitmap.recycle()}
    @Test fun browseServerDatesAuditAndFrozenReportThenOpenCurrentClosingThroughNativeUi(){
        compose.onNodeWithText("Closing bersama").performClick();waitText("Selamat datang kembali")
        compose.onNodeWithTag("online.username").performTextReplacement(employee);compose.onNodeWithTag("online.password").performTextInput(password)
        Espresso.closeSoftKeyboard();click("online.login");waitText("Belum ada closing tersimpan")
        compose.onNodeWithTag("online.nav.Riwayat").performClick();waitText("21 September 2026");capture("server-list")
        click("online.history.more");compose.waitUntil(45000){compose.onAllNodesWithText("1 September 2026").fetchSemanticsNodes().isNotEmpty()}
        click("online.history.detail.$closingId");waitText("Lihat laporan generasi 1");compose.waitUntil(45000){compose.onNodeWithTag("online.report.1").fetchSemanticsNode().config.contains(SemanticsProperties.Disabled).not()};capture("detail")
        click("online.report.1");waitText("Menu pada laporan")
        compose.onNodeWithTag("online.report.quantity.$menuId").performScrollTo().assertTextEquals("4");capture("frozen-report")
        click("online.report.back");waitText("Koreksi dari supervisor")
        compose.onNodeWithText("Alasan: Koreksi dari supervisor").performScrollTo();capture("audit-reopen")
        click("online.history.open");waitText("Lanjutkan input");waitText("Perangkat ini tersinkron")
        compose.onNodeWithTag("online.nav.Input").performClick();compose.onNodeWithTag("online.qty.$menuId").performScrollTo().assertTextContains("6");capture("current-six")
        val active=(context as ClosingCountApplication).onlineRepository.session()!!
        runBlocking{OnlineApi(OnlineEndpoint("http://10.0.2.2:4371",true)).logout(active)}
        compose.onNodeWithTag("online.nav.Riwayat").performClick();waitText("Jejak dan laporan belum dapat dimuat")
        compose.onNodeWithText("Masuk kembali").performScrollTo();capture("expired-session")
        compose.onNodeWithText("Masuk kembali").performClick();waitText("Selamat datang kembali")
        compose.onNodeWithTag("online.username").performTextReplacement(employee)
        compose.onNodeWithTag("online.password").performTextReplacement(password);Espresso.closeSoftKeyboard();click("online.login")
        compose.waitUntil(45000){compose.onAllNodesWithTag("online.history.detail.$closingId").fetchSemanticsNodes().isNotEmpty()}
        compose.onAllNodesWithText("belum dapat dimuat",substring=true).assertCountEquals(0);capture("session-recovered")
        compose.onNodeWithTag("online.nav.Akun").performClick();compose.onNodeWithText("Keluar akun").performScrollTo().performClick();waitText("Selamat datang kembali")
    }
}
