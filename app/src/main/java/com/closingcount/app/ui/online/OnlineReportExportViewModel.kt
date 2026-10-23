package com.closingcount.app.ui.online

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.closingcount.app.ClosingCountApplication
import com.closingcount.app.data.online.*
import com.closingcount.app.data.transfer.OnlineReportExcelExporter
import com.closingcount.app.data.transfer.ReportExportDocument
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.time.Instant
import java.util.UUID

data class ReportExportRequest(val id: String, val filename: String)
data class ReportExportUi(val preparing: Boolean = false, val writing: Boolean = false, val request: ReportExportRequest? = null,
    val reportId: String? = null, val message: String? = null, val failed: Boolean = false, val needsLogin: Boolean = false) {
    val busy get() = preparing || writing || request != null
}

class OnlineReportExportViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as ClosingCountApplication
    private val repo = app.onlineRepository
    private data class Ticket(val request: ReportExportRequest, val session: OnlineSession, val report: FrozenClosingReport, var launched: Boolean = false)
    private var ticket: Ticket? = null
    private var prepareJob: Job? = null
    private var epoch = 0
    private val mutable = MutableStateFlow(ReportExportUi())
    val state = mutable.asStateFlow()

    fun begin(raw: String, reportId: String) {
        if (state.value.busy) return
        val run = ++epoch
        mutable.value = ReportExportUi(preparing = true, reportId = reportId)
        prepareJob = viewModelScope.launch {
            try {
                val session = requireNotNull(repo.session())
                val report = withContext(Dispatchers.IO) { FrozenClosingReport.parse(raw) }
                if (run != epoch) return@launch
                check(report.id == reportId)
                check(repo.session() === session);session.scope(report.outletId)
                val request = ReportExportRequest(UUID.randomUUID().toString(), "ClosingCount_${report.date}_G${report.generation}_${report.id.take(8)}.xlsx")
                ticket = Ticket(request, session, report)
                mutable.value = ReportExportUi(request = request, reportId = report.id)
            } catch (cancel: CancellationException) { if(run == epoch)mutable.update { it.copy(preparing = false) };throw cancel
            } catch (error: Exception) { fail(error, run) }
        }
    }

    fun detached(reportId: String) {
        if (state.value.reportId != reportId || state.value.writing || ticket?.launched == true) return
        epoch++;prepareJob?.cancel();ticket = null;mutable.value = ReportExportUi()
    }

    /** Claim is retained by the ViewModel across Activity recreation; the picker launches once. */
    fun claim(id: String): Boolean {
        val value = ticket ?: return false
        if (value.request.id != id || value.launched) return false
        value.launched = true;return true
    }
    fun pickerResult(uri: Uri?) {
        val value = ticket ?: return
        if (!value.launched || mutable.value.writing || state.value.request?.id != value.request.id) return
        ticket = null
        if (uri == null) { mutable.value = ReportExportUi(reportId = value.report.id);return }
        mutable.update { it.copy(request = null, writing = true, message = null) }
        val run = epoch
        viewModelScope.launch {
            try {
                repo.withReportForExport(value.session, value.report) { report, outletName, actor ->
                    withContext(Dispatchers.IO) {
                        val bytes = OnlineReportExcelExporter.create(ReportExportDocument(report, outletName, actor, Instant.now()))
                        app.contentResolver.openOutputStream(uri, "wt")?.use { it.write(bytes) } ?: error("Output provider unavailable")
                    }
                }
                mutable.update { it.copy(writing = false, message = "Laporan generasi ${value.report.generation} berhasil disimpan.") }
            } catch (cancel: CancellationException) { mutable.update { it.copy(writing = false) };throw cancel
            } catch (error: Exception) { fail(error, run) }
        }
    }
    private fun fail(error: Exception, run: Int) {
        if(run != epoch)return
        ticket = null
        val login = error is OnlineApiException && error.status in listOf(401,403)
        val message = when {
            login -> "Akses laporan perlu diperiksa. Masuk kembali sebelum mengunduh."
            error is OnlineApiException -> "Laporan belum dapat diambil dari server. Coba lagi."
            error is IllegalStateException -> "Sesi atau laporan berubah. Buka laporan dan coba lagi."
            error is IllegalArgumentException -> "Isi laporan belum dapat diekspor. Periksa data atau hubungi administrator."
            else -> "File belum berhasil disimpan. Periksa koneksi dan lokasi tujuan."
        }
        mutable.update { ReportExportUi(reportId = it.reportId, message = message, failed = true, needsLogin = login) }
    }
}
