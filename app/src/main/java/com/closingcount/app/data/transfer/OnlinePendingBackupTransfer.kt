package com.closingcount.app.data.transfer

import android.content.ContentResolver
import android.net.Uri
import com.closingcount.app.data.online.OnlineRepository
import com.closingcount.app.data.online.OnlineSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.security.MessageDigest
import java.util.UUID

data class PendingBackupExportReview(val id:String,val filename:String,val sha256:String,val bytes:Int,
    val accountId:String,val accountLabel:String,val outletId:String,val outletLabel:String,val origin:String,
    val sourceDeviceId:String,val createdAt:String,val closings:Int,val entries:Int,val adminKinds:List<String>,val firstDate:String?,val lastDate:String?)

/** Holds a frozen local snapshot through a future system save dialog; never sends/replays it. */
class OnlinePendingBackupTransfer(private val resolver:ContentResolver,private val repository:OnlineRepository,
    private val device:OnlineBackupDeviceIdentity) {
    private data class Ticket(val review:PendingBackupExportReview,val bytes:ByteArray,val session:OnlineSession)
    private val mutex=Mutex()
    private var pending:Ticket?=null
    suspend fun prepare(outletId:String):PendingBackupExportReview=mutex.withLock {
        pending=null
        val session=requireNotNull(repository.session())
        withContext(Dispatchers.IO) {
            val model=repository.capturePendingBackup(session,outletId,device.get())
            val bytes=OnlinePendingBackupCodec.encode(model)
            val sha=MessageDigest.getInstance("SHA-256").digest(bytes).joinToString(""){"%02x".format(it)}
            val dates=model.closings.map{it.date}
            val review=PendingBackupExportReview(UUID.randomUUID().toString(),"ClosingCount-draft-${model.createdAt.take(10)}-${outletId.take(8)}.json",sha,bytes.size,
                model.account.id,model.account.name,model.outlet.id,model.outlet.name,model.origin,model.sourceDeviceId,model.createdAt,
                model.closings.size,model.closings.sumOf{it.entries.size},model.admin.map{it.kind},dates.minOrNull(),dates.maxOrNull())
            check(repository.session()===session) { "Backup session changed" }
            pending=Ticket(review,bytes,session);review
        }
    }
    suspend fun discard(id:String)=mutex.withLock {if(pending?.review?.id==id)pending=null}
    suspend fun write(id:String,uri:Uri):PendingBackupExportReview=mutex.withLock {
        val ticket=requireNotNull(pending).also{check(it.review.id==id)}
        pending=null // failed/duplicate callbacks cannot replay this write
        repository.withPendingBackupSession(ticket.session) {
            val output=requireNotNull(resolver.openOutputStream(uri,"wt")) { "File cadangan tidak dapat dibuka." }
            output.use{it.write(ticket.bytes);it.flush()}
        }
        ticket.review
    }
}
