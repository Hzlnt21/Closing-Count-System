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
import com.closingcount.app.data.local.OnlineOperationEntity
import com.closingcount.app.data.online.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.util.UUID

class OnlineNativeInstrumentedTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private lateinit var username: String
    private lateinit var colleague: String
    private lateinit var password: String
    private lateinit var outlet: String
    @Before fun setup() {
        val args = InstrumentationRegistry.getArguments()
        org.junit.Assume.assumeTrue("Requires explicit owned QA fixture", args.getString("ccsNativeQa") == "true")
        require(args.getString("ccsQaOrigin") == "http://10.0.2.2:4371")
        username = requireNotNull(args.getString("ccsQaEmployee")); colleague = requireNotNull(args.getString("ccsQaColleague"))
        password = requireNotNull(args.getString("ccsQaPassword")); outlet = requireNotNull(args.getString("ccsQaOutlet"))
        OnlineSessionStore(context).clear()
        context.getSharedPreferences("ccs_online_ui", Context.MODE_PRIVATE).edit().clear().commit()
    }
    private fun visible(text: String) = compose.onAllNodesWithText(text, substring = true).fetchSemanticsNodes().isNotEmpty()
    private fun waitText(text: String) { compose.waitUntil(45000) { visible(text) } }
    private fun capture(name: String) {
        compose.waitForIdle()
        val file = File(context.getExternalFilesDir(null), "native-r2-$name.png")
        val bitmap = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
        assertNotNull(bitmap)
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }
    @Test fun loginSaveConflictResolveAndLogoutThroughRealNativeScreens() {
        compose.onNodeWithText("Closing bersama").performClick()
        waitText("Selamat datang kembali"); capture("login")
        compose.onNodeWithTag("online.username").performTextInput(username)
        compose.onNodeWithTag("online.password").performTextInput(password)
        Espresso.closeSoftKeyboard()
        compose.onNodeWithTag("online.login").performScrollTo().performClick()
        waitText("Belum ada closing tersimpan")
        compose.onNodeWithTag("online.open").performScrollTo().performClick()
        waitText("Lanjutkan input")
        compose.onNodeWithText("Lanjutkan input").performScrollTo().performClick()
        waitText("Catat menu terjual.")
        val fieldMatcher = SemanticsMatcher("quantity input") { it.config.getOrNull(SemanticsProperties.TestTag)?.startsWith("online.qty.") == true }
        compose.waitUntil(45000) { compose.onAllNodes(fieldMatcher).fetchSemanticsNodes().isNotEmpty() }
        val tag = compose.onAllNodes(fieldMatcher).fetchSemanticsNodes().first().config[SemanticsProperties.TestTag]
        val menuId = tag.removePrefix("online.qty.")
        compose.onNodeWithTag(tag).performScrollTo().performTextReplacement("10000")
        Espresso.closeSoftKeyboard()
        compose.onNodeWithTag("online.save").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithTag("online.nav.Ringkasan").performClick()
        waitText("Perubahan belum disimpan")
        compose.onNodeWithText("Kembali mengedit").performClick()
        compose.onNodeWithTag(tag).assertTextContains("10000")
        compose.onNodeWithTag(tag).performScrollTo().performTextReplacement("8")
        Espresso.closeSoftKeyboard()
        compose.onNodeWithTag("online.save").performScrollTo().performClick()
        waitText("Perangkat ini tersinkron"); capture("input-synced")
        compose.onNodeWithTag("online.nav.Ringkasan").performClick(); waitText("Tim satu angka."); capture("overview")
        compose.onNodeWithTag("online.nav.Input").performClick()
        val api = OnlineApi(OnlineEndpoint("http://10.0.2.2:4371", true))
        val b = runBlocking { api.login(colleague, password) }
        try {
            val session = (context as ClosingCountApplication).onlineRepository.session()!!
            val current = runBlocking {
                val date = api.businessDate(session, outlet)
                api.create(session, outlet, date)
            }
            val entry = current.entries.first { it.menuId == menuId }
            runBlocking { api.send(b, OnlineOperationEntity(operationId = UUID.randomUUID().toString(), scope = b.scope(outlet),
                closingId = current.header.closingId, menuId = menuId, generation = current.header.generation,
                expectedRevision = entry.serverRevision, quantity = 12, status = "SENT", errorCode = null)) }
            compose.onNodeWithTag(tag).performScrollTo().performTextReplacement("9")
            Espresso.closeSoftKeyboard()
            compose.onNodeWithTag("online.save").performScrollTo().performClick()
            waitText("Tinjau ")
            val inspect = compose.onAllNodes(hasText("Tinjau ", substring = true) and hasClickAction()).onFirst()
            inspect.performScrollTo().performClick(); waitText("Tentukan angka yang benar"); capture("conflict")
            compose.onNodeWithText("Terapkan pilihan").assertIsNotEnabled()
            compose.onNodeWithText("Gunakan angka server terbaru").performClick()
            compose.onNodeWithText("Terapkan pilihan").performClick()
            waitText("Perangkat ini tersinkron")
            compose.onNodeWithTag(tag).assertTextContains("12"); capture("resolved")
            val final = runBlocking { api.fetch(session, outlet, current.header.closingId) }
            assertEquals(12, final.entries.first { it.menuId == menuId }.serverQuantity)
            compose.onNodeWithTag("online.nav.Akun").performClick()
            compose.onNodeWithText("Keluar akun").performScrollTo().performClick(); waitText("Selamat datang kembali")
            assertNull((context as ClosingCountApplication).onlineRepository.session())
            compose.onNodeWithContentDescription("Kembali ke data perangkat").performClick()
            waitText("Closing bersama")
        } finally { runBlocking { api.logout(b) } }
    }
}
