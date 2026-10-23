package com.closingcount.app.data.transfer

import android.content.ContentResolver
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.UUID

data class PendingRecoveryReview(val id:String,val summary:PendingRecoverySummary)

/** Future picker controller: preview freezes bytes; explicit accept only stores an inert inbox file. */
class OnlinePendingRecoveryTransfer(private val resolver:ContentResolver,private val inbox:OnlinePendingRecoveryInbox) {
    private data class Ticket(val review:PendingRecoveryReview,val bytes:ByteArray)
    private val mutex=Mutex()
    private var pending:Ticket?=null
    suspend fun prepare(uri:Uri):PendingRecoveryReview=mutex.withLock {
        pending=null
        withContext(Dispatchers.IO) {
            val bytes=requireNotNull(resolver.openInputStream(uri)).use{OnlinePendingRecoveryInbox.readBounded(it)}
            val decoded=OnlinePendingBackupCodec.decode(bytes)
            val review=PendingRecoveryReview(UUID.randomUUID().toString(),OnlinePendingRecoveryInbox.summary(decoded))
            pending=Ticket(review,bytes);review
        }
    }
    suspend fun discard(id:String)=mutex.withLock {if(pending?.review?.id==id)pending=null}
    suspend fun accept(id:String):PendingRecoveryAccepted=mutex.withLock {
        val ticket=requireNotNull(pending).also{check(it.review.id==id)}
        pending=null
        withContext(Dispatchers.IO){inbox.accept(ticket.bytes)}
    }
}
