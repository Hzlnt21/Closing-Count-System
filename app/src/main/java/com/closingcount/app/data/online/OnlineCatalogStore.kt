package com.closingcount.app.data.online

import android.content.Context
import android.util.AtomicFile
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.security.MessageDigest

data class OnlineCatalogOperation(val scope:String,val operationId:String,val expectedRevision:Long,
    val catalogJson:String,val status:String="QUEUED",val wasSent:Boolean=false,val error:String?=null)

/** Non-secret catalog intent only. Atomic and excluded from backup/transfer, just like online queues. */
class OnlineCatalogStore(context: Context) {
    private val directory = File(context.noBackupFilesDir,"online-catalog-operations").apply { check(mkdirs() || isDirectory) }
    private fun file(scope: String): AtomicFile {
        val hash = MessageDigest.getInstance("SHA-256").digest(scope.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
        return AtomicFile(File(directory,"$hash.json"))
    }
    private fun decode(value: JSONObject): OnlineCatalogOperation {
        require(value.getInt("formatVersion") == 1)
        val op=OnlineCatalogOperation(value.getString("scope"),OnlineApi.uuid(value.getString("operationId")),value.getLong("expectedRevision"),
            value.getString("catalogJson"),value.getString("status"),value.getBoolean("wasSent"),if(value.isNull("error"))null else value.getString("error"))
        require(op.expectedRevision in 0..9007199254740990L)
        OnlineCatalog.parse(JSONObject(op.catalogJson))
        require(OnlineCatalogStore.requestBody(op).toString().toByteArray(Charsets.UTF_8).size<=524288){"Katalog terlalu besar untuk disimpan ke server."}
        require(op.status in setOf("QUEUED","SENT","AUTH_REQUIRED","CONFLICT","FAILED"))
        require(!op.wasSent || op.status != "QUEUED")
        require(op.status != "SENT" || op.wasSent)
        return op
    }
    @Synchronized fun load(scope: String): OnlineCatalogOperation? {
        val file = file(scope)
        if (!file.baseFile.exists() && !File(file.baseFile.path+".bak").exists()) return null
        return try { file.openRead().use { decode(JSONObject(it.readBytes().toString(Charsets.UTF_8))) }.also { require(it.scope == scope) } }
        catch (_: Exception) { throw IOException("Stored catalog operation needs review") }
    }
    @Synchronized fun save(op: OnlineCatalogOperation) {
        val value = JSONObject().put("formatVersion",1).put("scope",op.scope).put("operationId",op.operationId)
            .put("expectedRevision",op.expectedRevision).put("catalogJson",op.catalogJson)
            .put("status",op.status).put("wasSent",op.wasSent).put("error",op.error ?: JSONObject.NULL)
        decode(value)
        val previous = load(op.scope)
        if (previous != null) {
            check(previous.operationId == op.operationId)
            check(previous.copy(status=op.status,wasSent=op.wasSent,error=op.error) == op) { "Catalog payload is immutable" }
            check(!previous.wasSent || op.wasSent)
        }
        val target = file(op.scope); val stream = target.startWrite()
        try { stream.write(value.toString().toByteArray(Charsets.UTF_8));target.finishWrite(stream) }
        catch (e: Exception) { target.failWrite(stream);throw e }
        if (load(op.scope) != op) throw IOException("Catalog intent was not committed")
    }
    @Synchronized fun remove(scope: String, operationId: String, confirmed: Boolean = false) {
        val op = load(scope) ?: return
        check(op.operationId == operationId)
        check(confirmed || op.status in setOf("CONFLICT","FAILED")) { "Uncertain catalog action must not be discarded" }
        file(scope).delete()
        if (load(scope) != null) throw IOException("Acknowledged catalog intent could not be removed")
    }
    @Synchronized fun pendingForAccount(prefix: String): Int = directory.listFiles()?.filter { it.name.endsWith(".json") || it.name.endsWith(".json.bak") }
        ?.map { if (it.name.endsWith(".bak")) File(it.path.removeSuffix(".bak")) else it }?.distinctBy { it.path }?.count { base ->
        val op = try { AtomicFile(base).openRead().use { decode(JSONObject(it.readBytes().toString(Charsets.UTF_8))) } }
        catch (_: Exception) { throw IOException("Stored catalog operation needs review") }
        op.scope.startsWith(prefix)
    } ?: 0
    companion object {
        fun requestBody(op:OnlineCatalogOperation)=JSONObject().put("operationId",op.operationId)
            .put("expectedRevision",op.expectedRevision).put("catalog",OnlineCatalog.parse(JSONObject(op.catalogJson)).json())
    }
}
