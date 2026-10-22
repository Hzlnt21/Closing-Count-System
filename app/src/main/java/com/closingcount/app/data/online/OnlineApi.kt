package com.closingcount.app.data.online

import com.closingcount.app.BuildConfig
import com.closingcount.app.data.local.OnlineClosingEntity
import com.closingcount.app.data.local.OnlineEntryEntity
import com.closingcount.app.data.local.OnlineOperationEntity
import com.closingcount.app.data.local.OnlineLifecycleEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.IOException
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URI
import java.time.Instant

class OnlineApiException(val status: Int, val code: String) : IOException("Closing Count API: $status $code")

/** Only origin URLs are accepted. Redirects never forward credentials to a new host. */
class OnlineEndpoint(raw: String, allowLocalQa: Boolean = false) {
    val origin: String
    init {
        val uri = URI(raw.trim())
        require(uri.userInfo == null && uri.query == null && uri.fragment == null)
        require(uri.path.isNullOrEmpty() || uri.path == "/")
        require(!uri.host.isNullOrEmpty() && uri.port in -1..65535 && uri.port != 0)
        val scheme = uri.scheme?.lowercase()
        require(scheme == "https" || (scheme == "http" && BuildConfig.DEBUG && allowLocalQa &&
            uri.host.lowercase() in setOf("127.0.0.1", "10.0.2.2", "localhost")))
        val port = if (uri.port == -1 || (scheme == "https" && uri.port == 443) ||
            (scheme == "http" && uri.port == 80)) "" else ":${uri.port}"
        val host = uri.host.lowercase().let { if (it.contains(':') && !it.startsWith('[')) "[$it]" else it }
        origin = "$scheme://$host$port"
    }
}

data class OnlineOutlet(val id: String, val name: String, val role: String)
data class OnlineIdentity(val id: String, val username: String, val displayName: String, val outlets: List<OnlineOutlet>)

class OnlineSession(val origin: String, val identity: OnlineIdentity, val token: String, val expiresAt: Instant) {
    fun scope(outletId: String): String {
        require(identity.outlets.any { it.id == outletId })
        return "$origin|${identity.id}|$outletId"
    }
    override fun toString() = "OnlineSession(redacted)"
}

data class OnlineSnapshot(val header: OnlineClosingEntity, val entries: List<OnlineEntryEntity>)
data class OnlineReceipt(val generation: Long, val closingRevision: Long, val entryRevision: Long, val quantity: Int)
data class OnlineLifecycleReceipt(val state: String, val generation: Long, val revision: Long)
data class OnlineHistoryRecord(val id: String, val date: String, val state: String, val generation: Long, val revision: Long)
data class OnlineHistoryPage(val items: List<OnlineHistoryRecord>, val nextBefore: String?)
data class OnlineAuditRecord(val id: String, val action: String, val actor: String, val menuId: String?,
    val before: Int?, val after: Int?, val generation: Long, val revision: Long, val reason: String?, val at: String)
data class OnlineAuditPage(val items: List<OnlineAuditRecord>, val nextAfter: String?)
data class OnlineReportRecord(val generation: Long, val revision: Long, val reviewer: String, val at: String)
data class OnlineReportPage(val items: List<OnlineReportRecord>, val nextBefore: Long?)

interface OnlineGateway {
    suspend fun fetch(session: OnlineSession, outletId: String, closingId: String): OnlineSnapshot
    suspend fun send(session: OnlineSession, operation: OnlineOperationEntity): OnlineReceipt
    suspend fun lifecycle(session: OnlineSession, operation: OnlineLifecycleEntity): OnlineLifecycleReceipt =
        throw IOException("Lifecycle transport not available")
}

