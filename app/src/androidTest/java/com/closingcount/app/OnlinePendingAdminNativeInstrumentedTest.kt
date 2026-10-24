package com.closingcount.app

import android.content.Context
import android.graphics.Bitmap
import android.os.Bundle
import android.os.Process
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso
import androidx.test.platform.app.InstrumentationRegistry
import com.closingcount.app.data.online.*
import com.closingcount.app.data.transfer.*
import com.closingcount.app.ui.online.*
import kotlinx.coroutines.Job
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.*
import org.junit.Assert.*
import java.io.File
import java.security.MessageDigest
import java.util.UUID

/**
 * Guarded native administrative recovery acceptance on a dedicated QA installation.
 *
 * Four separate host instrumentation invocations, in order: settings, team, catalog, lifecycle.
 * Each invocation requires ccsAdminNativeQa=true, ccsAdminNativeRun=<canonical UUID>,
 * ccsAdminNativePhase=<phase>, ccsAdminNativeUser=<owned secondary Android user > 0>,
 * ccsQaOrigin=http://10.0.2.2:4371, ccsQaOutlet=<owned ADMIN outlet UUID>,
 * ccsQaAdmin=<owned admin username>, ccsQaPassword=<fixture password>.
 *
 * The host must check AVD/user ownership, switch to this secondary user, collect files from this user,
 * restore owner/settings and remove only this uniquely named secondary user on success or failure.
 * Never run against user 0, a real phone, or an operational backend.
 *
 * Setup intentionally accepts codec-produced packets directly into the inert inbox. This class does
 * NOT claim DocumentsUI picker/import coverage. All recovery review/apply/continue/send actions use
 * production MainActivity UI. No repository sync/prepare methods are called as test shortcuts.
 * The marker holds identifiers and hashes only; normal encrypted OnlineSessionStore retains login.
 */
class OnlinePendingAdminNativeInstrumentedTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val app = ApplicationProvider.getApplicationContext<ClosingCountApplication>()
    private val phases = listOf("settings", "team", "catalog", "lifecycle")
    private val expectedInbox = mapOf("settings" to 1, "team" to 3, "catalog" to 4, "lifecycle" to 9)
    private lateinit var scenario: ActivityScenario<MainActivity>
    private lateinit var pending: OnlinePendingTransferViewModel
    private lateinit var online: OnlineViewModel
    private lateinit var settings: OnlineSettingsViewModel
    private lateinit var team: OnlineTeamViewModel
    private lateinit var catalog: OnlineCatalogViewModel
    private lateinit var api: OnlineApi
    private lateinit var session: OnlineSession
    private lateinit var marker: File
    private lateinit var saved: JSONObject
    private lateinit var run: String
    private lateinit var phase: String
    private lateinit var outlet: String
    private lateinit var fixturePassword: String
    private var phaseStartCaseCount = 0
    private val jobs = linkedSetOf<Job>()
    private val proof = JSONObject().put("passed", false).put("physicalPhoneAccessed", false)
        .put("packetsSeededDirectlyIntoInbox", true).put("documentsPickerExercised", false)
        .put("completePt07Acceptance", false)

    @Before fun guard(): Unit = runBlocking {
        val args = InstrumentationRegistry.getArguments()
        check(args.getString("ccsAdminNativeQa") == "true")
        run = requireNotNull(args.getString("ccsAdminNativeRun"))
        check(UUID.fromString(run).toString() == run)
        phase = requireNotNull(args.getString("ccsAdminNativePhase")); check(phase in phases)
        val user = requireNotNull(args.getString("ccsAdminNativeUser")).toInt()
        check(user > 0 && Process.myUid() / 100000 == user)
        check(args.getString("ccsQaOrigin") == "http://10.0.2.2:4371")
        outlet = OnlineApi.uuid(requireNotNull(args.getString("ccsQaOutlet")))
        fixturePassword = requireNotNull(args.getString("ccsQaPassword"))
        api = OnlineApi(OnlineEndpoint("http://10.0.2.2:4371", true), 4000)
        marker = File(app.noBackupFilesDir, "native-pending-admin-qa-$run.json")
        app.onlineRepository.initialize()
        if (phase == "settings") {
            check(!marker.exists()); assertNull(app.onlineRepository.session())
            assertTrue(OnlinePendingRecoveryInbox(app).inventory().isEmpty())
            session = api.login(requireNotNull(args.getString("ccsQaAdmin")), fixturePassword)
            assertEquals("ADMIN", session.identity.outlets.single { it.id == outlet }.role)
            app.onlineRepository.activate(session, true)
            val scope = session.scope(outlet)
            assertTrue(app.database.onlineSyncDao().closingIds(scope).isEmpty())
            assertNull(app.onlineRepository.pendingSettings(outlet))
            assertNull(app.onlineRepository.pendingTeam(outlet))
            assertNull(app.onlineRepository.pendingCatalog(outlet))
            saved = JSONObject().put("run", run).put("user", user).put("outlet", outlet)
                .put("actor", session.identity.id).put("username", session.identity.username)
                .put("sourceDevice", UUID.randomUUID().toString()).put("nextPhaseIndex", 0)
                .put("legacySha256", legacyHash()).put("cases", JSONArray())
        } else {
            saved = JSONObject(marker.readText())
            check(saved.getString("run") == run && saved.getInt("user") == user && saved.getString("outlet") == outlet)
            session = requireNotNull(app.onlineRepository.session())
            assertEquals(saved.getString("actor"), session.identity.id)
            assertEquals(saved.getString("username"), session.identity.username)
            assertEquals(requireNotNull(args.getString("ccsQaAdmin")), session.identity.username)
            assertEquals("http://10.0.2.2:4371", session.origin)
            assertEquals(saved.getString("legacySha256"), legacyHash())
        }
        // A partially failed phase is never silently replayed. Host must start a new owned run/user.
        check(!saved.has("phaseStarted"))
        assertEquals(phases.indexOf(phase), saved.getInt("nextPhaseIndex"))
        phaseStartCaseCount = saved.getJSONArray("cases").length()
        saved.put("phaseStarted", phase); persist()
        scenario = ActivityScenario.launch(MainActivity::class.java)
        attachModels()
        compose.onNodeWithText("Closing bersama").assertIsDisplayed()
        assertFalse(pending.state.value.opened)
        assertNull(pending.state.value.intent)
        assertNull(pending.state.value.importing)
        compose.onNodeWithText("Closing bersama").performClick()
        waitFor { online.state.value.initialized && online.state.value.identity?.id == session.identity.id }
        instrumentation.runOnMainSync { online.selectOutlet(outlet) }
        waitFor { online.state.value.outletId == outlet && !online.state.value.working }
        proof.put("run", run).put("phase", phase).put("user", user).put("uid", Process.myUid())
            .put("pid", Process.myPid()).put("outlet", outlet).put("actor", session.identity.id)
            .put("legacyBeforeSha256", saved.getString("legacySha256"))
        scenario.onActivity { activity ->
            val configuration = activity.resources.configuration
            proof.put("actualFontScale", configuration.fontScale.toDouble())
                .put("actualNightMode", if (configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK == android.content.res.Configuration.UI_MODE_NIGHT_YES) "dark" else "light")
        }
    }

    @After fun close(): Unit = runBlocking {
        if (::scenario.isInitialized) { scenario.close(); jobs.forEach { it.join() } }
        // All local test state remains only in the guarded temporary Android installation.
        // Host removal after collecting evidence is mandatory, including after an assertion failure.
    }

    private fun attachModels() {
        scenario.onActivity { activity ->
            val owner = ViewModelProvider(activity)
            pending = owner[OnlinePendingTransferViewModel::class.java]
            online = owner[OnlineViewModel::class.java]
            settings = owner[OnlineSettingsViewModel::class.java]
            team = owner[OnlineTeamViewModel::class.java]
            catalog = owner[OnlineCatalogViewModel::class.java]
            listOf(pending, online, settings, team, catalog).forEach {
                it.viewModelScope.coroutineContext[Job]?.let(jobs::add)
            }
        }
    }
    private fun persist() { marker.writeText(saved.toString(2)) }
    private fun sha(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes)
        .joinToString("") { "%02x".format(it) }
    private fun sha(text: String) = sha(text.toByteArray(Charsets.UTF_8))
    private suspend fun legacyHash() = sha(BackupJsonCodec.encode(app.database.backupDao().createSnapshot(), 0))
    private fun waitFor(check: () -> Boolean) { compose.waitUntil(45000) { runCatching(check).getOrDefault(false) } }
    private fun exists(tag: String) = compose.onAllNodesWithTag(tag).fetchSemanticsNodes().size == 1
    private fun click(tag: String, scroll: Boolean = true) {
        waitFor { exists(tag) }
        val node = compose.onNodeWithTag(tag)
        if (scroll) node.performScrollTo()
        node.assertIsEnabled().assertIsDisplayed().performClick()
    }
    private fun pendingClick(tag: String) {
        compose.onNodeWithTag("online.pending.page").performScrollToNode(hasTestTag(tag))
        click(tag)
    }
    private fun catalogClick(tag: String) {
        compose.onNodeWithTag("online.catalog.list").performScrollToNode(hasTestTag(tag))
        click(tag, false)
    }
    private fun nav(page: String) {
        if (exists("online.nav.menu")) click("online.nav.menu", false)
        click("online.nav.$page", false)
    }
    private fun openInbox() {
        assertFalse(pending.state.value.opened)
        nav("Akun"); click("online.pending.open")
        waitFor { pending.state.value.opened && !pending.state.value.busy }
    }
    private fun closeInbox() {
        repeat(4) {
            if (!pending.state.value.opened) return
            click("online.pending.back", false)
            waitFor { !pending.state.value.busy }
        }
        assertFalse("Pending panel should have returned to account", pending.state.value.opened)
    }
    private fun inspect(row: JSONObject) {
        openInbox()
        pendingClick("online.pending.inbox.${row.getString("packetSha256")}")
        waitFor { pending.state.value.file?.sha256 == row.getString("packetSha256") && !pending.state.value.busy }
        val reference = if (row.getString("kind") == "LIFECYCLE") row.getString("closing") else row.getString("originalId")
        pendingClick("online.pending.compare.$reference")
        waitFor { pending.state.value.intent?.originalOperationId == row.getString("originalId") && !pending.state.value.busy }
    }
    private suspend fun applyReviewed(row: JSONObject): String {
        val review = requireNotNull(pending.state.value.intent)
        assertEquals("READY", review.disposition)
        assertEquals("ADMIN", review.currentRole)
        pendingClick("online.pending.apply.intent")
        waitFor { pending.state.value.destination != null && !pending.state.value.busy }
        assertEquals(row.getString("kind"), pending.state.value.destination!!.kind)
        val dao = app.database.onlineSyncDao()
        val id = if (row.getString("kind") == "LIFECYCLE") {
            dao.recoveryClaims(session.scope(outlet), listOf(row.getString("originalId"))).single().recoveredOperationId!!
        } else requireNotNull(dao.recoveredAdminReference(session.scope(outlet), row.getString("originalId"))).operationId
        assertNotEquals(row.getString("originalId"), id)
        row.put("newId", id); persist()
        assertEquals(mapOf(row.getString("originalId") to false, id to false),
            api.operationStatuses(session, outlet, listOf(row.getString("originalId"), id)))
        return id
    }
    private fun continueToNormalPanel() {
        pendingClick("online.pending.continue")
        waitFor { !pending.state.value.opened }
    }
    private fun recreate() { scenario.recreate(); attachModels(); compose.waitForIdle() }
    private fun capture(scene: String) {
        compose.waitForIdle()
        var screenshot: Bitmap? = null
        repeat(5) { if (screenshot == null) { screenshot = instrumentation.uiAutomation.takeScreenshot(); if (screenshot == null) Thread.sleep(250) } }
        val bitmap = requireNotNull(screenshot)
        File(app.filesDir, "native-pending-admin-$phase-$scene.png").outputStream().use {
            check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it))
        }
        bitmap.recycle()
    }
    private fun model(admin: List<PendingBackupAdmin> = emptyList(), closings: List<PendingBackupClosing> = emptyList()) =
        OnlinePendingBackup(UUID.randomUUID().toString(), "2026-10-24T00:00:00Z", saved.getString("sourceDevice"), session.origin,
            PendingBackupAccount(session.identity.id, session.identity.username, session.identity.displayName),
            PendingBackupOutlet(outlet, "Admin native QA outlet"), closings, admin)
    private fun seedAdmin(kind: String, payload: JSONObject, revision: Long?): JSONObject {
        val source = PendingBackupAdmin(kind, UUID.randomUUID().toString(), revision, "SENT", true, payload.toString())
        return seed(model(admin = listOf(source)), kind, source.operationId, payload.toString())
    }
    private fun seed(packet: OnlinePendingBackup, kind: String, original: String, payload: String): JSONObject {
        val bytes = OnlinePendingBackupCodec.encode(packet)
        val accepted = OnlinePendingRecoveryInbox(app).accept(bytes)
        assertFalse(accepted.duplicate)
        assertEquals(sha(bytes), accepted.summary.sha256)
        val row = JSONObject().put("phase", phase).put("kind", kind).put("originalId", original)
            .put("backupId", packet.backupId).put("sourceDevice", packet.sourceDeviceId)
            .put("packetSha256", accepted.summary.sha256).put("packetBytes", bytes.size)
            .put("sourcePayloadSha256", sha(payload))
        saved.getJSONArray("cases").put(row); persist()
        return row
    }
    private suspend fun assertRecordedAndLocallyHandled(row: JSONObject) {
        val original = row.getString("originalId"); val id = row.getString("newId")
        assertEquals(mapOf(original to false, id to true), api.operationStatuses(session, outlet, listOf(original, id)))
        val dao = app.database.onlineSyncDao()
        if (row.getString("kind") == "LIFECYCLE") {
            val claim = dao.recoveryClaims(session.scope(outlet), listOf(original)).single()
            assertEquals(id, claim.recoveredOperationId); assertEquals(row.getString("packetSha256"), claim.fingerprint)
        } else {
            val archive = requireNotNull(dao.recoveredAdminReference(session.scope(outlet), original))
            assertFalse(archive.active); assertEquals(id, archive.operationId)
            assertEquals(row.getString("packetSha256"), archive.fingerprint)
            row.put("archivedPayloadSha256", sha(archive.payloadJson)).put("archiveInactive", true)
        }
        inspect(row)
        assertEquals("ALREADY_RECOVERED_LOCALLY", pending.state.value.intent!!.disposition)
        compose.onNodeWithTag("online.pending.apply.intent").assertDoesNotExist()
        row.put("originalRecorded", false).put("newRecorded", true).put("duplicateActionBlocked", true)
        capture("${saved.getJSONArray("cases").length()}-handled")
        closeInbox(); persist()
    }
    private fun newPasswordTrigger() { click("online.team.retry.password.open") }
    private fun newPasswordConfirm() { click("online.team.retry.password.confirm", false) }
    private suspend fun roster() = api.team(session, outlet, null, 100).also { assertNull(it.nextAfter) }.items

    @Test fun nativeAdministrativeRecoveryContinuesThroughNormalExplicitActions(): Unit = runBlocking {
        when (phase) {
            "settings" -> settingsJourney()
            "team" -> teamJourney()
            "catalog" -> catalogJourney()
            "lifecycle" -> lifecycleJourney()
        }
        assertEquals(expectedInbox.getValue(phase), OnlinePendingRecoveryInbox(app).list().size)
        assertNull(app.onlineRepository.pendingSettings(outlet))
        assertNull(app.onlineRepository.pendingTeam(outlet))
        assertNull(app.onlineRepository.pendingCatalog(outlet))
        assertEquals(saved.getString("legacySha256"), legacyHash())
        val all = saved.getJSONArray("cases")
        val current = JSONArray((phaseStartCaseCount until all.length()).map { all.getJSONObject(it) })
        if (phase == "lifecycle") api.logout(session)
        proof.put("phaseCases", current).put("allCases", all).put("inboxCount", expectedInbox.getValue(phase))
            .put("legacyAfterSha256", legacyHash()).put("legacyUnchanged", true)
            .put("allFinalSendsUsedProductionUi", true).put("passed", true)
        saved.remove("phaseStarted"); saved.put("nextPhaseIndex", phases.indexOf(phase) + 1); persist()
        File(app.filesDir, "native-pending-admin-$phase.json").writeText(proof.toString(2))
        instrumentation.sendStatus(2, Bundle().apply { putString("ccsAdminNativeProof", proof.toString()) })
    }

    private suspend fun settingsJourney() {
        val before = api.outletSettings(session, outlet)
        val desiredName = "Pulihan outlet ${run.take(8)}"
        val payload = JSONObject().put("name", desiredName).put("timezone", "Asia/Makassar").put("cutoffMinutes", 90)
        val row = seedAdmin("SETTINGS", payload, before.revision)
        inspect(row); capture("review")
        val id = applyReviewed(row)
        val operation = requireNotNull(app.onlineRepository.pendingSettings(outlet))
        assertEquals(id, operation.operationId); assertEquals(before.revision, operation.expectedRevision)
        assertEquals("QUEUED", operation.status); assertFalse(operation.wasSent)
        assertEquals(before, api.outletSettings(session, outlet))
        continueToNormalPanel()
        waitFor { settings.state.value.pending?.operationId == id && !settings.state.value.busy }
        compose.onNodeWithTag("online.settings.pending").performScrollTo().assertIsDisplayed()
        assertEquals(desiredName, settings.state.value.name)
        assertEquals("01:30", settings.state.value.cutoff)
        assertEquals("Asia/Makassar", settings.state.value.timezone)
        recreate()
        waitFor { settings.state.value.pending?.operationId == id && !settings.state.value.busy }
        assertEquals(before, api.outletSettings(session, outlet)); capture("held-after-recreation")
        click("online.settings.retry")
        waitFor { settings.state.value.pending == null && !settings.state.value.busy && settings.state.value.snapshot?.name == desiredName }
        val after = api.outletSettings(session, outlet)
        assertEquals(before.revision + 1, after.revision)
        assertEquals(desiredName, after.name); assertEquals("Asia/Makassar", after.timezone); assertEquals(90, after.cutoffMinutes)
        capture("confirmed"); click("online.settings.close", false)
        val archive = requireNotNull(app.database.onlineSyncDao().recoveredAdminReference(session.scope(outlet), row.getString("originalId")))
        assertFalse(archive.active)
        row.put("expectedRevision", before.revision).put("finalRevision", after.revision)
            .put("heldThroughContinuationAndRecreation", true).put("finalPayloadSha256", sha(payload.toString()))
        assertRecordedAndLocallyHandled(row)
    }

    private suspend fun teamJourney() {
        val username = "qa.recovered." + run.replace("-", "").take(20)
        val displayName = "Anggota pulihan ${run.take(8)}"
        val createPayload = JSONObject().put("username", username).put("displayName", displayName).put("role", "EMPLOYEE")
        val createdRow = seedAdmin("TEAM_CREATE", createPayload, null)
        inspect(createdRow); assertTrue(pending.state.value.intent!!.passwordRequired); capture("create-review")
        val createId = applyReviewed(createdRow)
        assertEquals("QUEUED", app.onlineRepository.pendingTeam(outlet)!!.status)
        assertFalse(app.onlineRepository.pendingTeam(outlet)!!.wasSent)
        assertTrue(roster().none { it.username == username })
        continueToNormalPanel()
        waitFor { team.state.value.pending?.operationId == createId && !team.state.value.busy }
        compose.onNodeWithTag("online.team.pending").performScrollTo().assertIsDisplayed()
        recreate()
        waitFor { team.state.value.pending?.operationId == createId && !team.state.value.busy }
        assertTrue(roster().none { it.username == username })
        click("online.team.retry")
        waitFor { team.state.value.passwordNeeded && !team.state.value.busy }
        compose.onNodeWithTag("online.team.error").assertTextContains("Tindakan belum dikirim.", substring = true)
        assertFalse(app.onlineRepository.pendingTeam(outlet)!!.wasSent)
        assertTrue(roster().none { it.username == username })
        newPasswordTrigger(); capture("fresh-password-required")
        compose.onNodeWithTag("online.team.retry.password").performScrollTo().performTextReplacement(fixturePassword)
        Espresso.closeSoftKeyboard(); newPasswordConfirm()
        waitFor { team.state.value.pending == null && !team.state.value.busy }
        val member = roster().single { it.username == username }
        assertEquals(displayName, member.name); assertEquals("EMPLOYEE", member.role); assertTrue(member.active)
        val stored = requireNotNull(app.database.onlineSyncDao().recoveredAdminReference(session.scope(outlet), createdRow.getString("originalId")))
        assertFalse(stored.active); assertFalse(stored.payloadJson.contains(fixturePassword)); assertFalse(stored.payloadJson.contains("password", true))
        createdRow.put("createdAccountId", member.id).put("heldThroughContinuationAndRecreation", true)
            .put("passwordRequiredOutsideStoredDraft", true).put("passwordAbsentFromStoredPayload", true)
        capture("created"); click("online.team.close", false); assertRecordedAndLocallyHandled(createdRow)

        val updatePayload = JSONObject().put("accountId", member.id).put("role", "SUPERVISOR").put("active", false)
        val updatedRow = seedAdmin("TEAM_UPDATE", updatePayload, member.revision)
        inspect(updatedRow); assertFalse(pending.state.value.intent!!.passwordRequired); capture("update-review")
        val updateId = applyReviewed(updatedRow)
        val operation = requireNotNull(app.onlineRepository.pendingTeam(outlet))
        assertEquals("UPDATE_MEMBERSHIP", operation.kind)
        assertEquals("QUEUED", operation.status); assertFalse(operation.wasSent)
        assertEquals(member.revision, JSONObject(operation.payload).getLong("expectedRevision"))
        assertEquals(member, api.member(session, outlet, member.id))
        continueToNormalPanel()
        waitFor { team.state.value.pending?.operationId == updateId && !team.state.value.busy }
        recreate()
        waitFor { team.state.value.pending?.operationId == updateId && !team.state.value.busy }
        assertEquals(member, api.member(session, outlet, member.id)); capture("update-held")
        click("online.team.retry")
        waitFor { team.state.value.pending == null && !team.state.value.busy }
        val updated = api.member(session, outlet, member.id)
        assertEquals("SUPERVISOR", updated.role); assertFalse(updated.active)
        assertEquals(member.revision + 1, updated.revision)
        updatedRow.put("targetAccountId", member.id).put("expectedRevision", member.revision).put("finalRevision", updated.revision)
            .put("heldThroughContinuationAndRecreation", true).put("newOperationPayloadSha256", sha(operation.payload))
        capture("updated"); click("online.team.close", false); assertRecordedAndLocallyHandled(updatedRow)
    }

    private suspend fun catalogJourney() {
        val before = api.catalog(session, outlet)
        val historical = api.create(session, outlet, "2026-07-06")
        val desired = before.catalog.copy(menus = before.catalog.menus.map { it.copy(name = "Pulihan " + it.name) }).validated()
        assertTrue(desired.menus.isNotEmpty()); assertTrue(desired.ingredients.isNotEmpty())
        val row = seedAdmin("CATALOG", desired.json(), before.revision)
        inspect(row); capture("review")
        pendingClick("online.pending.catalog.file")
        waitFor { pending.state.value.catalogSide == "file" }
        compose.onNodeWithTag("online.pending.page").performScrollToNode(hasText(desired.menus.first().name))
        compose.onNodeWithText(desired.menus.first().name).assertIsDisplayed(); capture("file-recipes")
        click("online.pending.back", false)
        waitFor { pending.state.value.catalogSide == null }
        pendingClick("online.pending.catalog.current")
        waitFor { pending.state.value.catalogSide == "current" }
        compose.onNodeWithTag("online.pending.page").performScrollToNode(hasText(before.catalog.menus.first().name))
        compose.onNodeWithText(before.catalog.menus.first().name).assertIsDisplayed(); capture("server-recipes")
        click("online.pending.back", false); waitFor { pending.state.value.catalogSide == null }
        val id = applyReviewed(row)
        val pendingCatalog = requireNotNull(app.onlineRepository.pendingCatalog(outlet))
        assertEquals("EDITING", pendingCatalog.status); assertFalse(pendingCatalog.wasSent)
        assertEquals(before.revision, pendingCatalog.expectedRevision)
        assertEquals(desired, OnlineCatalog.parse(JSONObject(pendingCatalog.catalogJson)))
        assertEquals(before, api.catalog(session, outlet))
        continueToNormalPanel()
        waitFor { catalog.state.value.pending?.operationId == id && !catalog.state.value.busy }
        recreate()
        waitFor { catalog.state.value.pending?.operationId == id && !catalog.state.value.busy }
        assertEquals(desired, catalog.state.value.working)
        assertEquals(before, api.catalog(session, outlet)); capture("editable-after-recreation")
        val first = desired.menus.first(); val editedName = "Review " + first.name
        catalogClick("online.catalog.edit.${first.id}")
        compose.onNodeWithTag("online.catalog.form.name").performScrollTo().performTextReplacement(editedName)
        Espresso.closeSoftKeyboard(); click("online.catalog.form.save", false)
        waitFor { !exists("online.catalog.form.save") && !catalog.state.value.busy }
        val edited = desired.copy(menus = desired.menus.map { if (it.id == first.id) it.copy(name = editedName) else it }).validated()
        assertEquals(edited, catalog.state.value.working)
        assertEquals(id, app.onlineRepository.pendingCatalog(outlet)!!.operationId)
        assertEquals(before, api.catalog(session, outlet))
        catalogClick("online.catalog.publish"); capture("publish-review")
        click("online.catalog.publish.confirm", false)
        waitFor { catalog.state.value.pending == null && !catalog.state.value.busy }
        val published = api.catalog(session, outlet)
        assertEquals(before.revision + 1, published.revision); assertEquals(edited, published.catalog)
        val historicalAfter = api.fetch(session, outlet, historical.header.closingId)
        assertEquals(historical.header.snapshotJson, historicalAfter.header.snapshotJson)
        val newer = api.create(session, outlet, "2026-07-07")
        assertEquals(published.versionId, JSONObject(newer.header.snapshotJson).getString("catalogVersionId"))
        assertEquals(edited, OnlineCatalog.parse(JSONObject(newer.header.snapshotJson).getJSONObject("catalog")))
        row.put("expectedRevision", before.revision).put("finalRevision", published.revision)
            .put("oldClosing", historical.header.closingId).put("newClosing", newer.header.closingId)
            .put("oldCatalogVersion", before.versionId).put("newCatalogVersion", published.versionId)
            .put("editedMenuId", first.id).put("publishedPayloadSha256", sha(edited.json().toString()))
            .put("editableDraftPreserved", true).put("oldSnapshotUnchanged", true)
            .put("heldThroughContinuationAndRecreation", true)
        capture("published"); click("online.catalog.close", false); assertRecordedAndLocallyHandled(row)
    }

    private suspend fun lifecycleJourney() {
        val initial = api.create(session, outlet, "2026-07-08"); val closingId = initial.header.closingId
        for ((index, action) in listOf("submit", "return", "submit", "lock", "reopen").withIndex()) {
            val before = api.fetch(session, outlet, closingId)
            val reason = if (action in setOf("return", "reopen")) "Koreksi pulihan $action ${run.take(8)}" else ""
            val original = UUID.randomUUID().toString()
            val life = PendingBackupLifecycle(original, action, before.header.generation, before.header.revision, reason, "SENT", true)
            val closing = PendingBackupClosing(closingId, "2026-07-08", before.header.generation, before.header.revision,
                before.header.state, emptyList(), emptyList(), life)
            val payload = JSONObject().put("action", action).put("reason", reason).toString()
            val row = seed(model(closings = listOf(closing)), "LIFECYCLE", original, payload)
                .put("closing", closingId).put("date", "2026-07-08").put("action", action).put("reason", reason)
            persist()
            inspect(row)
            assertEquals(before.header.state, pending.state.value.intent!!.currentState)
            capture("$index-$action-review")
            val id = applyReviewed(row)
            val operation = requireNotNull(app.database.onlineSyncDao().lifecycle(session.scope(outlet), closingId))
            assertEquals(id, operation.operationId); assertEquals(action, operation.action); assertEquals(reason, operation.reason)
            assertEquals("QUEUED", operation.status); assertFalse(operation.wasSent)
            assertEquals(before.header, api.fetch(session, outlet, closingId).header)
            continueToNormalPanel()
            waitFor { online.state.value.closingId == closingId && online.state.value.lifecycle?.operationId == id && !online.state.value.syncing }
            if (exists("online.nav.menu")) compose.onNodeWithTag("online.nav.menu").assertTextContains("Review")
            else compose.onNodeWithTag("online.nav.Review").assertIsSelected()
            recreate()
            waitFor { online.state.value.lifecycle?.operationId == id && !online.state.value.syncing }
            assertEquals(before.header, api.fetch(session, outlet, closingId).header)
            capture("$index-$action-held")
            click("online.sync")
            waitFor { online.state.value.lifecycle == null && !online.state.value.syncing && online.state.value.closingState != before.header.state }
            val after = api.fetch(session, outlet, closingId)
            val expectedState = when (action) { "submit" -> "SUBMITTED"; "lock" -> "LOCKED"; else -> "DRAFT" }
            assertEquals(expectedState, after.header.state)
            assertEquals(before.header.revision + 1, after.header.revision)
            assertEquals(before.header.generation + if (action in setOf("return", "reopen")) 1L else 0L, after.header.generation)
            assertNull(app.database.onlineSyncDao().lifecycle(session.scope(outlet), closingId))
            row.put("beforeState", before.header.state).put("afterState", after.header.state)
                .put("beforeGeneration", before.header.generation).put("afterGeneration", after.header.generation)
                .put("beforeRevision", before.header.revision).put("afterRevision", after.header.revision)
                .put("heldThroughContinuationAndRecreation", true)
            capture("$index-$action-confirmed"); assertRecordedAndLocallyHandled(row)
        }
        proof.put("lifecycleClosing", closingId).put("finalState", "DRAFT").put("finalGeneration", initial.header.generation + 2)
    }
}
