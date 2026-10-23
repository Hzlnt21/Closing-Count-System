package com.closingcount.app.data.transfer

import android.util.AtomicFile
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File

/** Reads committed non-secret intent files without the legacy reader's JSON coercion. */
internal object OnlinePendingIntentBackupReader {
    fun read(file: AtomicFile, scope: String, group: String): PendingBackupAdmin? {
        if(!file.baseFile.exists() && !File(file.baseFile.path+".bak").exists())return null
        val bytes=file.openRead().use { input ->
            val out=ByteArrayOutputStream();val buffer=ByteArray(8192);val max=2*1024*1024
            while(out.size()<=max) { val n=input.read(buffer,0,minOf(buffer.size,max+1-out.size()))
                if(n<0)break;check(n>0);out.write(buffer,0,n) }
            check(out.size()<=max);out.toByteArray()
        }
        val raw=OnlinePendingBackupCodec.readObject(bytes)
        fun text(key:String)=raw.get(key).let { require(it is String);it }
        fun integer(row:JSONObject,key:String)=row.get(key).let{require(it is Int||it is Long);(it as Number).toLong()}
        val common=setOf("formatVersion","scope","operationId","status","wasSent","error")
        val extra=when(group){"SETTINGS"->setOf("expectedRevision","name","timezone","cutoffMinutes");"CATALOG"->setOf("expectedRevision","catalogJson");"TEAM"->setOf("kind","payload");else->error("Unknown intent group")}
        require(raw.keys().asSequence().toSet()==common+extra)
        require(integer(raw,"formatVersion")==1L && text("scope")==scope)
        require(raw.get("wasSent") is Boolean)
        require(raw.isNull("error") || raw.get("error") is String)
        var kind=group
        var base:Long?=null
        val payload=when(group) {
            "SETTINGS" -> { base=integer(raw,"expectedRevision")
                JSONObject().put("name",raw.get("name")).put("timezone",raw.get("timezone")).put("cutoffMinutes",raw.get("cutoffMinutes")) }
            "CATALOG" -> { base=integer(raw,"expectedRevision");OnlinePendingBackupCodec.strictObject(text("catalogJson")) }
            else -> {
                val p=OnlinePendingBackupCodec.strictObject(text("payload"))
                kind=when(text("kind")){"CREATE_ACCOUNT"->"TEAM_CREATE";"UPDATE_MEMBERSHIP"->"TEAM_UPDATE";else->error("Invalid team intent")}
                if(kind=="TEAM_UPDATE") {base=integer(p,"expectedRevision");p.remove("expectedRevision")}
                p
            }
        }
        return OnlinePendingBackupCodec.decodeAdmin(JSONObject().put("kind",kind).put("operationId",raw.get("operationId"))
            .put("expectedRevision",base?:JSONObject.NULL).put("status",raw.get("status")).put("wasSent",raw.get("wasSent")).put("payload",payload))
    }
}
