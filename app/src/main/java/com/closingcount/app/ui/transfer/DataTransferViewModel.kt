package com.closingcount.app.ui.transfer

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import com.closingcount.app.ClosingCountApplication
import com.closingcount.app.data.transfer.BackupJsonCodec

class DataTransferViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as ClosingCountApplication
    private val dao = app.database.backupDao()

    suspend fun writeBackup(uri: Uri): String? = runCatching {
        val snapshot = dao.createSnapshot()
        val bytes = BackupJsonCodec.encode(snapshot, System.currentTimeMillis())
        app.contentResolver.openOutputStream(uri, "wt")?.use { it.write(bytes) }
            ?: error("File tujuan tidak dapat dibuka.")
    }.exceptionOrNull()?.let { "Backup gagal dibuat. Coba pilih lokasi lain." }

    suspend fun restoreBackup(uri: Uri): String? = runCatching {
        val bytes = app.contentResolver.openInputStream(uri)?.use { input ->
            input.readBytes(MAX_BACKUP_BYTES + 1)
        } ?: error("File tidak dapat dibuka.")
        require(bytes.size <= MAX_BACKUP_BYTES) { "File backup terlalu besar." }
        val snapshot = BackupJsonCodec.decode(bytes)
        dao.restore(snapshot)
    }.exceptionOrNull()?.let { error ->
        error.message?.takeIf { it.isNotBlank() } ?: "Restore gagal. File backup tidak valid."
    }

    private fun java.io.InputStream.readBytes(limit: Int): ByteArray {
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        val output = java.io.ByteArrayOutputStream()
        while (output.size() <= limit) {
            val read = read(buffer)
            if (read < 0) break
            output.write(buffer, 0, read)
        }
        return output.toByteArray()
    }

    companion object {
        private const val MAX_BACKUP_BYTES = 20 * 1024 * 1024
    }
}