class OnlineApi(val endpoint: OnlineEndpoint, private val timeoutMillis: Int = 10000) : OnlineGateway {
    private suspend fun request(method: String, path: String, token: String? = null, body: JSONObject? = null): JSONObject =
        withContext(Dispatchers.IO) {
            val connection = URI(endpoint.origin + "/api/v1" + path).toURL().openConnection() as HttpURLConnection
            try {
                connection.requestMethod = method
                connection.connectTimeout = timeoutMillis
                connection.readTimeout = timeoutMillis
                connection.instanceFollowRedirects = false
                connection.useCaches = false
                connection.setRequestProperty("Accept", "application/json")
                token?.let {
                    require(Regex("[a-f0-9]{64}").matches(it))
                    connection.setRequestProperty("Authorization", "Bearer $it")
                }
                if (body != null) {
                    connection.doOutput = true
                    connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                    val bytes = body.toString().toByteArray(Charsets.UTF_8)
                    connection.setFixedLengthStreamingMode(bytes.size)
                    connection.outputStream.use { it.write(bytes) }
                }
                val status = connection.responseCode
                if (status in 300..399) throw IOException("Redirect refused")
                val stream = if (status in 200..299) connection.inputStream else connection.errorStream
                val bytes = stream?.use { input ->
                    val buffer = ByteArray(8192); val output = ByteArrayOutputStream()
                    while (true) {
                        val size = input.read(buffer); if (size < 0) break
                        if (output.size() + size > 2 * 1024 * 1024) throw IOException("Response limit exceeded")
                        output.write(buffer, 0, size)
                    }
                    output.toByteArray()
                } ?: byteArrayOf()
                if (bytes.size > 2 * 1024 * 1024) throw IOException("Response limit exceeded")
                val json = try { JSONObject(bytes.toString(Charsets.UTF_8)) } catch (_: Exception) {
                    throw IOException("Invalid JSON response")
                }
                if (status !in 200..299) {
                    val code = json.optJSONObject("error")?.optString("code")
                        ?.takeIf { Regex("[A-Z_]{1,64}").matches(it) } ?: "API_ERROR"
                    throw OnlineApiException(status, code)
                }
                json.optJSONObject("data") ?: throw IOException("Missing response data")
            } finally { connection.disconnect() }
        }

    suspend fun login(username: String, password: String): OnlineSession {
        val login = request("POST", "/auth/login", body = JSONObject().put("username", username).put("password", password))
        val token = login.getString("accessToken")
        require(Regex("[a-f0-9]{64}").matches(token) && login.getString("tokenType") == "Bearer")
        val identity = identity(request("GET", "/me", token))
        return OnlineSession(endpoint.origin, identity, token, Instant.parse(login.getString("expiresAt")))
    }

    suspend fun refreshIdentity(session: OnlineSession): OnlineSession {
        checkSession(session)
        val identity = identity(request("GET", "/me", session.token))
        require(identity.id == session.identity.id)
        return OnlineSession(endpoint.origin, identity, session.token, session.expiresAt)
    }

    suspend fun logout(session: OnlineSession) {
        checkSession(session)
        request("POST", "/auth/logout", session.token, JSONObject())
    }

    suspend fun businessDate(session: OnlineSession, outletId: String): String {
        checkSession(session); uuid(outletId); session.scope(outletId)
        val value = request("GET", "/outlets/$outletId/business-date", session.token).getString("businessDate")
        require(java.time.LocalDate.parse(value).toString() == value)
        return value
    }

    private fun catalogVersion(row:JSONObject):OnlineCatalogVersion = decode {
        val revision=number(row,"revision")
        val id=if(row.isNull("versionId"))null else uuid(row.getString("versionId"))
        require((revision==0L)==(id==null))
        OnlineCatalogVersion(id,revision,OnlineCatalog.parse(row.getJSONObject("catalog")))
    }
    suspend fun catalog(session:OnlineSession,outlet:String):OnlineCatalogVersion{
        checkSession(session);uuid(outlet);session.scope(outlet)
        return catalogVersion(request("GET","/outlets/$outlet/catalog",session.token))
    }
    suspend fun publishCatalog(session:OnlineSession,op:OnlineCatalogOperation):OnlineCatalogVersion{
        checkSession(session);uuid(op.operationId)
        val parts=op.scope.split('|');require(parts.size==3&&parts[0]==session.origin&&parts[1]==session.identity.id)
        val outlet=uuid(parts[2]);require(session.scope(outlet)==op.scope)
        val body=OnlineCatalogStore.requestBody(op);require(body.toString().toByteArray(Charsets.UTF_8).size<=524288)
        val result=catalogVersion(request("POST","/outlets/$outlet/catalog",session.token,body))
        return decode{require(result.revision==op.expectedRevision+1&&result.catalog==OnlineCatalog.parse(JSONObject(op.catalogJson)));result}
    }

    suspend fun outletSettings(session: OnlineSession, outletId: String): OnlineOutletSettings {
        checkSession(session); uuid(outletId); session.scope(outletId)
        val row = request("GET","/outlets/$outletId",session.token)
        return decode {
            require(row.getString("id") == outletId)
            val role = row.getString("role"); require(role in setOf("ADMIN","SUPERVISOR","EMPLOYEE"))
            val name = row.getString("name"); require(name.trim().length in 1..120)
            val zone = row.getString("timezone"); java.time.ZoneId.of(zone)
            val cutoff = number(row,"cutoffMinutes"); require(cutoff <= 1439)
            OnlineOutletSettings(outletId,name,role,zone,cutoff.toInt(),number(row,"settingsRevision"))
        }
    }

