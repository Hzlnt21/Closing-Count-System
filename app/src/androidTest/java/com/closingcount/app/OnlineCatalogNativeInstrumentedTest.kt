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
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.io.File

class OnlineCatalogNativeInstrumentedTest{
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()
    private val context=ApplicationProvider.getApplicationContext<Context>()
    private lateinit var employee:String;private lateinit var admin:String;private lateinit var password:String;private lateinit var outlet:String
    @Before fun setup(){val args=InstrumentationRegistry.getArguments();org.junit.Assume.assumeTrue("Owned catalog UI QA only",args.getString("ccsCatalogUiQa")=="true")
        require(args.getString("ccsQaOrigin")=="http://10.0.2.2:4371");employee=requireNotNull(args.getString("ccsQaEmployee"));admin=requireNotNull(args.getString("ccsQaAdmin"));password=requireNotNull(args.getString("ccsQaPassword"));outlet=requireNotNull(args.getString("ccsQaOutlet"))
        OnlineSessionStore(context).clear();context.getSharedPreferences("ccs_online_ui",Context.MODE_PRIVATE).edit().clear().commit()}
    private fun waitText(text:String){compose.waitUntil(45000){compose.onAllNodesWithText(text,substring=true).fetchSemanticsNodes().isNotEmpty()}}
    private fun exists(tag:String){compose.waitUntil(45000){compose.onAllNodesWithTag(tag).fetchSemanticsNodes().size==1}}
    private fun main(tag:String){compose.onNodeWithTag("online.catalog.list").performScrollToNode(hasTestTag(tag));click(tag)}
    private fun ready(tag:String){compose.waitUntil(45000){val nodes=compose.onAllNodesWithTag(tag).fetchSemanticsNodes();nodes.size==1&&!nodes[0].config.contains(SemanticsProperties.Disabled)}}
    private fun click(tag:String){ready(tag);compose.onNodeWithTag(tag).performClick()}
    private fun fill(tag:String,value:String){exists(tag);compose.onNodeWithTag(tag).performScrollTo().performTextReplacement(value);Espresso.closeSoftKeyboard()}
    private fun login(user:String){waitText("Selamat datang kembali");fill("online.username",user);fill("online.password",password);click("online.login");waitText("Belum ada closing tersimpan")}
    private fun open(){compose.onNodeWithTag("online.nav.Akun").performClick();compose.onNodeWithTag("online.catalog.open").performScrollTo().performClick();waitText("Versi server 1")}
    private fun capture(name:String){compose.waitForIdle();val bitmap=InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot();File(context.getExternalFilesDir(null),"native-catalog-$name.png").outputStream().use{bitmap.compress(Bitmap.CompressFormat.PNG,100,it)};bitmap.recycle()}
    private fun draft():OnlineCatalog=runBlocking{OnlineCatalog.parse(JSONObject((context as ClosingCountApplication).onlineRepository.pendingCatalog(outlet)!!.catalogJson))}
    private fun add(kind:Int,name:String){main("online.catalog.section.$kind");main("online.catalog.add");fill("online.catalog.form.name",name)}
    private fun save(){click("online.catalog.form.save");compose.waitUntil(45000){compose.onAllNodesWithTag("online.catalog.form.save").fetchSemanticsNodes().isEmpty()}}
    @Test fun nativeCategoriesIngredientsRecipeReviewRetainedDraftAndFrozenHistory(){
        val api=OnlineApi(OnlineEndpoint("http://10.0.2.2:4371",true));val session=runBlocking{api.login(admin,password)}
        val old=runBlocking{api.create(session,outlet,"2026-05-01")};val original=runBlocking{api.catalog(session,outlet)}
        val coffee=original.catalog.ingredients.first{it.name=="Biji kopi"}
        try{
            compose.onNodeWithText("Closing bersama").performClick();login(employee);open()
            waitText("Mode baca");compose.onNodeWithTag("online.catalog.start").assertDoesNotExist();capture("employee-readonly")
            click("online.catalog.close");compose.onNodeWithText("Keluar akun").performScrollTo().performClick();login(admin);open();main("online.catalog.start");waitText("Draft perangkat tersimpan")
            add(3,"Sirup QA");save();add(1,"Vanila QA")
            val ingredientCategory=draft().ingredientCategories.first{it.name=="Sirup QA"};click("online.catalog.form.category");click("online.catalog.category.${ingredientCategory.id}");save()
            add(2,"Signature QA");save();add(2,"Unused QA");save()
            val unused=draft().menuCategories.first{it.name=="Unused QA"};main("online.catalog.delete.${unused.id}");click("online.catalog.delete.confirm")
            compose.waitUntil(45000){compose.onAllNodesWithTag("online.catalog.delete.confirm").fetchSemanticsNodes().isEmpty()};assertFalse(draft().menuCategories.any{it.id==unused.id})
            add(0,"Vanilla QA")
            val category=draft().menuCategories.first{it.name=="Signature QA"};val vanilla=draft().ingredients.first{it.name=="Vanila QA"}
            click("online.catalog.form.category");click("online.catalog.category.${category.id}")
            compose.onNodeWithTag("online.catalog.form.save").assertIsNotEnabled()
            compose.onNodeWithTag("online.catalog.recipe.list").performScrollToNode(hasTestTag("online.catalog.recipe.${coffee.id}"));click("online.catalog.recipe.${coffee.id}")
            compose.onNodeWithTag("online.catalog.recipe.list").performScrollToNode(hasTestTag("online.catalog.recipe.${vanilla.id}"));click("online.catalog.recipe.${vanilla.id}")
            fill("online.catalog.form.name","Vanilla Fine");compose.activityRule.scenario.recreate();exists("online.catalog.form.name")
            compose.onNodeWithTag("online.catalog.form.name").assertTextContains("Vanilla Fine");ready("online.catalog.form.save");capture("form-recreated");save()
            val menu=draft().menus.first{it.name=="Vanilla Fine"};assertEquals(setOf(coffee.id,vanilla.id),menu.ingredientIds.toSet())
            click("online.catalog.close");compose.onNodeWithTag("online.catalog.open").performScrollTo().performClick();waitText("Draft perangkat tersimpan")
            main("online.catalog.search");fill("online.catalog.search","Vanilla Fine");main("online.catalog.edit.${menu.id}");fill("online.catalog.form.order","2");save();capture("draft-restored")
            main("online.catalog.section.1");main("online.catalog.search");fill("online.catalog.search","Vanila QA");main("online.catalog.delete.${vanilla.id}")
            exists("online.catalog.delete.confirm");compose.onNodeWithTag("online.catalog.delete.confirm").assertIsNotEnabled();capture("referenced-delete");compose.onNodeWithText("Batal").performClick()
            main("online.catalog.edit.${vanilla.id}");click("online.catalog.form.active");compose.onNodeWithTag("online.catalog.form.save").assertIsNotEnabled();capture("inactive-guard")
            compose.onNodeWithText("Batal").performClick();waitText("Isian belum disimpan");compose.onNodeWithText("Buang isian form").performClick()
            main("online.catalog.publish");waitText("Publikasikan katalog?");capture("publish-review");click("online.catalog.publish.confirm");compose.onNodeWithTag("online.catalog.list").performScrollToIndex(0);waitText("Katalog terpublikasi")
            capture("published");compose.onNodeWithTag("online.catalog.publish").assertDoesNotExist()
            runBlocking{
                val current=api.catalog(session,outlet);assertEquals(2L,current.revision);assertEquals(3,current.catalog.menus.size);assertEquals(4,current.catalog.ingredients.size)
                val historical=api.fetch(session,outlet,old.header.closingId);assertEquals(original.versionId,JSONObject(historical.header.snapshotJson).getString("catalogVersionId"));assertEquals(2,JSONObject(historical.header.snapshotJson).getJSONObject("catalog").getJSONArray("menus").length())
                val newer=api.create(session,outlet,"2026-05-02");assertEquals(3,JSONObject(newer.header.snapshotJson).getJSONObject("catalog").getJSONArray("menus").length())
            }
            click("online.catalog.close");compose.onNodeWithText("Keluar akun").performScrollTo().performClick();waitText("Selamat datang kembali")
        }finally{runBlocking{api.logout(session)}}
    }
}
