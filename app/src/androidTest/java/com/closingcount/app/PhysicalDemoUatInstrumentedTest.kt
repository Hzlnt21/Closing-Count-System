package com.closingcount.app

import android.graphics.Bitmap
import android.os.Build
import android.os.Process
import android.os.SystemClock
import android.os.ParcelFileDescriptor
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.ViewCompat
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
import org.json.JSONObject
import org.junit.*
import org.junit.Assert.*
import java.io.File
import java.security.MessageDigest
import java.util.UUID

/** Physical spare-phone acceptance. Every business mutation uses MainActivity UI.
 * Host stops/restarts only the owned DEMO runtime between the guarded phases.
 * Repository/API reads are independent test oracles, never fixture substitutes. */
class PhysicalDemoUatInstrumentedTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val instrument = InstrumentationRegistry.getInstrumentation()
    private val args = InstrumentationRegistry.getArguments()
    private val app = ApplicationProvider.getApplicationContext<ClosingCountApplication>()
    private lateinit var scenario: ActivityScenario<MainActivity>
    private lateinit var online: OnlineViewModel
    private lateinit var output: File
    private lateinit var marker: File
    private lateinit var state: JSONObject
    private lateinit var phase: String
    private lateinit var outlet: String
    private lateinit var closing: String
    private lateinit var capp: String
    private lateinit var latte: String
    private var job: Job? = null
    private val checks = org.json.JSONArray()
    private val proof = JSONObject().put("passed", false).put("physicalPhoneAccessed", true)
    private fun arg(key: String) = requireNotNull(args.getString("ccsPhone$key"))
    private fun hash(b: ByteArray) = MessageDigest.getInstance("SHA-256").digest(b).joinToString("") { "%02x".format(it) }
    private fun legacy() = runBlocking { hash(BackupJsonCodec.encode(app.database.backupDao().createSnapshot(), 0)) }
    private fun waitFor(test: () -> Boolean) { compose.waitUntil(45000) { runCatching(test).getOrDefault(false) } }
    private fun exists(tag: String) = compose.onAllNodesWithTag(tag).fetchSemanticsNodes().size == 1
    private fun click(tag: String, scroll: Boolean = true) {
        waitFor { exists(tag) }; val node = compose.onNodeWithTag(tag)
        if (scroll) node.performScrollTo()
        node.assertIsDisplayed().assertIsEnabled().performClick()
    }
    private fun text(value: String) { compose.onNodeWithText(value).performScrollTo().performClick() }
    private fun fill(tag: String, value: String) {
        compose.onNodeWithTag(tag).performScrollTo().performTextReplacement(value)
        scenario.onActivity { WindowCompat.getInsetsController(it.window, it.window.decorView).hide(WindowInsetsCompat.Type.ime()) }
        compose.waitForIdle()
        var hiddenSince = 0L
        var backRequested = false
        waitFor {
            val now = SystemClock.uptimeMillis()
            val requested = ParcelFileDescriptor.AutoCloseInputStream(instrument.uiAutomation.executeShellCommand("dumpsys input_method")).use {
                Regex("mInputShown=true").containsMatchIn(it.readBytes().toString(Charsets.UTF_8))
            }
            var shown = false
            scenario.onActivity { shown = ViewCompat.getRootWindowInsets(it.window.decorView)?.isVisible(WindowInsetsCompat.Type.ime()) == true }
            if (requested) {
                if (!backRequested) ParcelFileDescriptor.AutoCloseInputStream(instrument.uiAutomation.executeShellCommand("input keyevent 4")).use { it.readBytes() }
                backRequested = true; hiddenSince = 0
            } else {
                backRequested = false
                if (shown) hiddenSince = 0 else if (hiddenSince == 0L) hiddenSince = now
            }
            hiddenSince != 0L && now - hiddenSince >= 750
        }
    }
    private fun nav(value: String) { if (exists("online.nav.menu")) click("online.nav.menu", false); click("online.nav.$value", false) }
    private fun session() = requireNotNull(app.onlineRepository.session())
    private fun api() = OnlineApi(OnlineEndpoint("http://127.0.0.1:4372", true), 4000)
    private fun synced() { waitFor { online.state.value.pending == 0 && online.state.value.syncVerified && !online.state.value.syncing && !online.state.value.working } }
    private fun checkCase(name: String, passed: Boolean) {
        checks.put(JSONObject().put("name", name).put("passed", passed)); write(); assertTrue(name, passed)
    }
    private fun write() { if (::output.isInitialized) File(output, "$phase-proof.json").writeText(proof.put("checks", checks).toString(2)) }
    private fun snap(name: String) {
        compose.waitForIdle(); instrument.waitForIdleSync()
        val image = requireNotNull(instrument.uiAutomation.takeScreenshot())
        File(output, "$phase-$name.png").outputStream().use { check(image.compress(Bitmap.CompressFormat.PNG, 100, it)) }; image.recycle()
    }
    private fun login(role: String) {
        waitFor { exists("online.username") }; fill("online.username", arg(role + "Username")); fill("online.password", arg(role + "Password")); click("online.login")
        waitFor { !online.state.value.loginVisible && !online.state.value.working }
        assertEquals(arg(role + "Actor"), session().identity.id)
    }
    private fun logout() { nav("Akun"); text("Keluar akun"); waitFor { online.state.value.loginVisible }; assertNull(app.onlineRepository.session()) }
    private fun open(synchronize: Boolean = true) {
        nav("Ringkasan"); waitFor { online.state.value.outletId == outlet && online.state.value.date == arg("Date") && !online.state.value.working }
        if (online.state.value.closingId == null) click("online.open")
        waitFor { online.state.value.closingId == closing && online.state.value.menus.size == 2 }; nav("Input")
        if (synchronize) synced()
    }
    private fun quantity(menu: String, value: Int) { fill("online.qty.$menu", value.toString()); click("online.save"); waitFor { !online.state.value.working && !online.state.value.dirty } }
    private fun action(value: String, reason: String? = null) {
        nav("Review"); synced(); click("online.action.$value")
        if (reason != null) fill("online.lifecycle.reason", reason)
        click("online.lifecycle.confirm", false); synced()
    }
    @Before fun guard() {
        check(BuildConfig.DEBUG && BuildConfig.ONLINE_API_ORIGIN == "http://127.0.0.1:4372")
        check(app.packageName == "com.closingcount.app.demo" && Build.MODEL == "M2101K7BNY" && Process.myUid() / 100000 == 0)
        check(arg("AuthorizedRef") == "96ea811bdb")
        val run = arg("Run"); check(UUID.fromString(run).toString() == run); phase = arg("Phase"); check(phase in listOf("prime", "offline", "resume"))
        outlet = OnlineApi.uuid(arg("Outlet")); closing = OnlineApi.uuid(arg("Closing")); capp = OnlineApi.uuid(arg("Cappuccino")); latte = OnlineApi.uuid(arg("Latte"))
        runBlocking { app.onlineRepository.initialize() }
        marker = File(app.noBackupFilesDir, "physical-demo-$run.json")
        if (phase == "prime") { check(!marker.exists()); assertNull(app.onlineRepository.session()); state = JSONObject().put("legacy", legacy()).put("next", "prime") }
        else state = JSONObject(marker.readText())
        assertEquals(phase, state.getString("next")); assertEquals(state.getString("legacy"), legacy())
        output = File(app.filesDir, "physical-demo-$run").apply { mkdirs() }
        proof.put("run", run).put("phase", phase).put("model", Build.MODEL).put("android", Build.VERSION.RELEASE).put("pid", Process.myPid()).put("legacyBefore", legacy())
        scenario = ActivityScenario.launch(MainActivity::class.java)
        waitFor { exists("online.username") || exists("online.nav.Akun") || compose.onAllNodesWithText("Closing bersama").fetchSemanticsNodes().isNotEmpty() }
        if (!exists("online.username") && !exists("online.nav.Akun")) text("Closing bersama")
        scenario.onActivity { online = ViewModelProvider(it)[OnlineViewModel::class.java]; job = online.viewModelScope.coroutineContext[Job] }
        waitFor { online.state.value.initialized }
    }
    @After fun close() = runBlocking {
        if (::scenario.isInitialized) { scenario.close(); job?.join() }
        if (::state.isInitialized) { assertEquals(state.getString("legacy"), legacy()); proof.put("legacyAfter", legacy()); write() }
    }
    @Test fun realPhoneUsesSharedClosingAndRetainsOfflineWork() {
        when (phase) {
            "prime" -> {
                login("Rani"); open(); checkCase("Real phone shows fresh DEMO quantities3and5", online.state.value.menus.single { it.id == capp }.quantity == 3 && online.state.value.menus.single { it.id == latte }.quantity == 5)
                quantity(capp, 4); synced(); val live = runBlocking { api().fetch(session(), outlet, closing) }
                checkCase("Native UI saves Cappuccino4 to the actual shared server", live.entries.single { it.menuId == capp }.serverQuantity == 4)
                snap("saved-draft"); state.put("next", "offline")
            }
            "offline" -> {
                open(false); quantity(latte, 6); waitFor { online.state.value.pending == 1 }
                val before = online.state.value.menus.single { it.id == latte }; checkCase("Offline native save retains desired6 as pending", before.quantity == 6)
                scenario.recreate(); compose.waitForIdle(); scenario.onActivity { online = ViewModelProvider(it)[OnlineViewModel::class.java] }; waitFor { online.state.value.pending == 1 }
                checkCase("Activity recreation retains pending quantity without server acknowledgement", online.state.value.menus.single { it.id == latte }.quantity == 6)
                snap("retained-pending"); state.put("next", "resume")
            }
            "resume" -> {
                open(false); click("online.sync"); synced(); val live = runBlocking { api().fetch(session(), outlet, closing) }
                checkCase("Actual process restart and server recovery apply pending Latte6", live.entries.single { it.menuId == latte }.serverQuantity == 6 && live.entries.single { it.menuId == capp }.serverQuantity == 4)
                action("submit"); checkCase("Employee submits through real UI without supervisor lock authority", online.state.value.closingState == "SUBMITTED" && !exists("online.action.lock")); snap("submitted")
                logout(); login("Arif"); open(); action("lock"); checkCase("Supervisor locks the actual submitted closing through UI", online.state.value.closingState == "LOCKED")
                val frozen = runBlocking { api().report(session(), outlet, closing, 1) }; File(output, "locked-report.json").writeText(frozen); snap("locked")
                action("reopen", "Physical Android13 review correction")
                checkCase("Supervisor reopens explicitly while frozen generation1 remains exact", online.state.value.closingState == "DRAFT" && runBlocking { api().report(session(), outlet, closing, 1) } == frozen)
                logout(); login("Rani"); open(); quantity(capp, 5); synced(); checkCase("New native draft edits remain separate from locked historical report", runBlocking { api().report(session(), outlet, closing, 1) } == frozen)
                snap("reopened-draft"); logout(); checkCase("Native account logout finishes with no pending work", app.onlineRepository.session() == null); state.put("next", "finished")
            }
        }
        marker.writeText(state.toString(2)); proof.put("passed", true); write()
    }
}