    private fun teamMember(row:JSONObject):OnlineTeamMember {
        val username=row.getString("username");require(username.matches(Regex("[a-z0-9][a-z0-9._-]{2,63}")))
        val name=row.getString("displayName");require(name.trim().length in 1..120)
        val role=row.getString("role");require(role in setOf("ADMIN","SUPERVISOR","EMPLOYEE"))
        require(row.get("active") is Boolean && row.get("accountActive") is Boolean)
        return OnlineTeamMember(uuid(row.getString("id")),username,name,role,row.getBoolean("active"),row.getBoolean("accountActive"),number(row,"revision"))
    }
    suspend fun team(session:OnlineSession,outlet:String,after:String?=null,limit:Int=30):OnlineTeamPage {
        checkSession(session);uuid(outlet);session.scope(outlet);require(limit in 1..100)
        after?.let{require(it.matches(Regex("[a-z0-9][a-z0-9._-]{2,63}")))}
        val data=request("GET","/outlets/$outlet/team?limit=$limit"+(after?.let{"&after=$it"}?:""),session.token)
        return decode{
            val raw=data.getJSONArray("items");require(raw.length()<=limit)
            val rows=(0 until raw.length()).map{teamMember(raw.getJSONObject(it))}
            require(rows.map{it.id}.distinct().size==rows.size&&rows.zipWithNext().all{(a,b)->a.username<b.username}&&rows.all{after==null||it.username>after})
            val next=if(data.isNull("nextAfter"))null else data.getString("nextAfter")
            require(next==null||(rows.size==limit&&rows.last().username==next));OnlineTeamPage(rows,next)
        }
    }
    suspend fun member(session:OnlineSession,outlet:String,id:String):OnlineTeamMember {
        checkSession(session);uuid(outlet);uuid(id);session.scope(outlet)
        val row=request("GET","/outlets/$outlet/team/$id",session.token)
        return decode{teamMember(row).also{require(it.id==id)}}
    }
    private fun teamScope(session:OnlineSession,op:OnlineTeamOperation):String{
        checkSession(session);val outlet=uuid(op.scope.substringAfterLast('|'));uuid(op.operationId)
        require(op.scope=="${session.origin}|${session.identity.id}|$outlet");return outlet
    }
    private fun teamReceipt(data:JSONObject,outlet:String,op:OnlineTeamOperation):OnlineTeamReceipt=decode{
        require(data.getString("outletId")==outlet&&data.getString("operationId")==op.operationId&&data.getString("kind")==op.kind&&data.get("changed") is Boolean)
        val member=teamMember(data.getJSONObject("member"));val p=JSONObject(op.payload);val changed=data.getBoolean("changed")
        require(member.role==p.getString("role"))
        if(op.kind=="CREATE_ACCOUNT")require(changed&&member.username==p.getString("username")&&member.name==p.getString("displayName")&&member.active&&member.accountActive&&member.revision==0L)
        else require(member.id==p.getString("accountId")&&member.active==p.getBoolean("active")&&member.revision==p.getLong("expectedRevision")+(if(changed)1 else 0))
        OnlineTeamReceipt(member,changed)
    }
    suspend fun teamReceipt(session:OnlineSession,op:OnlineTeamOperation):OnlineTeamReceipt {
        val outlet=teamScope(session,op)
        return teamReceipt(request("GET","/outlets/$outlet/team-operations/${op.operationId}",session.token),outlet,op)
    }
    suspend fun sendTeam(session:OnlineSession,op:OnlineTeamOperation,password:String?):OnlineTeamReceipt {
        val outlet=teamScope(session,op);session.scope(outlet);require(op.status=="SENT"&&op.wasSent)
        val payload=JSONObject(op.payload)
        if(op.kind=="CREATE_ACCOUNT"){
            if(password==null)throw TeamPasswordRequiredException()
            require(password.codePointCount(0,password.length)>=12&&password.toByteArray().size<=256);payload.put("password",password)
        }
        val body=JSONObject().put("operationId",op.operationId).put("kind",op.kind).put("payload",payload)
        return teamReceipt(request("POST","/outlets/$outlet/team-operations",session.token,body),outlet,op)
    }

