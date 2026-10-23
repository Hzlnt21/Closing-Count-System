package com.closingcount.app.data.online

import com.closingcount.app.data.local.OnlineSyncDao
import com.closingcount.app.data.local.OnlineRecoveredAdminEntity
import com.closingcount.app.data.transfer.OnlinePendingBackupCodec
import com.closingcount.app.data.transfer.PendingBackupAdmin
import org.json.JSONObject

/** Existing AtomicFiles remain intact. Recovered actions use Room-backed normal workflow adapters. */
internal class OnlineRecoveredAdminStore(private val dao:OnlineSyncDao,private val settings:OnlineSettingsStore?,
    private val team:OnlineTeamStore?,private val catalog:OnlineCatalogStore?) {
    private suspend fun active(scope:String,group:String)=dao.activeRecoveredAdmin(scope,group).also{check(it.size<=1)}.singleOrNull()
    private suspend fun <T> choose(scope:String,group:String,legacy:T?,convert:(OnlineRecoveredAdminEntity)->T):T? {
        val row=active(scope,group);check(row==null||legacy==null) { "RECOVERY_LOCAL_PENDING" }
        return row?.let(convert)?:legacy
    }
    suspend fun settings(scope:String):OnlineSettingsOperation?=choose(scope,"SETTINGS",settings?.load(scope),::settingsOp)
    suspend fun team(scope:String):OnlineTeamOperation?=choose(scope,"TEAM",team?.load(scope),::teamOp)
    suspend fun catalog(scope:String):OnlineCatalogOperation?=choose(scope,"CATALOG",catalog?.load(scope),::catalogOp)

    suspend fun save(op:OnlineSettingsOperation) = save(op.scope,"SETTINGS",op.operationId,settingsBackup(op),op.error){requireNotNull(settings).save(op)}
    suspend fun save(op:OnlineTeamOperation) = save(op.scope,"TEAM",op.operationId,teamBackup(op),op.error){requireNotNull(team).save(op)}
    suspend fun save(op:OnlineCatalogOperation) = save(op.scope,"CATALOG",op.operationId,catalogBackup(op),op.error){requireNotNull(catalog).save(op)}
    private suspend fun save(scope:String,group:String,id:String,intent:PendingBackupAdmin,error:String?,legacy:()->Unit) {
        val old=dao.recoveredAdminOperation(id)
        if(old==null){check(active(scope,group)==null);legacy();return}
        val checked=canonical(intent)
        check(old.scope==scope&&old.group==group&&old.kind==checked.kind&&old.expectedRevision==checked.expectedRevision)
        dao.saveRecoveredAdmin(old.copy(payloadJson=checked.payloadJson,status=checked.status,wasSent=checked.wasSent,error=error))
    }
    suspend fun remove(scope:String,group:String,id:String,confirmed:Boolean=false) {
        val row=dao.recoveredAdminOperation(id)
        if(row!=null){check(row.scope==scope&&row.group==group);dao.archiveRecoveredAdmin(scope,id,confirmed);return}
        when(group){"SETTINGS"->requireNotNull(settings).remove(scope,id,confirmed);"TEAM"->requireNotNull(team).remove(scope,id,confirmed);"CATALOG"->requireNotNull(catalog).remove(scope,id,confirmed);else->error("Unknown group")}
    }
    suspend fun count(prefix:String):Int {
        val rows=dao.recoveredAdminForAccount(prefix)
        for(row in rows)when(row.group){"SETTINGS"->settings(row.scope);"TEAM"->team(row.scope);"CATALOG"->catalog(row.scope);else->error("Unknown group")}
        return (settings?.pendingForAccount(prefix)?:0)+(team?.forAccount(prefix)?.size?:0)+(catalog?.pendingForAccount(prefix)?:0)+rows.size
    }
    suspend fun teamForAccount(prefix:String):List<OnlineTeamOperation> {
        val old=team?.forAccount(prefix)?:emptyList();val rows=dao.recoveredAdminForAccount(prefix).filter{it.group=="TEAM"}
        check(rows.none{row->old.any{it.scope==row.scope}}) { "RECOVERY_LOCAL_PENDING" }
        return old+rows.map(::teamOp)
    }
    suspend fun capture(scope:String):List<PendingBackupAdmin> {
        // Keep strict legacy reads, including corrupt-file detection, before considering Room overlay.
        val originals=listOfNotNull(requireNotNull(settings).captureForBackup(scope),requireNotNull(team).captureForBackup(scope),requireNotNull(catalog).captureForBackup(scope))
        val recovered=listOfNotNull(active(scope,"SETTINGS"),active(scope,"TEAM"),active(scope,"CATALOG"))
        check(recovered.none{row->originals.any{group(it.kind)==row.group}}) { "RECOVERY_LOCAL_PENDING" }
        return originals+recovered.map{checked(it)}
    }
    suspend fun requireFree(scope:String,group:String) {
        val pending=when(group){"SETTINGS"->settings(scope);"TEAM"->team(scope);"CATALOG"->catalog(scope);else->error("Unknown group")}
        check(pending==null) { "RECOVERY_LOCAL_PENDING" }
    }
    suspend fun attach(value:OnlineRecoveredAdminEntity) {
        requireFree(value.scope,value.group);val normalized=canonical(checked(value))
        dao.attachRecoveredAdmin(value.copy(payloadJson=normalized.payloadJson))
    }
    companion object {
        fun group(kind:String)=when(kind){"SETTINGS"->"SETTINGS";"TEAM_CREATE","TEAM_UPDATE"->"TEAM";"CATALOG"->"CATALOG";else->error("Unknown intent kind")}
        private fun OnlineRecoveredAdminEntity.backup()=PendingBackupAdmin(kind,operationId,expectedRevision,status,wasSent,payloadJson)
        private fun validate(op:PendingBackupAdmin):PendingBackupAdmin = OnlinePendingBackupCodec.decodeAdmin(JSONObject().put("kind",op.kind).put("operationId",op.operationId)
            .put("expectedRevision",op.expectedRevision?:JSONObject.NULL).put("status",op.status).put("wasSent",op.wasSent).put("payload",OnlinePendingBackupCodec.strictObject(op.payloadJson)))
        private fun checked(row:OnlineRecoveredAdminEntity):PendingBackupAdmin {
            require(row.group==group(row.kind)&&Regex("[0-9a-f]{64}").matches(row.fingerprint))
            OnlineApi.uuid(row.originalOperationId);return validate(row.backup())
        }
        private fun canonical(source:PendingBackupAdmin):PendingBackupAdmin {
            val op=validate(source);val p=JSONObject(op.payloadJson)
            val payload=when(op.kind){
                "SETTINGS"->JSONObject().put("name",p.getString("name")).put("timezone",p.getString("timezone")).put("cutoffMinutes",p.getInt("cutoffMinutes"))
                "TEAM_CREATE"->JSONObject().put("username",p.getString("username")).put("displayName",p.getString("displayName")).put("role",p.getString("role"))
                "TEAM_UPDATE"->JSONObject().put("accountId",p.getString("accountId")).put("role",p.getString("role")).put("active",p.getBoolean("active"))
                "CATALOG"->OnlineCatalog.parse(p).json()
                else->error("Unknown intent")
            }
            return op.copy(payloadJson=payload.toString())
        }
        private fun settingsBackup(op:OnlineSettingsOperation)=PendingBackupAdmin("SETTINGS",op.operationId,op.expectedRevision,op.status,op.wasSent,
            JSONObject().put("name",op.name).put("timezone",op.timezone).put("cutoffMinutes",op.cutoffMinutes).toString())
        private fun teamBackup(op:OnlineTeamOperation):PendingBackupAdmin {
            require(op.kind in setOf("CREATE_ACCOUNT","UPDATE_MEMBERSHIP"))
            val payload=JSONObject(op.payload);val revision=if(op.kind=="UPDATE_MEMBERSHIP")payload.getLong("expectedRevision").also{payload.remove("expectedRevision")}else null
            return PendingBackupAdmin(if(op.kind=="CREATE_ACCOUNT")"TEAM_CREATE" else "TEAM_UPDATE",op.operationId,revision,op.status,op.wasSent,payload.toString())
        }
        private fun catalogBackup(op:OnlineCatalogOperation)=PendingBackupAdmin("CATALOG",op.operationId,op.expectedRevision,op.status,op.wasSent,op.catalogJson)
        private fun settingsOp(row:OnlineRecoveredAdminEntity):OnlineSettingsOperation {
            val op=checked(row);val p=JSONObject(op.payloadJson)
            return OnlineSettingsOperation(row.scope,op.operationId,requireNotNull(op.expectedRevision),p.getString("name"),p.getString("timezone"),p.getInt("cutoffMinutes"),op.status,op.wasSent,row.error)
        }
        private fun teamOp(row:OnlineRecoveredAdminEntity):OnlineTeamOperation {
            val op=checked(row);val payload=JSONObject(op.payloadJson)
            if(op.kind=="TEAM_UPDATE")payload.put("expectedRevision",requireNotNull(op.expectedRevision))
            return OnlineTeamOperation(row.scope,op.operationId,if(op.kind=="TEAM_CREATE")"CREATE_ACCOUNT" else "UPDATE_MEMBERSHIP",payload.toString(),op.status,op.wasSent,row.error)
        }
        private fun catalogOp(row:OnlineRecoveredAdminEntity):OnlineCatalogOperation {
            val op=checked(row);return OnlineCatalogOperation(row.scope,op.operationId,requireNotNull(op.expectedRevision),op.payloadJson,op.status,op.wasSent,row.error)
        }
    }
}
