package com.closingcount.app.data.transfer

import android.content.Context
import android.util.AtomicFile
import java.io.File
import java.io.InputStream

data class PendingRecoverySummary(val sha256:String,val bytes:Int,val backupId:String,val createdAt:String,
    val origin:String,val accountId:String,val accountLabel:String,val outletId:String,val outletLabel:String,
    val sourceDeviceId:String,val closings:Int,val entries:Int,val adminKinds:List<String>,val firstDate:String?,val lastDate:String?)
data class PendingRecoveryAccepted(val summary:PendingRecoverySummary,val duplicate:Boolean)

/** Quarantined original bytes only. No repository, session, Room or transport dependency. */
class OnlinePendingRecoveryInbox(context:Context) {
    private val folder=File(context.noBackupFilesDir,"online-recovery-inbox")

    fun list():List<PendingRecoverySummary> = synchronized(lock) {
        names().map{summary(readLocked(it))}.sortedWith(compareByDescending<PendingRecoverySummary>{it.createdAt}.thenBy{it.sha256})
    }
    fun read(fingerprint:String):DecodedPendingBackup = synchronized(lock) {readLocked(key(fingerprint))}
    fun accept(bytes:ByteArray):PendingRecoveryAccepted = synchronized(lock) {
        require(bytes.size<=OnlinePendingBackupCodec.MAX_BYTES)
        val frozen=bytes.copyOf()
        val decoded=OnlinePendingBackupCodec.decode(frozen)
        val hash=key(decoded.sha256)
        if(hash in names())return@synchronized PendingRecoveryAccepted(summary(readLocked(hash)),true)
        check(folder.isDirectory||folder.mkdirs()) { "Inbox pemulihan tidak dapat dibuka." }
        // Explicit capacity error, never automatic deletion of another accepted recovery.
        val files=folder.listFiles()?:error("Inbox pemulihan tidak dapat dibaca.")
        val stored=files.filter{filePattern.matches(it.name)}
        check(stored.map{it.name.substringBefore('.')}.distinct().size<MAX_FILES &&
            stored.sumOf{it.length()}+frozen.size<=MAX_TOTAL_BYTES) { "Inbox pemulihan penuh." }
        val target=AtomicFile(File(folder,"$hash.json"));val output=target.startWrite()
        try {output.write(frozen);target.finishWrite(output)}catch(error:Exception){target.failWrite(output);throw error}
        val saved=readLocked(hash)
        check(saved.sha256==decoded.sha256)
        PendingRecoveryAccepted(summary(saved),false)
    }

    private fun names():List<String> {
        if(!folder.exists())return emptyList()
        check(folder.isDirectory)
        return requireNotNull(folder.listFiles()).filter{basePattern.matches(it.name)||backupPattern.matches(it.name)}
            .map{it.name.substringBefore('.')}.distinct()
    }
    private fun readLocked(hash:String):DecodedPendingBackup {
        val decoded=AtomicFile(File(folder,"${key(hash)}.json")).openRead().use{OnlinePendingBackupCodec.decode(readBounded(it))}
        check(decoded.sha256==hash) { "Fingerprint cadangan pemulihan tidak cocok." }
        return decoded
    }
    companion object {
        const val MAX_FILES=10
        const val MAX_TOTAL_BYTES=100L*1024*1024
        private val lock=Any()
        private val basePattern=Regex("[0-9a-f]{64}\\.json")
        private val backupPattern=Regex("[0-9a-f]{64}\\.json\\.bak")
        private val filePattern=Regex("[0-9a-f]{64}\\.json(?:\\.bak|\\.new)?")
        private fun key(value:String):String {require(Regex("[0-9a-f]{64}").matches(value));return value}
        internal fun readBounded(input:InputStream):ByteArray {
            val output=java.io.ByteArrayOutputStream();val buffer=ByteArray(8192)
            while(true){val size=input.read(buffer);if(size<0)break;check(size>0)
                check(output.size().toLong()+size<=OnlinePendingBackupCodec.MAX_BYTES) { "Ukuran cadangan melebihi batas." }
                output.write(buffer,0,size)
            }
            return output.toByteArray()
        }
        internal fun summary(decoded:DecodedPendingBackup):PendingRecoverySummary {
            val m=decoded.model;val dates=m.closings.map{it.date}
            return PendingRecoverySummary(decoded.sha256,decoded.bytes,m.backupId,m.createdAt,m.origin,m.account.id,m.account.name,
                m.outlet.id,m.outlet.name,m.sourceDeviceId,m.closings.size,m.closings.sumOf{it.entries.size},m.admin.map{it.kind},dates.minOrNull(),dates.maxOrNull())
        }
    }
}