    suspend fun updateSettings(session: OnlineSession, op: OnlineSettingsOperation): Long {
        checkSession(session); require(op.scope == session.scope(op.scope.substringAfterLast('|')))
        require(op.status == "SENT" && op.wasSent); uuid(op.operationId)
        val outletId = op.scope.substringAfterLast('|')
        val body = JSONObject().put("operationId",op.operationId).put("expectedRevision",op.expectedRevision)
            .put("name",op.name).put("timezone",op.timezone).put("cutoffMinutes",op.cutoffMinutes)
        val row = request("POST","/outlets/$outletId/settings",session.token,body)
        return decode {
            require(row.getString("outletId") == outletId && row.getString("operationId") == op.operationId)
            require(row.getString("name") == op.name.trim() && row.getString("timezone") == op.timezone && number(row,"cutoffMinutes") == op.cutoffMinutes.toLong())
            number(row,"settingsRevision",1).also { require(it == op.expectedRevision + 1) }
        }
    }

    suspend fun create(session: OnlineSession, outletId: String, businessDate: String): OnlineSnapshot {
        checkSession(session); uuid(outletId)
        require(java.time.LocalDate.parse(businessDate).toString() == businessDate)
        val data = request("POST", "/outlets/$outletId/closings", session.token, JSONObject().put("businessDate", businessDate))
        return decode { snapshot(session, outletId, data) }
    }

    override suspend fun fetch(session: OnlineSession, outletId: String, closingId: String): OnlineSnapshot {
        checkSession(session); uuid(outletId); uuid(closingId)
        val data = request("GET", "/closings/$closingId", session.token)
        return decode { snapshot(session, outletId, data).also {
            require(it.header.closingId == closingId)
        } }
    }

    override suspend fun send(session: OnlineSession, operation: OnlineOperationEntity): OnlineReceipt {
        checkSession(session); uuid(operation.closingId); uuid(operation.menuId); uuid(operation.operationId)
        require(operation.scope.startsWith("${session.origin}|${session.identity.id}|"))
        require(operation.scope == session.scope(operation.scope.substringAfterLast('|')))
        require(operation.status == "SENT" && operation.quantity in 0..9999)
        val body = JSONObject().put("operationId", operation.operationId).put("generation", operation.generation)
            .put("expectedEntryRevision", requireNotNull(operation.expectedRevision)).put("quantity", operation.quantity)
        val ack = request("POST", "/closings/${operation.closingId}/entries/${operation.menuId}/operations", session.token, body)
        return decode {
            val entry = ack.getJSONObject("entry")
            require(ack.getString("closingId") == operation.closingId && entry.getString("menuId") == operation.menuId)
            OnlineReceipt(number(ack, "generation", 1), number(ack, "closingRevision"), number(entry, "entryRevision"),
                quantity(entry)).also { require(it.generation == operation.generation && it.quantity == operation.quantity) }
        }
    }

    override suspend fun lifecycle(session: OnlineSession, operation: OnlineLifecycleEntity): OnlineLifecycleReceipt {
        checkSession(session); uuid(operation.closingId); uuid(operation.operationId)
        require(operation.scope == session.scope(operation.scope.substringAfterLast('|')))
        require(operation.status == "SENT" && operation.wasSent)
        require(operation.action in setOf("submit", "return", "lock", "reopen"))
        val body = JSONObject().put("operationId", operation.operationId).put("generation", operation.generation)
            .put("expectedRevision", operation.expectedRevision).put("deviceSyncState", "SYNCED")
        if (operation.action in setOf("return", "reopen")) body.put("reason", operation.reason)
        val response = request("POST", "/closings/${operation.closingId}/${operation.action}", session.token, body)
        return decode {
            require(response.getString("closingId") == operation.closingId)
            val expectedState = when (operation.action) { "submit" -> "SUBMITTED"; "lock" -> "LOCKED"; else -> "DRAFT" }
            val expectedGeneration = operation.generation + if (operation.action in setOf("return", "reopen")) 1 else 0
            OnlineLifecycleReceipt(response.getString("state"), number(response, "generation", 1), number(response, "closingRevision")).also {
                require(it.state == expectedState && it.generation == expectedGeneration && it.revision == operation.expectedRevision + 1)
            }
        }
    }

