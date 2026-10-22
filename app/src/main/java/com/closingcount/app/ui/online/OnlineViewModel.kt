package com.closingcount.app.ui.online

import android.app.Application
import android.content.Context
import androidx.core.content.edit
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.closingcount.app.BuildConfig
import com.closingcount.app.ClosingCountApplication
import com.closingcount.app.data.local.OnlineClosingEntity
import com.closingcount.app.data.local.OnlineLifecycleEntity
import com.closingcount.app.data.online.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.IOException
import java.time.LocalDate

data class OnlineMenuUi(val id: String, val name: String, val category: String, val quantity: Int,
    val serverQuantity: Int, val revision: Long, val generation: Long, val status: String, val actor: String)
data class OnlineHistoryUi(val id: String, val date: String, val state: String)
data class OnlineReviewLine(val name: String, val quantity: Long)
private data class RenderedClosing(val state: String, val revision: Long, val generation: Long, val menus: List<OnlineMenuUi>,
    val pending: Int, val lifecycle: OnlineLifecycleEntity?, val reviewMenus: List<OnlineReviewLine>,
    val ingredients: List<OnlineReviewLine>, val submittedBy: String?, val lockedBy: String?)
data class OnlineUiState(val initialized: Boolean = false, val identity: OnlineIdentity? = null, val loginVisible: Boolean = true,
    val savedWork:Int=0,val pendingOwnAccess:Int=0,
    val outletId: String = "", val date: String = LocalDate.now().toString(), val closingId: String? = null,
    val closingState: String = "", val revision: Long = 0, val generation: Long = 1,
    val lifecycle: OnlineLifecycleEntity? = null, val reviewMenus: List<OnlineReviewLine> = emptyList(),
    val ingredients: List<OnlineReviewLine> = emptyList(), val submittedBy: String? = null, val lockedBy: String? = null,
    val menus: List<OnlineMenuUi> = emptyList(), val history: List<OnlineHistoryUi> = emptyList(),
    val buffers: Map<String, String> = emptyMap(), val pending: Int = 0, val working: Boolean = false,
    val syncing: Boolean = false, val syncVerified: Boolean = false, val connectionUnavailable: Boolean = false, val error: String? = null,
    val serverHistory: List<OnlineHistoryUi> = emptyList(), val historyCursor: String? = null,
    val historyLoaded: Boolean = false, val historyLoading: Boolean = false, val historyError: String? = null,
    val historyDetail: OnlineHistoryUi? = null, val audit: List<OnlineAuditRecord> = emptyList(), val auditCursor: String? = null,
    val reports: List<OnlineReportRecord> = emptyList(), val reportCursor: Long? = null, val frozenReport: String? = null,
    val historyMenuNames: Map<String, String> = emptyMap(), val historyTimezone: String = "Asia/Jakarta",
    val detailLoading: Boolean = false, val detailError: String? = null,
    val notice: String? = null, val authRequired: Boolean = false, val confirmSwitch: String? = null, val confirmLogout: Boolean = false) {
    val dirty get() = buffers.isNotEmpty()
    val valid get() = buffers.values.all { it.matches(Regex("[0-9]+")) && (it.toIntOrNull()?.let { n -> n in 0..9999 } == true) }
    val total get() = menus.sumOf { it.quantity.toLong() }
}

class OnlineViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as ClosingCountApplication
    private val repo = app.onlineRepository
    private val dao = app.database.onlineSyncDao()
    private val preferences = app.getSharedPreferences("ccs_online_ui", Context.MODE_PRIVATE)
    private val mutable = MutableStateFlow(OnlineUiState())
    val state: StateFlow<OnlineUiState> = mutable.asStateFlow()
    private var historyJob: Job? = null
    private var serverHistoryJob: Job? = null
    private var detailJob: Job? = null
    private var historyEpoch = 0
    private var detailEpoch = 0
    private var closingJob: Job? = null
    private var syncJob: Job? = null
    private var epoch = 0
    private var foreground = false
    private var candidate: OnlineSession? = null
    private var cached = emptyList<OnlineClosingEntity>()

    init { viewModelScope.launch {
        try { repo.initialize(); val session = repo.session()
            mutable.update { it.copy(initialized = true, identity = session?.identity, loginVisible = session == null) }
            if (session != null) startOutlet(session.identity.outlets.firstOrNull { it.id == preferences.getString("outlet", "") }?.id
                ?: session.identity.outlets.firstOrNull()?.id.orEmpty())
        } catch (e: Exception) { mutable.update { it.copy(initialized = true, error = message(e)) } }
    } }

    fun login(username: String, password: String) {
        if (state.value.working) return
        mutable.update { it.copy(working = true, error = null) }
        viewModelScope.launch {
            try {
                require(BuildConfig.ONLINE_API_ORIGIN.isNotBlank()) { "Layanan belum dikonfigurasi. Hubungi pengelola." }
                candidate = repo.login(BuildConfig.ONLINE_API_ORIGIN, username.trim(), password)
                activateCandidate(false)
            } catch (_: PendingOnlineDraftsException) {
                mutable.update { it.copy(confirmSwitch = candidate?.identity?.displayName) }
            } catch (e: Exception) { mutable.update { it.copy(error = message(e)) }
            } finally { mutable.update { it.copy(working = false) } }
        }
    }

    private suspend fun activateCandidate(confirmed: Boolean) {
        val prepared = requireNotNull(candidate)
        val previousOutlet = state.value.outletId.ifBlank { preferences.getString("outlet", "").orEmpty() }
        repo.activate(prepared, confirmed); candidate = null; stopSync()
        mutable.update { OnlineUiState(initialized = true, identity = prepared.identity, loginVisible = false) }
        startOutlet(prepared.identity.outlets.firstOrNull { it.id == previousOutlet }?.id ?: prepared.identity.outlets.firstOrNull()?.id.orEmpty())
    }

    fun confirmSwitch(confirmed: Boolean) { viewModelScope.launch {
        mutable.update { it.copy(confirmSwitch = null) }
        try { if (confirmed) activateCandidate(true) else { candidate?.let { repo.cancelPreparedLogin(it) }; candidate = null } }
        catch (e: Exception) { mutable.update { it.copy(error = message(e)) } }
    } }

    fun showLogin() { clearRemoteHistory(); stopSync(); mutable.update { it.copy(loginVisible = true, error = null) } }
    fun cancelLogin() { if (repo.session() != null) mutable.update { it.copy(loginVisible = false, error = null) } }
    fun discardForm() { mutable.update { it.copy(buffers = emptyMap()) } }
    fun refreshAccount() {
        if(state.value.dirty)return
        stopSync(); viewModelScope.launch {
            try {val identity=repo.refreshAccount();mutable.update {it.copy(identity=identity)}
                startOutlet(identity.outlets.firstOrNull {it.id==state.value.outletId}?.id ?: identity.outlets.firstOrNull()?.id.orEmpty())
            } catch(cancel:CancellationException){throw cancel
            } catch(e:Exception){mutable.update {it.copy(error=message(e))}}
        }
    }

    fun selectOutlet(id: String) { if (!state.value.dirty) startOutlet(id) }
    private fun startOutlet(id: String) {
        clearRemoteHistory(); historyJob?.cancel(); closingJob?.cancel(); stopSync(); cached = emptyList()
        val session = repo.session() ?: return
        checkSavedAccess()
        val scope = session.identity.outlets.firstOrNull { it.id == id }?.let { session.scope(id) } ?: run {
            mutable.update { it.copy(outletId = "", error = "Akun ini belum memiliki akses outlet aktif.") }; return
        }
        val remembered = if (preferences.getString("scope", "") == scope) preferences.getString("date", null) else null
        val date = remembered?.takeIf { runCatching { LocalDate.parse(it) }.isSuccess } ?: LocalDate.now().toString()
        mutable.update { it.copy(outletId = id, date = date, closingId = null, menus = emptyList(), closingState = "",
            buffers = emptyMap(), history = emptyList(), pending = 0, lifecycle = null, reviewMenus = emptyList(), ingredients = emptyList(),
            submittedBy = null, lockedBy = null, syncVerified = false, error = null, notice = null) }
        rememberSelection()
        historyJob = viewModelScope.launch {
            dao.observeClosings(scope).collect { headers ->
                cached = headers
                val history = headers.mapNotNull { h -> runCatching {
                    OnlineHistoryUi(h.closingId, JSONObject(h.snapshotJson).getString("businessDate"), h.state)
                }.getOrNull() }.sortedByDescending { it.date }
                mutable.update { it.copy(history = history) }
                val selected = history.firstOrNull { it.date == state.value.date }
                if (selected != null && selected.id != state.value.closingId) bind(selected.id)
            }
        }
        if (remembered == null) viewModelScope.launch {
            try { val serverDate = repo.businessDate(id)
                if (state.value.outletId == id && state.value.date == date && state.value.closingId == null && !state.value.dirty) selectDate(serverDate)
            } catch (cancel: CancellationException) { throw cancel
            } catch (e: Exception) { mutable.update { it.copy(connectionUnavailable = e is IOException,
                error = if (e is OnlineApiException) message(e) else it.error, authRequired = e is OnlineApiException && needsLogin(e.code)) } }
        }
    }

    private fun checkSavedAccess(){
        val actor=repo.session()?.identity?.id?:return
        viewModelScope.launch{try{val count=repo.pendingCount();val own=repo.pendingOwnTeamAccessCount()
            if(state.value.identity?.id==actor)mutable.update{it.copy(savedWork=count,pendingOwnAccess=own)}
        }catch(c:CancellationException){throw c}catch(e:Exception){if(state.value.identity?.id==actor)mutable.update{it.copy(error=message(e))}}}
    }
    fun reconcileOwnTeamAccess(){
        if(state.value.working)return
        mutable.update{it.copy(working=true,error=null)}
        viewModelScope.launch{try{val n=repo.reconcileOwnTeamAccess();mutable.update{it.copy(notice="$n tindakan akses akun terkonfirmasi.")};checkSavedAccess()}
            catch(c:CancellationException){throw c}catch(e:Exception){mutable.update{it.copy(error=message(e))}}finally{mutable.update{it.copy(working=false)}}}
    }

    fun selectDate(date: String) {
        if (state.value.dirty) return
        LocalDate.parse(date); closingJob?.cancel(); stopSync()
        mutable.update { it.copy(date = date, closingId = null, menus = emptyList(), closingState = "", pending = 0, lifecycle = null,
            reviewMenus = emptyList(), ingredients = emptyList(), submittedBy = null, lockedBy = null, syncVerified = false, error = null, notice = null) }
        rememberSelection()
        state.value.history.firstOrNull { it.date == date }?.let { bind(it.id) }
    }

    private fun rememberSelection() { repo.session()?.let { session ->
        val s = state.value; if (s.outletId.isNotEmpty()) preferences.edit {
            putString("scope", session.scope(s.outletId)); putString("outlet", s.outletId); putString("date", s.date)
        }
    } }

    fun openClosing() {
        val current = state.value; if (current.working || current.outletId.isEmpty()) return
        mutable.update { it.copy(working = true, error = null) }
        viewModelScope.launch {
            try { val id = repo.create(current.outletId, current.date)
                if (state.value.outletId == current.outletId && state.value.date == current.date) bind(id)
            } catch (e: Exception) { mutable.update { it.copy(error = message(e)) }
            } finally { mutable.update { it.copy(working = false) } }
        }
    }

    private fun bind(id: String) {
        closingJob?.cancel(); stopSync()
        val selected = state.value; val session = repo.session() ?: return
        val scope = session.scope(selected.outletId)
        mutable.update { it.copy(closingId = id, buffers = emptyMap(), syncVerified = false, error = null) }
        closingJob = viewModelScope.launch {
            combine(dao.observeClosing(scope, id), dao.observeEntries(scope, id), dao.observeOperations(scope, id), dao.observeLifecycle(scope, id)) { header, entries, ops, lifecycle ->
                if (header == null) return@combine null
                val json = JSONObject(header.snapshotJson); val catalog = json.getJSONObject("catalog")
                val categories = catalog.getJSONArray("menuCategories")
                val names = (0 until categories.length()).associate { val item = categories.getJSONObject(it); item.getString("id") to item.getString("name") }
                val raw = catalog.getJSONArray("menus"); val actors = json.getJSONArray("entries")
                val actorNames = (0 until actors.length()).associate { val item = actors.getJSONObject(it)
                    item.getString("menuId") to (item.optJSONObject("updatedBy")?.optString("displayName") ?: "Belum diubah") }
                val byId = entries.associateBy { it.menuId }
                val menus = (0 until raw.length()).mapNotNull { index ->
                    val item = raw.getJSONObject(index); val row = byId[item.getString("id")] ?: return@mapNotNull null
                    OnlineMenuUi(row.menuId, item.getString("name"), names[item.getString("categoryId")] ?: "Menu", row.localQuantity,
                        row.serverQuantity, row.serverRevision, row.generation, row.status, actorNames[row.menuId].orEmpty())
                }
                val serverQuantities = (0 until actors.length()).associate { val item = actors.getJSONObject(it); item.getString("menuId") to item.getLong("quantity") }
                val reviewMenus = (0 until raw.length()).map { val item = raw.getJSONObject(it)
                    OnlineReviewLine(item.getString("name"), serverQuantities[item.getString("id")] ?: 0L) }
                val rawTotals = json.getJSONArray("ingredientTotals")
                val ingredients = (0 until rawTotals.length()).map { val item = rawTotals.getJSONObject(it)
                    OnlineReviewLine(item.getString("name"), item.getLong("total")) }
                RenderedClosing(header.state, header.revision, header.generation, menus, ops.size, lifecycle, reviewMenus,
                    ingredients, json.optJSONObject("submittedBy")?.optString("displayName"), json.optJSONObject("lockedBy")?.optString("displayName"))
            }.catch { mutable.update { it.copy(error = "Data tersimpan belum dapat dibaca. Coba muat ulang.") } }
                .collect { value -> if (value != null) mutable.update { it.copy(closingState = value.state, revision = value.revision,
                    generation = value.generation, menus = value.menus, pending = value.pending, lifecycle = value.lifecycle,
                    reviewMenus = value.reviewMenus, ingredients = value.ingredients, submittedBy = value.submittedBy, lockedBy = value.lockedBy) } }
        }
        if (foreground) sync()
    }

    private fun clearRemoteHistory() {
        historyEpoch++; detailEpoch++; serverHistoryJob?.cancel(); detailJob?.cancel()
        mutable.update { it.copy(serverHistory = emptyList(), historyCursor = null, historyLoaded = false,
            historyLoading = false, historyError = null, historyDetail = null, audit = emptyList(), auditCursor = null,
            reports = emptyList(), reportCursor = null, frozenReport = null, historyMenuNames = emptyMap(), detailLoading = false, detailError = null) }
    }

    fun loadHistory(more: Boolean = false) {
        val s = state.value
        if (s.historyLoading || s.outletId.isEmpty() || s.loginVisible || (more && s.historyCursor == null)) return
        val run = historyEpoch; val cursor = if (more) s.historyCursor else null
        mutable.update { it.copy(historyLoading = true, historyError = null) }
        serverHistoryJob = viewModelScope.launch {
            try { val page = repo.history(s.outletId, cursor)
                if (run != historyEpoch) return@launch
                val rows = page.items.map { OnlineHistoryUi(it.id,it.date,it.state) }
                mutable.update { it.copy(serverHistory = (if (more) it.serverHistory + rows else rows).distinctBy { row -> row.id },
                    historyLoaded = true, historyCursor = page.nextBefore) }
            } catch (cancel: CancellationException) { throw cancel
            } catch (e: Exception) { if (run == historyEpoch) mutable.update { it.copy(historyError = message(e),
                authRequired = it.authRequired || (e is OnlineApiException && needsLogin(e.code))) }
            } finally { if (run == historyEpoch) mutable.update { it.copy(historyLoading = false) } }
        }
    }

    fun closeHistoryDetail() {
        detailEpoch++; detailJob?.cancel(); mutable.update { it.copy(historyDetail = null, audit = emptyList(), reports = emptyList(),
            auditCursor = null, reportCursor = null, frozenReport = null, detailLoading = false, detailError = null) }
    }
    fun closeFrozenReport() { mutable.update { it.copy(frozenReport = null) } }
    fun inspectHistory(row: OnlineHistoryUi) {
        detailJob?.cancel(); val s = state.value; val run = historyEpoch; val detailRun = ++detailEpoch
        mutable.update { it.copy(historyDetail = row, audit = emptyList(), reports = emptyList(), auditCursor = null,
            reportCursor = null, frozenReport = null, historyMenuNames = emptyMap(), detailLoading = true, detailError = null) }
        detailJob = viewModelScope.launch {
            try { val snapshot = repo.readClosing(s.outletId,row.id)
                val json = JSONObject(snapshot.header.snapshotJson)
                val timezone = java.time.ZoneId.of(json.getString("timezone")).id
                val raw = json.getJSONObject("catalog").getJSONArray("menus")
                val names = (0 until raw.length()).associate { val menu = raw.getJSONObject(it); menu.getString("id") to menu.getString("name") }
                val audit = repo.audit(s.outletId,row.id); val reports = repo.reports(s.outletId,row.id)
                if (run == historyEpoch && detailRun == detailEpoch && state.value.historyDetail?.id == row.id) mutable.update { it.copy(
                    historyDetail = row.copy(state = snapshot.header.state), historyMenuNames = names, historyTimezone = timezone,
                    audit = audit.items, auditCursor = audit.nextAfter, reports = reports.items, reportCursor = reports.nextBefore) }
            } catch (cancel: CancellationException) { throw cancel
            } catch (e: Exception) { if (run == historyEpoch && detailRun == detailEpoch) mutable.update { it.copy(detailError = message(e), authRequired = it.authRequired || (e is OnlineApiException && needsLogin(e.code))) }
            } finally { if (run == historyEpoch && detailRun == detailEpoch && state.value.historyDetail?.id == row.id) mutable.update { it.copy(detailLoading = false) } }
        }
    }
    fun loadMoreDetail(reports: Boolean) {
        val s = state.value; val row = s.historyDetail ?: return; val run = historyEpoch
        if (s.detailLoading || (if (reports) s.reportCursor == null else s.auditCursor == null)) return
        val detailRun = ++detailEpoch
        mutable.update { it.copy(detailLoading = true, detailError = null) }
        detailJob = viewModelScope.launch {
            try {
                if (reports) { val page = repo.reports(s.outletId,row.id,s.reportCursor)
                    if (run == historyEpoch && detailRun == detailEpoch && state.value.historyDetail?.id == row.id) mutable.update { it.copy(
                        reports = (it.reports + page.items).distinctBy { report -> report.generation }, reportCursor = page.nextBefore) }
                } else { val page = repo.audit(s.outletId,row.id,requireNotNull(s.auditCursor))
                    if (run == historyEpoch && detailRun == detailEpoch && state.value.historyDetail?.id == row.id) mutable.update { it.copy(
                        audit = (it.audit + page.items).distinctBy { audit -> audit.id }, auditCursor = page.nextAfter) }
                }
            } catch (cancel: CancellationException) { throw cancel
            } catch (e: Exception) { if (run == historyEpoch && detailRun == detailEpoch) mutable.update { it.copy(detailError = message(e), authRequired = it.authRequired || (e is OnlineApiException && needsLogin(e.code))) }
            } finally { if (run == historyEpoch && detailRun == detailEpoch && state.value.historyDetail?.id == row.id) mutable.update { it.copy(detailLoading = false) } }
        }
    }
    fun openReport(generation: Long) {
        val s = state.value; val row = s.historyDetail ?: return; val run = historyEpoch
        if (s.detailLoading) return
        val detailRun = ++detailEpoch
        mutable.update { it.copy(detailLoading = true, detailError = null, frozenReport = null) }
        detailJob = viewModelScope.launch {
            try { val report = repo.report(s.outletId,row.id,generation)
                if (run == historyEpoch && detailRun == detailEpoch && state.value.historyDetail?.id == row.id) mutable.update { it.copy(frozenReport = report) }
            } catch (cancel: CancellationException) { throw cancel
            } catch (e: Exception) { if (run == historyEpoch && detailRun == detailEpoch) mutable.update { it.copy(detailError = message(e), authRequired = it.authRequired || (e is OnlineApiException && needsLogin(e.code))) }
            } finally { if (run == historyEpoch && detailRun == detailEpoch && state.value.historyDetail?.id == row.id) mutable.update { it.copy(detailLoading = false) } }
        }
    }
    fun openHistory(row: OnlineHistoryUi) {
        if (state.value.working || state.value.dirty) return
        selectDate(row.date)
        if (state.value.closingId == row.id) return
        val s = state.value; val run = historyEpoch
        mutable.update { it.copy(working = true, error = null) }
        viewModelScope.launch {
            try { repo.readClosing(s.outletId,row.id)
                if (run == historyEpoch && state.value.date == row.date) bind(row.id)
            } catch (cancel: CancellationException) { throw cancel
            } catch (e: Exception) { if (run == historyEpoch) mutable.update { it.copy(error = message(e), authRequired = it.authRequired || (e is OnlineApiException && needsLogin(e.code))) }
            } finally { if (run == historyEpoch) mutable.update { it.copy(working = false) } }
        }
    }

    fun change(menuId: String, value: String) { mutable.update {
        val buffers = if (it.menus.firstOrNull { m -> m.id == menuId }?.quantity?.toString() == value) it.buffers - menuId else it.buffers + (menuId to value)
        it.copy(buffers = buffers, error = null, notice = null)
    } }
    fun save() {
        val s = state.value; val id = s.closingId ?: return
        if (s.working || !s.valid || !s.dirty) return
        val quantities = s.buffers.mapValues { it.value.toInt() }
        mutable.update { it.copy(working = true, syncVerified = false, error = null) }
        viewModelScope.launch {
            try { repo.save(s.outletId, id, quantities)
                mutable.update { it.copy(buffers = emptyMap(), notice = "Draft tersimpan di perangkat.") }; sync()
            } catch (e: Exception) { mutable.update { it.copy(error = message(e)) }
            } finally { mutable.update { it.copy(working = false) } }
        }
    }

    fun resume() { foreground = true; sync() }
    fun pause() { foreground = false; stopSync() }
    private fun stopSync() { epoch++; syncJob?.cancel(); mutable.update { it.copy(syncing = false) } }
    fun sync() {
        val s = state.value; val id = s.closingId ?: return
        if (!foreground || s.loginVisible || s.syncing) return
        stopSync(); val run = epoch
        syncJob = viewModelScope.launch {
            try {
                repeat(4) {
                    if (!foreground || run != epoch) return@launch
                    mutable.update { it.copy(syncing = true) }
                    val result = repo.sync(s.outletId, id)
                    if (run != epoch) return@launch
                    mutable.update { it.copy(syncing = false) }
                    when (result) {
                        OnlineSyncResult.Synced -> { mutable.update { it.copy(connectionUnavailable = false, syncVerified = true, authRequired = false, notice = null, error = null) }; return@launch }
                        is OnlineSyncResult.Blocked -> { mutable.update { it.copy(connectionUnavailable = false, authRequired = needsLogin(result.code), error = codeMessage(result.code)) }; return@launch }
                        is OnlineSyncResult.Retry -> { mutable.update { it.copy(connectionUnavailable = true) }; delay(result.afterMillis) }
                        OnlineSyncResult.MorePending -> delay(250)
                    }
                }
            } catch (cancel: CancellationException) { throw cancel
            } catch (e: Exception) { if (run == epoch) mutable.update { it.copy(error = message(e)) }
            } finally { if (run == epoch) mutable.update { it.copy(syncing = false) } }
        }
    }

    fun resolve(menu: OnlineMenuUi, reapply: Boolean) {
        val s = state.value; val id = s.closingId ?: return
        if (s.working) return
        stopSync(); mutable.update { it.copy(working = true, error = null) }
        viewModelScope.launch {
            try { repo.resolve(s.outletId, id, menu.id, reapply, menu.revision, menu.generation); sync()
            } catch (e: Exception) { mutable.update { it.copy(error = message(e)) }
            } finally { mutable.update { it.copy(working = false) } }
        }
    }

    fun logout(confirmed: Boolean = false) {
        clearRemoteHistory(); stopSync(); viewModelScope.launch {
            try { val revoked = repo.logout(confirmed); historyJob?.cancel(); closingJob?.cancel()
                mutable.value = OnlineUiState(initialized = true, notice = if (revoked) null else "Keluar dari perangkat. Koneksi server belum tersedia.")
            } catch (_: PendingOnlineDraftsException) { mutable.update { it.copy(confirmLogout = true) }
            } catch (e: Exception) { mutable.update { it.copy(error = message(e)) } }
        }
    }
    fun cancelLogout() { mutable.update { it.copy(confirmLogout = false) } }

    fun lifecycle(action: String, reason: String, reviewed: OnlineUiState) {
        val current = state.value; val id = current.closingId ?: return
        if (current.working || current.dirty || !current.syncVerified || current.pending != 0 || current.lifecycle != null) return
        if (current.closingId != reviewed.closingId || current.outletId != reviewed.outletId) return
        stopSync(); mutable.update { it.copy(working = true, syncVerified = false, error = null) }
        viewModelScope.launch {
            try { repo.lifecycle(current.outletId, id, action, reason, reviewed.generation, reviewed.revision)
                mutable.update { it.copy(notice = "Tindakan tersimpan di perangkat. Menunggu konfirmasi server.") }; sync()
            } catch (e: Exception) { mutable.update { it.copy(error = message(e)) }
            } finally { mutable.update { it.copy(working = false) } }
        }
    }

    fun dismissLifecycle() {
        val s = state.value; val id = s.closingId ?: return; val op = s.lifecycle ?: return
        if (s.working || op.status !in setOf("CONFLICT", "FAILED")) return
        stopSync(); mutable.update { it.copy(working = true, syncVerified = false) }
        viewModelScope.launch {
            try { repo.dismissLifecycle(s.outletId, id, op.operationId); mutable.update { it.copy(error = null) }; sync()
            } catch (e: Exception) { mutable.update { it.copy(error = message(e)) }
            } finally { mutable.update { it.copy(working = false) } }
        }
    }
    private fun needsLogin(code: String) = code in setOf("SESSION_REQUIRED", "SESSION_CHANGED", "AUTH_REQUIRED", "ACCOUNT_DISABLED", "OUTLET_FORBIDDEN")
    private fun message(e: Exception): String = when (e) {
        is OnlineApiException -> codeMessage(e.code)
        is OnlineConflictChangedException -> e.message.orEmpty()
        is IOException -> "Koneksi belum tersedia. Draft yang sudah disimpan tetap aman di perangkat ini."
        else -> "Tindakan belum berhasil. Periksa data dan coba lagi."
    }
    private fun codeMessage(code: String): String = when (code) {
        "INVALID_CREDENTIALS" -> "Username atau password tidak sesuai."
        "SESSION_REQUIRED", "SESSION_CHANGED", "AUTH_REQUIRED", "ACCOUNT_DISABLED", "OUTLET_FORBIDDEN" -> "Sesi atau akses akun perlu diperiksa. Masuk kembali dengan akun yang sama untuk melanjutkan draft."
        "ENTRY_CONFLICT", "PULL_CONFLICT", "CONFLICT", "STALE_ACK", "GENERATION_CONFLICT", "LIFECYCLE_REVIEW_CHANGED", "REVISION_CONFLICT" -> "Ada perubahan dari tim. Tinjau data terbaru sebelum mengirim ulang."
        "CLOSING_STATE_CONFLICT" -> "Closing sudah diajukan atau dikunci. Draft perangkat tetap disimpan untuk diperiksa."
        else -> "Perubahan belum dapat dikirim. Periksa jumlah, akses akun, dan status closing."
    }
}
