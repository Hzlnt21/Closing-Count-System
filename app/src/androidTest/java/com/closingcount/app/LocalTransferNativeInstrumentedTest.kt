package com.closingcount.app

import android.content.Context
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.view.accessibility.AccessibilityNodeInfo
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.text.TextLayoutResult
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.closingcount.app.data.local.BackupSnapshot
import com.closingcount.app.data.transfer.BackupJsonCodec
import com.closingcount.app.ui.transfer.DataTransferViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.File
import java.security.MessageDigest
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class LocalTransferNativeInstrumentedTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val app = ApplicationProvider.getApplicationContext<ClosingCountApplication>()
    private lateinit var files: LocalTransferQaFiles
    private lateinit var vm: DataTransferViewModel
    private var old: BackupSnapshot? = null
    private var job: Job? = null
    private val initial = localTransferQaSnapshot("Menu sebelum restore")
    private val backup = localTransferQaSnapshot("Menu dari backup", true)
    private val code = InstrumentationRegistry.getArguments().getString("ccsTransferCase") ?: "phone-light"
    private val proof = JSONObject().put("case", code).put("passed", false)
    private val steps = JSONArray()
    private val layouts = JSONArray()
    private var beforeOnline = ""

    @Before fun setup(): Unit = runBlocking {
        Assume.assumeTrue(InstrumentationRegistry.getArguments().getString("ccsLocalTransferQa") == "true")
        require(code.matches(Regex("[a-z0-9-]{1,40}")))
        files = LocalTransferQaFiles(app)
        instrumentation.runOnMainSync { vm = ViewModelProvider(compose.activity)[DataTransferViewModel::class.java];job = vm.viewModelScope.coroutineContext[Job] }
        old = app.database.backupDao().createSnapshot()
        app.database.backupDao().restore(initial)
        beforeOnline = onlineDigest()
        val c = app.resources.configuration
        proof.put("fontScale", c.fontScale).put("widthDp", c.screenWidthDp).put("heightDp", c.screenHeightDp)
            .put("dark", c.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK == android.content.res.Configuration.UI_MODE_NIGHT_YES)
            .put("steps", steps).put("layouts", layouts).put("onlineBeforeSha256", beforeOnline)
    }
    @After fun cleanup(): Unit = runBlocking {
        if (old != null) {
            compose.activityRule.scenario.close()
            job?.join()
            app.database.backupDao().restore(requireNotNull(old))
            assertEquals(old, app.database.backupDao().createSnapshot())
            assertEquals(beforeOnline, onlineDigest())
            proof.put("originalLocalDataRestored", true).put("onlineAfterSha256", onlineDigest())
            saveProof()
        }
        if (::files.isInitialized) files.close()
    }
    private fun waitFor(check: () -> Boolean) { compose.waitUntil(45000) { runCatching(check).getOrDefault(false) } }
    private fun tag(name: String) { waitFor { compose.onAllNodesWithTag(name).fetchSemanticsNodes().size == 1 } }
    private fun click(name: String, scroll: Boolean = true) {
        tag(name);val node = compose.onNodeWithTag(name)
        if (scroll) node.performScrollTo()
        node.assertIsDisplayed().performClick()
    }
    private fun saveProof() { File(app.getExternalFilesDir(null), "local-transfer-$code.json").writeText(proof.toString(2)) }
    private fun capture(scene: String) {
        if (instrumentation.uiAutomation.rootInActiveWindow?.packageName?.toString() == "com.closingcount.app") compose.waitForIdle()
        val bitmap = instrumentation.uiAutomation.takeScreenshot()
        File(app.getExternalFilesDir(null), "local-transfer-$code-$scene.png").outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }
    private fun mark(step: String) { steps.put(step);saveProof() }
    private fun navigate(page: String) {
        if (compose.onAllNodesWithTag("local.nav.menu").fetchSemanticsNodes().isNotEmpty()) click("local.nav.menu", false)
        for (name in listOf("Home", "Closing", "History", "Menus", "Ingredients")) compose.onNodeWithTag("local.nav.$name").assertIsDisplayed()
        click("local.nav.$page", false)
    }
    private fun verifyText(scene: String, filename: String? = null) {
        compose.waitForIdle()
        var measured = 0
        val overflow = JSONArray()
        val matcher = SemanticsMatcher("static layout") { it.config.contains(SemanticsActions.GetTextLayoutResult) && it.config.contains(SemanticsProperties.Text) && !it.config.contains(SemanticsProperties.EditableText) }
        for (node in compose.onAllNodes(matcher, useUnmergedTree = true).fetchSemanticsNodes()) {
            if (node.boundsInRoot.width <= 0 || node.boundsInRoot.height <= 0) continue
            val text = node.config[SemanticsProperties.Text].joinToString(" ") { it.text }
            val values = mutableListOf<TextLayoutResult>()
            compose.onNode(SemanticsMatcher("node ${node.id}") { it.id == node.id }, useUnmergedTree = true)
                .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(values) }
            for (layout in values) {
                measured++
                val intentional = text in listOfNotNull("Closing Count", filename) && (0 until layout.lineCount).any { layout.isLineEllipsized(it) }
                if (significantTextOverflow(layout) && !intentional) overflow.put(text)
            }
        }
        layouts.put(JSONObject().put("scene", scene).put("measuredStaticTexts", measured).put("overflow", overflow))
        assertTrue(measured > 0);assertEquals("Clipped static text at $scene: $overflow", 0, overflow.length())
    }
    private fun node(predicate: (AccessibilityNodeInfo) -> Boolean): AccessibilityNodeInfo? {
        fun find(n: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
            n ?: return null
            if (n.isVisibleToUser && predicate(n)) return n
            for (i in 0 until n.childCount) find(n.getChild(i))?.let { return it }
            return null
        }
        return find(instrumentation.uiAutomation.rootInActiveWindow)
    }
    private fun systemClick(predicate: (AccessibilityNodeInfo) -> Boolean) {
        waitFor { node(predicate) != null }
        var target = requireNotNull(node(predicate))
        while (!target.isClickable) target = target.parent ?: error("System control has no clickable parent")
        check(target.isEnabled && target.performAction(AccessibilityNodeInfo.ACTION_CLICK)) { "System control could not be clicked" }
    }
    private fun picker() { waitFor { instrumentation.uiAutomation.rootInActiveWindow?.packageName?.toString()?.contains("documentsui") == true } }
    private fun shell(command: String) = ParcelFileDescriptor.AutoCloseInputStream(instrumentation.uiAutomation.executeShellCommand(command)).use { it.readBytes() }
    private fun cancelPicker() {
        repeat(5) {
            if (instrumentation.uiAutomation.rootInActiveWindow?.packageName?.toString()?.contains("documentsui") == true) {
                shell("input keyevent 4");Thread.sleep(250)
            }
        }
        tag("local.restore.select")
    }
    private fun choose(name: String) {
        picker()
        if (node { it.text?.toString() == name } == null) {
            systemClick { it.contentDescription?.toString() in listOf("Show roots", "Show navigation drawer", "Tampilkan akar", "Buka panel navigasi") }
            systemClick { it.text?.toString() in listOf("Downloads", "Download", "Unduhan") }
        }
        repeat(10) {
            if (node { it.text?.toString() == name } != null) {
                systemClick { it.text?.toString() == name };return
            }
            node { it.isScrollable }?.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)
            Thread.sleep(200)
        }
        error("Owned dummy document not visible in system picker")
    }
    private fun onlineDigest(): String {
        val value = JSONObject()
        for (table in listOf("online_closings", "online_entries", "online_operations", "online_lifecycle_operations")) {
            val rows = JSONArray()
            app.database.openHelper.readableDatabase.query("SELECT * FROM $table ORDER BY rowid").use { c ->
                while (c.moveToNext()) rows.put(JSONArray().apply { for (i in 0 until c.columnCount) put(if (c.isNull(i)) JSONObject.NULL else c.getString(i)) })
            }
            value.put(table, rows)
        }
        return digest(value.toString().toByteArray())
    }
    private fun digest(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    @Test fun systemPickerCancelInvalidReviewRecreateFrozenRestoreAndBackup(): Unit = runBlocking {
        try {
            val (valid, filename) = files.create("native-restore", BackupJsonCodec.encode(backup, 123))
            val (_, invalidName) = files.create("native-invalid", "not a backup".toByteArray())
            tag("local.restore.select");navigate("History")
            waitFor { compose.onAllNodesWithText("Riwayat Closing").fetchSemanticsNodes().isNotEmpty() }
            navigate("Home");tag("local.restore.select");mark("five-destinations-and-history-home-navigation")
            compose.onNodeWithTag("local.restore.select").performScrollTo();verifyText("home");capture("home")
            click("local.restore.select");picker();capture("picker");cancelPicker()
            assertNull(vm.state.value.review);assertEquals(initial, app.database.backupDao().createSnapshot());mark("system-picker-cancel-keeps-local-data")
            click("local.restore.select");choose(invalidName)
            waitFor { !vm.state.value.busy && vm.state.value.failed && vm.state.value.message != null }
            capture("invalid");assertEquals(initial, app.database.backupDao().createSnapshot());mark("invalid-provider-file-rejected")
            click("local.restore.select");choose(filename);tag("local.restore.review")
            compose.onNodeWithText("1 menu / 2 bahan / 3 riwayat closing").assertExists()
            compose.onNodeWithText("Restore memakai salinan file yang telah diperiksa ini.").performScrollTo().assertIsDisplayed()
            verifyText("review", filename);capture("review");click("local.restore.cancel", false)
            waitFor { vm.state.value.review == null };assertEquals(initial, app.database.backupDao().createSnapshot());mark("review-cancel-keeps-local-data")
            click("local.restore.select");choose(filename);tag("local.restore.review")
            val reviewed = requireNotNull(vm.state.value.review)
            compose.activityRule.scenario.recreate();tag("local.restore.review")
            assertEquals(reviewed, vm.state.value.review);mark("activity-recreation-keeps-reviewed-identity")
            files.rewrite(valid, "provider content changed".toByteArray())
            click("local.restore.confirm", false)
            waitFor { !vm.state.value.busy && vm.state.value.message?.startsWith("Data lokal berhasil") == true }
            assertEquals(backup, app.database.backupDao().createSnapshot());assertEquals(beforeOnline, onlineDigest())
            capture("restored");mark("applies-original-reviewed-content-online-tables-unchanged")
            click("local.backup.create");picker()
            val exported = "ccs-qa-export-${UUID.randomUUID()}.json"
            require(exported.matches(Regex("ccs-qa-export-[a-f0-9-]+\\.json")))
            waitFor { node { it.className?.toString()?.endsWith("EditText") == true } != null }
            check(requireNotNull(node { it.className?.toString()?.endsWith("EditText") == true }).performAction(AccessibilityNodeInfo.ACTION_SET_TEXT,
                Bundle().apply { putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, exported) }))
            systemClick { it.isEnabled && it.text?.toString()?.lowercase() in listOf("save", "simpan") }
            waitFor { !vm.state.value.busy && vm.state.value.message == "Backup lokal berhasil disimpan." }
            val bytes = shell("cat /sdcard/Download/$exported")
            assertEquals(backup, BackupJsonCodec.decode(bytes))
            capture("backup-written");mark("system-create-document-backup-roundtrip")
            proof.put("exportFilename", exported).put("exportSha256", digest(bytes)).put("reviewSha256", reviewed.sha256)
                .put("passed", true).put("physicalPhoneAccessed", false).put("serverTransfer", false)
            saveProof()
        } catch (failure: Throwable) {
            capture("failure");saveProof()
            val text = StringBuilder()
            fun dump(n: AccessibilityNodeInfo?, depth: Int = 0) {
                n ?: return
                text.append(" ".repeat(depth)).append(n.className).append(" | ").append(n.text).append(" | ").append(n.contentDescription).append('\n')
                for (i in 0 until n.childCount) dump(n.getChild(i), depth + 1)
            }
            dump(instrumentation.uiAutomation.rootInActiveWindow)
            File(app.getExternalFilesDir(null), "local-transfer-$code-failure.txt").writeText(text.toString())
            throw failure
        }
    }
}
