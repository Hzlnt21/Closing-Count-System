package com.closingcount.app

import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso
import androidx.test.platform.app.InstrumentationRegistry
import com.closingcount.app.data.online.*
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.util.UUID

class OnlineLayoutInstrumentedTest{
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()
    private val context=ApplicationProvider.getApplicationContext<Context>() as ClosingCountApplication
    private lateinit var outlet:String;private lateinit var username:String;private lateinit var password:String;private lateinit var code:String
    private lateinit var menu:String;private val checks=JSONArray()
    private val outletName="Kedai Kopi Nusantara Cabang Pusat Pelayanan Pelanggan dan Operasional"
    private val longMenu="Kopi Susu Arabika dengan Tambahan Krim dan Gula Aren untuk Menu Spesial Pelanggan"
    private lateinit var meta:JSONObject
    @Before fun setup():Unit=runBlocking{
        val args=InstrumentationRegistry.getArguments();org.junit.Assume.assumeTrue("Owned layout QA only",args.getString("ccsLayoutQa")=="true")
        require(args.getString("ccsQaOrigin")=="http://10.0.2.2:4371")
        outlet=requireNotNull(args.getString("ccsQaOutlet"));username=requireNotNull(args.getString("ccsQaAdmin"));password=requireNotNull(args.getString("ccsQaPassword"))
        code=requireNotNull(args.getString("ccsLayoutCase"));require(code.matches(Regex("[a-z0-9-]{1,40}")))
        val config=context.resources.configuration
        assertEquals(requireNotNull(args.getString("ccsFontScale")).toFloat(),config.fontScale,0.03f)
        val dark=config.uiMode and Configuration.UI_MODE_NIGHT_MASK==Configuration.UI_MODE_NIGHT_YES
        assertEquals(args.getString("ccsDark")=="true",dark)
        assertTrue(kotlin.math.abs(config.screenWidthDp-requireNotNull(args.getString("ccsWidthDp")).toInt())<=8)
        meta=JSONObject().put("density",context.resources.displayMetrics.density).put("case",code).put("fontScale",config.fontScale).put("screenWidthDp",config.screenWidthDp).put("screenHeightDp",config.screenHeightDp).put("dark",dark).put("checks",checks).put("physicalPhoneAccessed",false)
        val api=OnlineApi(OnlineEndpoint("http://10.0.2.2:4371",true));val a=api.login(username,password)
        try{
            val catalog=api.catalog(a,outlet);menu=catalog.catalog.menus.first().id
            val seeded=catalog.catalog.copy(menus=catalog.catalog.menus.map{if(it.id==menu)it.copy(name=longMenu)else it},
                ingredients=catalog.catalog.ingredients.mapIndexed{i,row->if(i==0)row.copy(name="Biji Kopi Arabika Pilihan untuk Resep Minuman Spesial Nusantara")else row})
            api.publishCatalog(a,OnlineCatalogOperation(a.scope(outlet),UUID.randomUUID().toString(),catalog.revision,seeded.json().toString(),"SENT",true))
            val settings=api.outletSettings(a,outlet);api.updateSettings(a,OnlineSettingsOperation(a.scope(outlet),UUID.randomUUID().toString(),settings.revision,outletName,"Asia/Jakarta",0,"SENT",true))
        }finally{api.logout(a)}
        OnlineSessionStore(context).clear();context.getSharedPreferences("ccs_online_ui",Context.MODE_PRIVATE).edit().clear().commit()
        Unit
    }
    private fun waitText(text:String){compose.waitUntil(45000){compose.onAllNodesWithText(text,substring=true).fetchSemanticsNodes().isNotEmpty()}}
    private fun ready(tag:String){compose.waitUntil(45000){val nodes=compose.onAllNodesWithTag(tag).fetchSemanticsNodes();nodes.size==1&&!nodes[0].config.contains(SemanticsProperties.Disabled)}}
    private fun click(tag:String,scroll:Boolean=true){ready(tag);if(scroll)compose.onNodeWithTag(tag).performScrollTo();compose.onNodeWithTag(tag).assertIsDisplayed().performClick()}
    private fun textClick(text:String){compose.onNodeWithText(text).assertIsDisplayed().performClick()}
    private fun fill(tag:String,value:String){ready(tag);compose.onNodeWithTag(tag).performScrollTo().performTextReplacement(value);Espresso.closeSoftKeyboard()}
    private fun nav(page:String){
        if(compose.onAllNodesWithTag("online.nav.menu").fetchSemanticsNodes().isNotEmpty()){click("online.nav.menu",false);click("online.nav.$page",false)}else click("online.nav.$page",false)
    }
    private fun catalogClick(tag:String){compose.onNodeWithTag("online.catalog.list").performScrollToNode(hasTestTag(tag));click(tag,false)}
    private fun scene(name:String,capture:Boolean=true){
        compose.waitForIdle()
        val problems=JSONArray();var measured=0;var intentional=0;var paragraphOnly=0
        val matcher=SemanticsMatcher("static text layout"){it.config.contains(SemanticsActions.GetTextLayoutResult)&&it.config.contains(SemanticsProperties.Text)&&!it.config.contains(SemanticsProperties.EditableText)}
        for(node in compose.onAllNodes(matcher,useUnmergedTree=true).fetchSemanticsNodes()){
            if(node.boundsInRoot.width<=0||node.boundsInRoot.height<=0)continue
            val text=node.config[SemanticsProperties.Text].joinToString(" "){it.text}
            val layouts=mutableListOf<TextLayoutResult>()
            compose.onNode(SemanticsMatcher("node ${node.id}"){it.id==node.id},useUnmergedTree=true).performSemanticsAction(SemanticsActions.GetTextLayoutResult){it(layouts)}
            for(layout in layouts){measured++;if(layout.hasVisualOverflow){
                val ellipsized=(0 until layout.lineCount).any{layout.isLineEllipsized(it)}
                if(!significantTextOverflow(layout))paragraphOnly++
                else if(text==outletName&&ellipsized)intentional++ else problems.put(JSONObject().put("text",text).put("width",layout.size.width).put("height",layout.size.height).put("paragraphWidth",layout.multiParagraph.width).put("paragraphHeight",layout.multiParagraph.height).put("maxLinesExceeded",layout.multiParagraph.didExceedMaxLines).put("lineRightMax",(0 until layout.lineCount).maxOfOrNull{layout.getLineRight(it)}).put("lineBottomMax",(0 until layout.lineCount).maxOfOrNull{layout.getLineBottom(it)}).put("ellipsized",ellipsized))
            }}
        }
        checks.put(JSONObject().put("scene",name).put("measuredStaticTexts",measured).put("intentionalOutletEllipses",intentional).put("paragraphContainerOnly",paragraphOnly).put("overflow",problems))
        File(context.getExternalFilesDir(null),"layout-$code.json").writeText(meta.toString(2))
        if(capture||problems.length()>0){val bitmap=InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot();File(context.getExternalFilesDir(null),"layout-$code-$name.png").outputStream().use{bitmap.compress(Bitmap.CompressFormat.PNG,100,it)};bitmap.recycle()}
        assertTrue("Static text layout hooks unavailable",measured>0)
        assertEquals("Unexpected static text overflow at $name: $problems",0,problems.length())
    }
    @Test fun criticalScreensAndDialogsRemainReadableAndReachable(){
        compose.onNodeWithText("Closing bersama").performClick();waitText("Selamat datang kembali");scene("login")
        fill("online.username",username);fill("online.password",password);click("online.login");waitText("Belum ada closing tersimpan")
        click("online.open");waitText("Lanjutkan input");scene("overview")
        nav("Input");waitText("Catat menu terjual.");fill("online.qty.$menu","9999");scene("input")
        nav("Review");waitText("Perubahan belum disimpan")
        val unsaved=compose.onNodeWithText("Angka yang sedang diketik belum menjadi draft tersimpan. Kembali untuk menyimpan, atau buang perubahan kolom ini.")
        val unsavedRange=unsaved.fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange]
        if(unsavedRange.maxValue()>0f){unsaved.performSemanticsAction(SemanticsActions.ScrollBy){it(0f,unsavedRange.maxValue())};assertTrue(unsaved.fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange].value()>0f)}
        meta.put("unsavedMessageScrolledToEnd",unsavedRange.maxValue()>0f)
        scene("unsaved",true);textClick("Buang perubahan");waitText("Periksa. Lalu pastikan.");scene("review",false)
        nav("Riwayat");waitText("Jejak tetap tersimpan.");scene("history",false)
        nav("Akun");waitText("Akun & akses outlet");scene("account",false)
        click("online.settings.open");waitText("Versi pengaturan 1");fill("online.settings.name","Preview pengaturan untuk pemeriksaan tampilan")
        click("online.settings.save");waitText("Terapkan aturan baru?")
        val explanation="Berlaku untuk closing baru. Closing lama tidak diubah. Perubahan yang masuk dari admin lain harus diperiksa ulang."
        val scrollMatcher=SemanticsMatcher("review text scroller"){it.config.contains(SemanticsProperties.VerticalScrollAxisRange)&&it.config.contains(SemanticsActions.ScrollBy)} and hasAnyDescendant(hasText(explanation))
        val scroller=compose.onNode(scrollMatcher);val range=scroller.fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange]
        val needsScroll=range.maxValue()>0f
        if(needsScroll){scroller.performSemanticsAction(SemanticsActions.ScrollBy){it(0f,range.maxValue())};assertTrue(scroller.fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange].value()>0f)}
        meta.put("settingsReviewScrolledToEnd",needsScroll)
        scene("settings-review");textClick("Periksa lagi");click("online.settings.close",false)
        waitText("Perubahan belum disimpan");textClick("Buang isian")
        click("online.team.open");ready("online.team.create");click("online.team.create");fill("online.team.name","Pratinjau Nama Anggota dengan Keterangan Panjang")
        scene("team-form");textClick("Batal");waitText("Buang isian anggota?");textClick("Buang isian");click("online.team.close",false)
        click("online.catalog.open");ready("online.catalog.list")
        compose.onNodeWithTag("online.catalog.list").performScrollToNode(hasText("Versi server",substring=true))
        waitText("Versi server 2");catalogClick("online.catalog.start");ready("online.catalog.publish")
        catalogClick("online.catalog.edit.$menu");ready("online.catalog.form.save");scene("catalog-form")
        textClick("Batal");catalogClick("online.catalog.discard");click("online.catalog.discard.confirm",false)
        ready("online.catalog.start");click("online.catalog.close",false)
        compose.onNodeWithText("Keluar akun").performScrollTo().performClick();waitText("Selamat datang kembali")
        meta.put("passed",true).put("scope","Selected native layouts; fixture setup writes only, no UI publication or saved quantity")
        File(context.getExternalFilesDir(null),"layout-$code.json").writeText(meta.toString(2))
    }
}
