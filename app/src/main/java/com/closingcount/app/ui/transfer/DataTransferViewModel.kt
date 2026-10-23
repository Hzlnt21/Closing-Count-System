package com.closingcount.app.ui.transfer

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.closingcount.app.ClosingCountApplication
import com.closingcount.app.data.transfer.LocalBackupTransfer
import com.closingcount.app.data.transfer.LocalRestoreReview
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class LocalTransferUiState(
    val busy: Boolean = false,
    val activity: String = "",
    val review: LocalRestoreReview? = null,
    val message: String? = null,
    val failed: Boolean = false,
)

class DataTransferViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as ClosingCountApplication
    private val transfer = LocalBackupTransfer(app.contentResolver, app.database.backupDao())
    private val mutable = MutableStateFlow(LocalTransferUiState())
    val state = mutable.asStateFlow()

    fun inspect(uri: Uri) {
        if (mutable.value.busy) return
        mutable.update { it.copy(review = null) }
        task("Memeriksa backup lokal") {
            val review = transfer.inspect(uri)
            mutable.update { it.copy(review = review) }
        }
    }

    fun discard() {
        if (mutable.value.busy) return
        val id = mutable.value.review?.id ?: return
        mutable.update { it.copy(review = null) }
        viewModelScope.launch { transfer.discard(id) }
    }

    fun restore(id: String) {
        if (mutable.value.review?.id != id) return
        task("Memulihkan data lokal") {
            transfer.restore(id)
            mutable.update { it.copy(review = null, message = "Data lokal berhasil dipulihkan. Closing bersama tetap terpisah.") }
        }
    }

    fun writeBackup(uri: Uri) {
        if (mutable.value.review != null) return
        task("Menulis backup lokal") {
            transfer.write(uri)
            mutable.update { it.copy(message = "Backup lokal berhasil disimpan.") }
        }
    }

    fun clearMessage() { mutable.update { it.copy(message = null) } }

    private fun task(label: String, action: suspend () -> Unit) {
        if (mutable.value.busy) return
        mutable.update { it.copy(busy = true, activity = label, message = null, failed = false) }
        viewModelScope.launch {
            try { action() }
            catch (cancelled: CancellationException) {
                mutable.update { it.copy(review = null) }
                throw cancelled
            } catch (error: Exception) {
                val message = when (error) {
                    is SecurityException -> "File tidak dapat diakses. Pilih file kembali dan izinkan akses."
                    is IllegalArgumentException -> error.message ?: "File backup tidak valid."
                    is org.json.JSONException -> "File backup tidak lengkap atau formatnya tidak sesuai."
                    is java.io.IOException -> "File tidak dapat dibaca atau ditulis. Coba lokasi lain."
                    else -> "Transfer belum berhasil. Periksa file lalu coba lagi."
                }
                mutable.update { it.copy(review = null, message = message, failed = true) }
            } finally { mutable.update { it.copy(busy = false, activity = "") } }
        }
    }
}
