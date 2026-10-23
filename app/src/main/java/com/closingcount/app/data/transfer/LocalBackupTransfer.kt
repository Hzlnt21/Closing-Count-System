package com.closingcount.app.data.transfer

import android.content.ContentResolver
import android.net.Uri
import android.provider.OpenableColumns
import com.closingcount.app.data.local.BackupDao
import com.closingcount.app.data.local.BackupSnapshot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.ByteArrayOutputStream
import java.security.MessageDigest
import java.util.UUID

data class LocalRestoreReview(
    val id: String,
    val filename: String,
    val sha256: String,
    val menus: Int,
    val ingredients: Int,
    val closings: Int,
    val firstDate: String?,
    val lastDate: String?,
)

/** Local version1 transfer only; never reads, replaces or queues online state. */
class LocalBackupTransfer(private val resolver: ContentResolver, private val dao: BackupDao) {
    private data class Pending(val review: LocalRestoreReview, val snapshot: BackupSnapshot)
    private val mutex = Mutex()
    private var pending: Pending? = null

    suspend fun inspect(uri: Uri): LocalRestoreReview = mutex.withLock {
        pending = null
        withContext(Dispatchers.IO) {
            val bytes = resolver.openInputStream(uri)?.use { input ->
                val limit = BackupJsonCodec.MAX_BACKUP_BYTES + 1
                val output = ByteArrayOutputStream()
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                while (output.size() < limit) {
                    val count = input.read(buffer, 0, minOf(buffer.size, limit - output.size()))
                    if (count < 0) break
                    if (count == 0) throw java.io.IOException("File tidak dapat dibaca.")
                    output.write(buffer, 0, count)
                }
                output.toByteArray()
            } ?: error("File tidak dapat dibuka.")
            val snapshot = BackupJsonCodec.decode(bytes)
            val filename = runCatching {
                resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
                    if (it.moveToFirst()) it.getString(0) else null
                }
            }.getOrNull()?.filterNot { it.isISOControl() }?.take(120)?.takeIf { it.isNotBlank() } ?: "Backup lokal"
            val dates = snapshot.closings.map { it.date }
            val review = LocalRestoreReview(UUID.randomUUID().toString(), filename,
                MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) },
                snapshot.menus.size, snapshot.ingredients.size, snapshot.closings.size, dates.minOrNull(), dates.maxOrNull())
            pending = Pending(review, snapshot)
            review
        }
    }

    suspend fun discard(id: String) = mutex.withLock {
        if (pending?.review?.id == id) pending = null
    }

    suspend fun restore(id: String) = mutex.withLock {
        val reviewed = requireNotNull(pending?.takeIf { it.review.id == id }) { "Pemeriksaan backup sudah ditutup. Pilih file kembali." }
        // Consume before replacement: retries, stale dialogs and a failed transaction require a new review.
        pending = null
        withContext(Dispatchers.IO) { dao.restore(reviewed.snapshot) }
    }

    suspend fun write(uri: Uri) = mutex.withLock {
        withContext(Dispatchers.IO) {
            val bytes = BackupJsonCodec.encode(dao.createSnapshot(), System.currentTimeMillis())
            require(bytes.size <= BackupJsonCodec.MAX_BACKUP_BYTES) { "Backup melampaui batas 20 MiB." }
            resolver.openOutputStream(uri, "wt")?.use { it.write(bytes) } ?: error("File tujuan tidak dapat dibuka.")
        }
    }
}
