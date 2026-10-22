package com.closingcount.app
import android.content.Context
import android.graphics.Bitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.semantics.SemanticsProperties
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
import java.util.UUID

class OnlineTeamNativeInstrumentedTest{
 @get:Rule val compose=createAndroidComposeRule<MainActivity>()
 private val context=ApplicationProvider.getApplicationContext<Context>();private lateinit var employee:String;private lateinit var admin:String;private lateinit var password:String;private lateinit var outlet:String
 @Before fun setup(){val args=InstrumentationRegistry.getArguments();org.junit.Assume.assumeTrue("Owned team native QA only",args.getString("ccsTeamUiQa")=="true")
  require(args.getString("ccsQaOrigin")=="http://10.0.2.2:4371");employee=requireNotNull(args.getString("ccsQaEmployee"));admin=requireNotNull(args.getString("ccsQaAdmin"));password=requireNotNull(args.getString("ccsQaPassword"));outlet=requireNotNull(args.getString("ccsQaOutlet"))
  OnlineSessionStore(context).clear();context.getSharedPreferences("ccs_online_ui",Context.MODE_PRIVATE).edit().clear().commit()
 }
 private fun waitText(text:String){compose.waitUntil(45000){compose.onAllNodesWithText(text,substring=true).fetchSemanticsNodes().isNotEmpty()}}
 private fun ready(tag:String){compose.waitUntil(45000){val nodes=compose.onAllNodesWithTag(tag).fetchSemanticsNodes();nodes.size==1&&!nodes[0].config.contains(SemanticsProperties.Disabled)}}
 private fun click(tag:String){ready(tag);val node=compose.onNodeWithTag(tag);if(tag !in setOf("online.team.close","online.team.role","online.team.edit.review")&&!tag.startsWith("online.team.role."))node.performScrollTo();node.performClick()}
 private fun login(user:String){waitText("Selamat datang kembali");compose.onNodeWithTag("online.username").performTextReplacement(user);compose.onNodeWithTag("online.password").performTextReplacement(password);Espresso.closeSoftKeyboard();click("online.login");waitText("Belum ada closing tersimpan")}
 private fun capture(name:String){compose.waitForIdle();val bitmap=InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot();File(context.getExternalFilesDir(null),"native-team-$name.png").outputStream().use{bitmap.compress(Bitmap.CompressFormat.PNG,100,it)};bitmap.recycle()}
 @Test fun createAndChangeTeamAccessWithLastAdminProtectionPaginationAndOwnRoleRefresh(){
  compose.onNodeWithText("Closing bersama").performClick();login(employee);compose.onNodeWithTag("online.nav.Akun").performClick();compose.onNodeWithTag("online.team.open").assertDoesNotExist()
  compose.onNodeWithText("Keluar akun").performScrollTo().performClick();login(admin);compose.onNodeWithTag("online.nav.Akun").performClick();click("online.team.open");waitText("Dummy admin");capture("roster")
  val session=(context as ClosingCountApplication).onlineRepository.session()!!;val api=OnlineApi(OnlineEndpoint("http://10.0.2.2:4371",true))
  click("online.team.edit.${session.identity.id}");click("online.team.role");click("online.team.role.EMPLOYEE");click("online.team.edit.review");compose.onNodeWithTag("online.team.edit.confirm").performClick()
  waitText("Minimal satu administrator aktif");capture("last-admin");click("online.team.dismiss");click("online.team.reload");waitText("Dummy admin")
  click("online.team.create");compose.onNodeWithTag("online.team.username").performScrollTo().performTextInput(admin.substringBeforeLast('.')+".uat")
  compose.onNodeWithTag("online.team.name").performScrollTo().performTextInput("UAT Member");compose.onNodeWithTag("online.team.password").performScrollTo().performTextInput("short");Espresso.closeSoftKeyboard()
  compose.onNodeWithTag("online.team.create.confirm").assertIsNotEnabled();compose.onNodeWithTag("online.team.password").performTextReplacement(password);Espresso.closeSoftKeyboard();capture("create-review")
  compose.onNodeWithTag("online.team.create.confirm").performClick();waitText("Tindakan tim terkonfirmasi")
  val member=runBlocking{api.team(session,outlet,null,100).items.first{it.username==admin.substringBeforeLast('.')+".uat"}}
  click("online.team.more");ready("online.team.edit.${member.id}");compose.onNodeWithTag("online.team.edit.${member.id}").performScrollTo();capture("created-member")
  click("online.team.edit.${member.id}");click("online.team.role");click("online.team.role.SUPERVISOR");compose.onNodeWithTag("online.team.active").performClick()
  click("online.team.edit.review");capture("review-access");compose.onNodeWithTag("online.team.edit.confirm").performClick();waitText("Tindakan tim terkonfirmasi")
  click("online.team.more");waitText("Akses outlet nonaktif");compose.onNodeWithText("Akses outlet nonaktif").performScrollTo();capture("access-updated")
  runBlocking{val m=api.member(session,outlet,member.id);assertEquals("SUPERVISOR",m.role);assertFalse(m.active);assertEquals(1L,m.revision)
   val op=OnlineTeamOperation(session.scope(outlet),UUID.randomUUID().toString(),"CREATE_ACCOUNT",JSONObject().put("username",admin.substringBeforeLast('.')+".backup").put("displayName","Backup Admin").put("role","ADMIN").toString(),"SENT",true);api.sendTeam(session,op,password)}
  click("online.team.reload");waitText("Dummy admin");click("online.team.edit.${session.identity.id}");click("online.team.role");click("online.team.role.SUPERVISOR");click("online.team.edit.review");compose.onNodeWithTag("online.team.edit.confirm").performClick()
  waitText("Akses akunmu sudah diperbarui");capture("own-access");click("online.team.access.close")
  compose.waitUntil(45000){compose.onAllNodesWithTag("online.team.open").fetchSemanticsNodes().isEmpty()};compose.onNodeWithTag("online.team.close").assertDoesNotExist();waitText("Supervisor");capture("account-refreshed")
  compose.onNodeWithText("Keluar akun").performScrollTo().performClick();waitText("Selamat datang kembali")
 }
}
