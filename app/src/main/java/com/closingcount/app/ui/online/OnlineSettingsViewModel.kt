package com.closingcount.app.ui.online

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.closingcount.app.ClosingCountApplication
import com.closingcount.app.data.online.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.IOException

data class SettingsReview(val name: String, val timezone: String, val cutoff: Int, val revision: Long)
data class OnlineSettingsUi(val outletId: String = "", val snapshot: OnlineOutletSettings? = null,
    val pending: OnlineSettingsOperation? = null, val name: String = "", val timezone: String = "Asia/Jakarta",
    val cutoff: String = "00:00", val busy: Boolean = false, val error: String? = null, val notice: String? = null,
    val authRequired: Boolean = false) {
    val cutoffMinutes get() = cutoff.takeIf { it.matches(Regex("(?:[01][0-9]|2[0-3]):[0-5][0-9]")) }?.let {
        it.substringBefore(':').toInt()*60 + it.substringAfter(':').toInt() }
    val valid get() = name.trim().length in 1..120 && cutoffMinutes != null && runCatching { java.time.ZoneId.of(timezone) }.isSuccess
    val editable get() = snapshot?.role == "ADMIN" && pending == null && !busy
    val dirty get() = pending == null && snapshot?.let { name != it.name || timezone != it.timezone || cutoffMinutes != it.cutoffMinutes } == true
}

class OnlineSettingsViewModel(application: Application): AndroidViewModel(application) {
    private val repo = (application as ClosingCountApplication).onlineRepository
    private val mutable = MutableStateFlow(OnlineSettingsUi())
    val state = mutable.asStateFlow()
    private var job: Job? = null
    private var epoch = 0
    private var ownerScope: String? = null
    private fun time(minutes: Int) = "%02d:%02d".format(minutes/60,minutes%60)
    fun open(outletId: String, discard: Boolean = false) {
        val scope=repo.session()?.scope(outletId) ?: return
        val retained=state.value.takeIf { !discard && ownerScope==scope && it.outletId==outletId && it.dirty && it.pending==null }
        ownerScope=scope
        job?.cancel(); val run = ++epoch
        mutable.value = retained?.copy(busy=true,error=null,notice=null) ?: OnlineSettingsUi(outletId=outletId,busy=true)
        job = viewModelScope.launch {
            try {
                val pending = repo.pendingSettings(outletId)
                if (run == epoch) mutable.update { it.copy(pending=pending,name=pending?.name ?: it.name,
                    timezone=pending?.timezone ?: it.timezone,cutoff=pending?.let {op->time(op.cutoffMinutes)} ?: it.cutoff) }
                val snapshot = repo.outletSettings(outletId)
                if (run == epoch) mutable.update {
                    if(retained!=null && pending==null) it.copy(snapshot=retained.snapshot?.copy(role=snapshot.role),
                        notice="Isian belum disimpan dipulihkan. Periksa kembali sebelum menerapkan.",
                        error=if(retained.snapshot?.revision!=snapshot.revision) "Pengaturan server sudah berubah. Muat versi terbaru sebelum membuat tindakan baru." else null)
                    else it.copy(snapshot=snapshot,name=pending?.name ?: snapshot.name,
                        timezone=pending?.timezone ?: snapshot.timezone,cutoff=time(pending?.cutoffMinutes ?: snapshot.cutoffMinutes))
                }
            } catch (cancel: CancellationException) { throw cancel
            } catch (e: Exception) { if(run==epoch) fail(e)
            } finally { if(run==epoch) mutable.update { it.copy(busy=false) } }
        }
    }
    fun cancel() { epoch++;job?.cancel() }
    fun forgetForm() { cancel();ownerScope=null;mutable.value=OnlineSettingsUi() }
    fun name(value: String) { if(state.value.editable) mutable.update { it.copy(name=value,error=null,notice=null) } }
    fun timezone(value: String) { if(state.value.editable) mutable.update { it.copy(timezone=value,error=null,notice=null) } }
    fun cutoff(value: String) { if(state.value.editable) mutable.update { it.copy(cutoff=value,error=null,notice=null) } }
    fun save(review: SettingsReview) {
        val s=state.value
        if(!s.editable || !s.dirty || !s.valid || s.snapshot?.revision != review.revision) return
        if(review.name != s.name.trim() || review.timezone != s.timezone || review.cutoff != s.cutoffMinutes) return
        execute { repo.prepareSettings(s.outletId,review.name,review.timezone,review.cutoff,review.revision);repo.syncSettings(s.outletId) }
    }
    fun retry() { val s=state.value;if(s.busy || s.pending?.status !in setOf("QUEUED","SENT"))return
        execute { repo.syncSettings(s.outletId) }
    }
    fun dismiss() { val s=state.value;if(s.busy || s.pending?.status !in setOf("CONFLICT","FAILED"))return
        execute { repo.dismissSettings(s.outletId);repo.outletSettings(s.outletId) }
    }
    private fun execute(action: suspend () -> OnlineOutletSettings) {
        val s=state.value;val run=epoch
        mutable.update { it.copy(busy=true,error=null,notice=null) }
        job = viewModelScope.launch {
            try { val fresh=action()
                if(run==epoch)mutable.update { it.copy(snapshot=fresh,name=fresh.name,timezone=fresh.timezone,cutoff=time(fresh.cutoffMinutes),
                    notice="Pengaturan terkonfirmasi. Menampilkan versi terbaru dari server.",authRequired=false) }
            } catch(cancel:CancellationException){throw cancel
            } catch(e:Exception){if(run==epoch)fail(e)
            } finally { if(run==epoch){
                try {val pending=repo.pendingSettings(s.outletId);if(run==epoch)mutable.update {it.copy(pending=pending)} }
                catch(e:Exception){if(run==epoch)fail(e)}
                mutable.update {it.copy(busy=false)}
            } }
        }
    }
    private fun fail(e: Exception) {
        val code=(e as? OnlineApiException)?.code
        val auth=code in setOf("SESSION_REQUIRED","ACCOUNT_DISABLED","OUTLET_FORBIDDEN")
        val message=when {
            auth -> "Sesi atau izin perlu diperiksa. Masuk kembali dengan akun yang sama; tindakan tersimpan tetap dipertahankan."
            code=="SETTINGS_CONFLICT" || e is OnlineConflictChangedException -> "Pengaturan sudah berubah dari admin lain. Periksa versi terbaru sebelum membuat perubahan baru."
            e is IOException -> "Konfirmasi belum diperoleh. Tindakan yang sudah tersimpan tetap aman; periksa kembali saat koneksi tersedia."
            else -> "Pengaturan belum dapat disimpan. Periksa isian dan coba lagi."
        }
        mutable.update {it.copy(error=message,authRequired=auth)}
    }
}