    suspend fun report(session: OnlineSession, outletId: String, closingId: String, generation: Long): String {
        checkSession(session); uuid(closingId); session.scope(outletId); require(generation >= 1)
        val response = request("GET", "/closings/$closingId/reports/$generation", session.token)
        return decode {
            require(response.getString("format") == "closing-count-report" && response.getInt("schemaVersion") == 1)
            require(response.getString("closingId") == closingId && response.getString("outletId") == outletId && number(response, "generation", 1) == generation)
            val frozen = snapshot(session, outletId, response.getJSONObject("closing"))
            require(frozen.header.closingId == closingId && frozen.header.generation == generation && frozen.header.state == "LOCKED")
            require(frozen.header.revision == number(response, "closingRevision"))
            Instant.parse(response.getString("reviewedAt")); uuid(response.getJSONObject("reviewedBy").getString("id"))
            require(response.getJSONObject("reviewedBy").getString("displayName").length in 1..200)
            val closing = response.getJSONObject("closing")
            require(closing.getJSONObject("lockedBy").getString("id") == response.getJSONObject("reviewedBy").getString("id"))
            val date = closing.getString("businessDate"); require(java.time.LocalDate.parse(date).toString() == date)
            java.time.ZoneId.of(closing.getString("timezone"))
            val menus = closing.getJSONObject("catalog").getJSONArray("menus")
            for (i in 0 until menus.length()) require(menus.getJSONObject(i).getString("name").length in 1..200)
            val totals = closing.getJSONArray("ingredientTotals"); require(totals.length() <= 10000)
            for (i in 0 until totals.length()) { val line = totals.getJSONObject(i)
                uuid(line.getString("id")); require(line.getString("name").length in 1..200); number(line,"total") }
            response.toString()
        }
    }

    suspend fun history(session: OnlineSession, outletId: String, before: String? = null, limit: Int = 20): OnlineHistoryPage {
        checkSession(session); uuid(outletId); session.scope(outletId); require(limit in 1..100)
        before?.let { require(java.time.LocalDate.parse(it).toString() == it) }
        val response = request("GET", "/outlets/$outletId/closings?limit=$limit" + (before?.let { "&before=$it" } ?: ""), session.token)
        return decode {
            val raw = response.getJSONArray("items"); require(raw.length() <= limit)
            val items = (0 until raw.length()).map { i -> val row = raw.getJSONObject(i)
                val date = row.getString("businessDate"); require(java.time.LocalDate.parse(date).toString() == date && (before == null || date < before))
                val state = row.getString("state"); require(state in setOf("DRAFT", "SUBMITTED", "LOCKED"))
                OnlineHistoryRecord(uuid(row.getString("id")), date, state, number(row,"generation",1), number(row,"closingRevision"))
            }
            require(items.map { it.id }.distinct().size == items.size && items.zipWithNext().all { (a,b) -> a.date > b.date })
            val next = if (response.isNull("nextBefore")) null else response.getString("nextBefore")
            require(next == null || (items.size == limit && next == items.last().date))
            OnlineHistoryPage(items, next)
        }
    }

    suspend fun audit(session: OnlineSession, outletId: String, closingId: String, after: String = "0", limit: Int = 50): OnlineAuditPage {
        checkSession(session); uuid(closingId); session.scope(outletId); require(limit in 1..100)
        require(after.matches(Regex("0|[1-9][0-9]{0,18}")) && after.toLongOrNull() != null)
        val response = request("GET", "/closings/$closingId/audit?after=$after&limit=$limit", session.token)
        return decode {
            val raw = response.getJSONArray("items"); require(raw.length() <= limit)
            val items = (0 until raw.length()).map { i -> val row = raw.getJSONObject(i)
                val id = row.getString("id"); require(id.matches(Regex("[1-9][0-9]{0,18}")) && (id.toLongOrNull() ?: -1) > after.toLong())
                val action = row.getString("action"); require(action in setOf("CREATE", "ENTRY_UPDATED", "SUBMIT", "RETURN", "LOCK", "REOPEN"))
                uuid(row.getString("actor_id")); val actor = row.getString("actor_name"); require(actor.length in 1..200)
                val menu = if (row.isNull("menu_id")) null else uuid(row.getString("menu_id"))
                fun count(key: String): Int? = if (row.isNull(key)) null else number(row,key).also { require(it <= 9999) }.toInt()
                fun revision(key: String): Long { val value = row.getString(key); require(value.matches(Regex("0|[1-9][0-9]{0,18}"))); return requireNotNull(value.toLongOrNull()) }
                val reason = if (row.isNull("reason")) null else row.getString("reason").also { require(it.length <= 500) }
                val time = row.getString("created_at"); Instant.parse(time)
                OnlineAuditRecord(id, action, actor, menu, count("before_quantity"), count("after_quantity"), revision("generation"), revision("closing_revision"), reason, time)
            }
            require(items.zipWithNext().all { (a,b) -> a.id.toLong() < b.id.toLong() })
            val next = if (response.isNull("nextAfter")) null else response.getString("nextAfter")
            require(next == null || (items.size == limit && next == items.last().id))
            OnlineAuditPage(items,next)
        }
    }

