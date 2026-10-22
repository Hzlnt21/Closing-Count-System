package com.closingcount.app

import android.content.Context
import android.graphics.Bitmap
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

class OnlineSettingsNativeInstrumentedTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()
    private val context=ApplicationProvider.getApplicationContext<Context>()
    private lateinit var employee:String;private lateinit var admin:String;private lateinit var password:String;private lateinit var outlet:String;private lateinit var oldId:String
    @Before fun setup()=runBlocking{
        val args=InstrumentationRegistry.getArguments()
        org.junit.Assume.assumeTrue("Owned settings native QA only",args.getString("ccsSettingsUiQa")=="true")
        require(args.getString("ccsQaOrigin")=="http://10.0.2.2:4371")
        employee=requireNotNull(args.getString("ccsQaEmployee"));admin=requireNotNull(args.getString("ccsQaAdmin"));password=requireNotNull(args.getString("ccsQaPassword"));outlet=requireNotNull(args.getString("ccsQaOutlet"))
        OnlineSessionStore(context).clear();context.getSharedPreferences("ccs_online_ui",Context.MODE_PRIVATE).edit().clear().commit()
        val api=OnlineApi(OnlineEndpoint("http://10.0.2.2:4371",true));val a=api.login(employee,password)
        oldId=api.create(a,outlet,"2026-07-01").header.closingId;api.logout(a)
    }
    private fun waitText(text:String){compose.waitUntil(45000){compose.onAllNodesWithText(text,substring=true).fetchSemanticsNodes().isNotEmpty()}}
    private fun click(tag:String){val node=compose.onNodeWithTag(tag);if(tag!="online.settings.close")node.performScrollTo();node.performClick()}
    private fun login(user:String){waitText("Selamat datang kembali");compose.onNodeWithTag("online.username").performTextReplacement(user)
        compose.onNodeWithTag("online.password").performTextReplacement(password);Espresso.closeSoftKeyboard();click("online.login");waitText("Belum ada closing tersimpan")}
    private fun capture(name:String){compose.waitForIdle();val bitmap=InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
        File(context.getExternalFilesDir(null),"native-settings-$name.png").outputStream().use{bitmap.compress(Bitmap.CompressFormat.PNG,100,it)};bitmap.recycle()}
    @Test fun employeeReadsAdminValidatesReviewsAndSavesWithoutChangingHistoricalClosing(){
        compose.onNodeWithText("Closing bersama").performClick();login(employee)
        compose.onNodeWithTag("online.nav.Akun").performClick();click("online.settings.open")
        waitText("Hanya administrator outlet");compose.onNodeWithTag("online.settings.save").assertDoesNotExist()
        compose.onNodeWithText("Hanya administrator outlet yang dapat mengubah aturan ini.").performScrollTo();capture("employee-readonly")
        click("online.settings.close");compose.onNodeWithText("Keluar akun").performScrollTo().performClick();login(admin)
        compose.onNodeWithTag("online.nav.Akun").performClick();click("online.settings.open");waitText("Versi pengaturan 0")
        compose.onNodeWithTag("online.settings.name").performScrollTo().performTextReplacement("Kedai QA Profesional")
        compose.onNodeWithTag("online.settings.cutoff").performScrollTo().performTextReplacement("24:00");Espresso.closeSoftKeyboard()
        compose.onNodeWithTag("online.settings.save").performScrollTo().assertIsNotEnabled();capture("invalid-cutoff")
        compose.onNodeWithTag("online.settings.cutoff").performScrollTo().performTextReplacement("04:00");Espresso.closeSoftKeyboard()
        click("online.settings.zone");compose.onNodeWithText("WITA - Makassar").performClick()
        compose.activityRule.scenario.recreate();waitText("Isian belum disimpan dipulihkan")
        compose.onNodeWithTag("online.settings.name").assertTextContains("Kedai QA Profesional")
        compose.onNodeWithTag("online.settings.cutoff").assertTextContains("04:00");capture("form-recreated")
        click("online.settings.close");waitText("Perubahan belum disimpan");compose.onNodeWithText("Kembali mengedit").performClick()
        click("online.settings.save");waitText("Terapkan aturan baru?");capture("review")
        compose.onNodeWithTag("online.settings.confirm").performClick();waitText("Pengaturan terkonfirmasi")
        compose.onNodeWithTag("online.settings.pending").assertDoesNotExist();capture("confirmed")
        val session=(context as ClosingCountApplication).onlineRepository.session()!!
        val api=OnlineApi(OnlineEndpoint("http://10.0.2.2:4371",true))
        runBlocking{
            assertEquals(1L,api.outletSettings(session,outlet).revision)
            val old=JSONObject(api.fetch(session,outlet,oldId).header.snapshotJson)
            assertEquals("Asia/Jakarta",old.getString("timezone"));assertEquals(0,old.getInt("cutoffMinutes"))
            val newer=JSONObject(api.create(session,outlet,"2026-07-02").header.snapshotJson)
            assertEquals("Asia/Makassar",newer.getString("timezone"));assertEquals(240,newer.getInt("cutoffMinutes"))
        }
        click("online.settings.close");waitText("Kedai QA Profesional")
        compose.onNodeWithTag("online.nav.Akun").assertIsSelected()
        compose.onNodeWithText("Keluar akun").performScrollTo().performClick();waitText("Selamat datang kembali")
    }
}
