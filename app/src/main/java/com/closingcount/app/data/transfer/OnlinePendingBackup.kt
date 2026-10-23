package com.closingcount.app.data.transfer

/** Transport models only: decoding these values never creates a session or a runnable queue. */
data class PendingBackupAccount(val id: String, val username: String, val name: String)
data class PendingBackupOutlet(val id: String, val name: String)
data class PendingBackupEntry(val menuId: String, val name: String, val baseQuantity: Int,
    val baseRevision: Long, val localQuantity: Int, val status: String)
data class PendingBackupQuantity(val operationId: String, val menuId: String, val generation: Long,
    val expectedRevision: Long?, val quantity: Int, val status: String)
data class PendingBackupLifecycle(val operationId: String, val action: String, val generation: Long,
    val expectedRevision: Long, val reason: String, val status: String, val wasSent: Boolean)
data class PendingBackupClosing(val id: String, val date: String, val generation: Long, val revision: Long,
    val state: String, val entries: List<PendingBackupEntry>, val operations: List<PendingBackupQuantity>,
    val lifecycle: PendingBackupLifecycle?)
data class PendingBackupAdmin(val kind: String, val operationId: String, val expectedRevision: Long?,
    val status: String, val wasSent: Boolean, val payloadJson: String)
data class OnlinePendingBackup(val backupId: String, val createdAt: String, val sourceDeviceId: String,
    val origin: String, val account: PendingBackupAccount, val outlet: PendingBackupOutlet,
    val closings: List<PendingBackupClosing>, val admin: List<PendingBackupAdmin>)
data class DecodedPendingBackup(val model: OnlinePendingBackup, val sha256: String, val bytes: Int)