    suspend fun reports(session: OnlineSession, outletId: String, closingId: String, before: Long? = null, limit: Int = 20): OnlineReportPage {
        checkSession(session); uuid(closingId); session.scope(outletId); require(limit in 1..100 && (before == null || before >= 1))
        val response = request("GET", "/closings/$closingId/reports?limit=$limit" + (before?.let { "&beforeGeneration=$it" } ?: ""), session.token)
        return decode {
            val raw = response.getJSONArray("items"); require(raw.length() <= limit)
            val items = (0 until raw.length()).map { i -> val row = raw.getJSONObject(i)
                uuid(row.getString("id")); val generation = number(row,"generation",1); require(before == null || generation < before)
                val actor = row.getJSONObject("reviewedBy"); uuid(actor.getString("id")); val name = actor.getString("displayName"); require(name.length in 1..200)
                val time = row.getString("reviewedAt"); Instant.parse(time)
                OnlineReportRecord(generation,number(row,"closingRevision"),name,time)
            }
            require(items.zipWithNext().all { (a,b) -> a.generation > b.generation })
            val next = if (response.isNull("nextBeforeGeneration")) null else number(response,"nextBeforeGeneration",1)
            require(next == null || (items.size == limit && next == items.last().generation))
            OnlineReportPage(items,next)
        }
    }

    private fun checkSession(session: OnlineSession) { require(session.origin == endpoint.origin) }

    private fun snapshot(session: OnlineSession, outletId: String, data: JSONObject): OnlineSnapshot {
        val id = uuid(data.getString("id")); require(data.getString("outletId") == outletId)
        val scope = session.scope(outletId)
        val generation = number(data, "generation", 1)
        val revision = number(data, "closingRevision")
        val state = data.getString("state"); require(state in setOf("DRAFT", "SUBMITTED", "LOCKED"))
        val raw = data.getJSONArray("entries"); require(raw.length() <= 10000)
        val entries = (0 until raw.length()).map { index ->
            val item = raw.getJSONObject(index); val qty = quantity(item)
            OnlineEntryEntity(scope, id, uuid(item.getString("menuId")), generation, qty,
                number(item, "entryRevision"), qty, "SYNCED")
        }
        require(entries.map { it.menuId }.distinct().size == entries.size)
        val menus = data.getJSONObject("catalog").getJSONArray("menus")
        require((0 until menus.length()).map { uuid(menus.getJSONObject(it).getString("id")) }.toSet() == entries.map { it.menuId }.toSet())
        return OnlineSnapshot(OnlineClosingEntity(scope, id, generation, revision, state, data.toString()), entries)
    }

    private fun identity(data: JSONObject): OnlineIdentity {
        val raw = data.getJSONArray("outlets")
        val outlets = (0 until raw.length()).map { i -> val item = raw.getJSONObject(i)
            OnlineOutlet(uuid(item.getString("outlet_id")), item.getString("name"), item.getString("role").also {
                require(it in setOf("EMPLOYEE", "SUPERVISOR", "ADMIN"))
            })
        }
        require(outlets.map { it.id }.distinct().size == outlets.size)
        return OnlineIdentity(uuid(data.getString("id")), data.getString("username"), data.getString("displayName"), outlets)
    }

    companion object {
        private fun <T> decode(block: () -> T): T = try { block() } catch (_: IllegalArgumentException) {
            throw IOException("Invalid response payload")
        } catch (_: org.json.JSONException) { throw IOException("Invalid response payload")
        } catch (_: java.time.DateTimeException) { throw IOException("Invalid response payload") }
        fun uuid(value: String): String {
            require(Regex("[0-9a-f]{8}-[0-9a-f]{4}-[1-8][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}").matches(value))
            return value
        }
        private fun number(json: JSONObject, key: String, minimum: Long = 0): Long {
            val value = json.get(key); require(value is Int || value is Long)
            return (value as Number).toLong().also { require(it in minimum..9007199254740991L) }
        }
        private fun quantity(json: JSONObject) = number(json, "quantity").also { require(it <= 9999) }.toInt()
    }
}
