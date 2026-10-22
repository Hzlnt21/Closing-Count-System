package com.closingcount.app.data.online

import android.content.Context
import android.util.AtomicFile
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.security.MessageDigest

data class OnlineTeamMember(val id:String,val username:String,val name:String,val role:String,val active:Boolean,val accountActive:Boolean,val revision:Long)
data class OnlineTeamPage(val items:List<OnlineTeamMember>,val nextAfter:String?)
data class OnlineTeamReceipt(val member:OnlineTeamMember,val changed:Boolean)
data class OnlineTeamOperation(val scope:String,val operationId:String,val kind:String,val payload:String,
    val status:String="QUEUED",val wasSent:Boolean=false,val error:String?=null) {
    fun ownAccessLoss(accountId:String):Boolean {
        val p=JSONObject(payload)
        return kind=="UPDATE_MEMBERSHIP" && p.getString("accountId")==accountId && (!p.getBoolean("active")||p.getString("role")!="ADMIN")
    }
}
class TeamPasswordRequiredException:IOException("Reenter the original password to continue the pending account creation")

/** No password, password digest or session token is serialized here. */
class OnlineTeamStore(context:Context){
    private val directory=File(context.noBackupFilesDir,"online-team-operations").apply{check(mkdirs()||isDirectory)}
    private fun file(scope:String)=AtomicFile(File(directory,MessageDigest.getInstance("SHA-256").digest(scope.toByteArray()).joinToString(""){"%02x".format(it)}+".json"))
    private fun decode(json:JSONObject):OnlineTeamOperation{
        require(json.getInt("formatVersion")==1)
        val op=OnlineTeamOperation(json.getString("scope"),OnlineApi.uuid(json.getString("operationId")),json.getString("kind"),json.getString("payload"),json.getString("status"),json.getBoolean("wasSent"),if(json.isNull("error"))null else json.getString("error"))
        val p=JSONObject(op.payload);val keys=p.keys().asSequence().toSet()
        require(p.getString("role") in setOf("ADMIN","SUPERVISOR","EMPLOYEE"))
        if(op.kind=="CREATE_ACCOUNT"){
            require(keys==setOf("username","displayName","role"));require(p.getString("username").matches(Regex("[a-z0-9][a-z0-9._-]{2,63}")))
            require(p.getString("displayName").trim().length in 1..120)
        }else{
            require(op.kind=="UPDATE_MEMBERSHIP"&&keys==setOf("accountId","expectedRevision","role","active"))
            OnlineApi.uuid(p.getString("accountId"));require(p.getLong("expectedRevision") in 0..9007199254740991L);require(p.get("active") is Boolean)
        }
        require(op.status in setOf("QUEUED","SENT","AUTH_REQUIRED","CONFLICT","FAILED"))
        require((!op.wasSent||op.status!="QUEUED")&&(op.status!="SENT"||op.wasSent));return op
    }
    @Synchronized fun load(scope:String):OnlineTeamOperation?{
        val f=file(scope);if(!f.baseFile.exists()&&!File(f.baseFile.path+".bak").exists())return null
        return try{f.openRead().use{decode(JSONObject(it.readBytes().toString(Charsets.UTF_8)))}.also{require(it.scope==scope)}}catch(_:Exception){throw IOException("Pending team operation requires review")}
    }
    @Synchronized fun save(op:OnlineTeamOperation){
        val json=JSONObject().put("formatVersion",1).put("scope",op.scope).put("operationId",op.operationId).put("kind",op.kind).put("payload",op.payload)
            .put("status",op.status).put("wasSent",op.wasSent).put("error",op.error?:JSONObject.NULL)
        decode(json);val previous=load(op.scope)
        if(previous!=null){check(previous.copy(status=op.status,wasSent=op.wasSent,error=op.error)==op);check(!previous.wasSent||op.wasSent)}
        val f=file(op.scope);val stream=f.startWrite()
        try{stream.write(json.toString().toByteArray());f.finishWrite(stream)}catch(e:Exception){f.failWrite(stream);throw e}
        if(load(op.scope)!=op)throw IOException("Team intent was not committed")
    }
    @Synchronized fun remove(scope:String,id:String,confirmed:Boolean=false){
        val op=load(scope)?:return;check(op.operationId==id);check(confirmed||op.status in setOf("CONFLICT","FAILED"))
        file(scope).delete();if(load(scope)!=null)throw IOException("Team intent could not be removed")
    }
    @Synchronized fun forAccount(prefix:String):List<OnlineTeamOperation> = directory.listFiles()?.filter{it.name.endsWith(".json")||it.name.endsWith(".json.bak")}
        ?.map{if(it.name.endsWith(".bak"))File(it.path.removeSuffix(".bak"))else it}?.distinctBy{it.path}?.map{f->
            try{AtomicFile(f).openRead().use{decode(JSONObject(it.readBytes().toString(Charsets.UTF_8)))}}catch(_:Exception){throw IOException("Pending team operation requires review")}
        }?.filter{it.scope.startsWith(prefix)}?:emptyList()
}
