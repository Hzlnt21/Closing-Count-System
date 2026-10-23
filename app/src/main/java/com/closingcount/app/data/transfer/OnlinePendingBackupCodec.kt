package com.closingcount.app.data.transfer

import android.util.JsonReader
import android.util.JsonToken
import com.closingcount.app.data.online.OnlineApi
import com.closingcount.app.data.online.OnlineCatalog
import com.closingcount.app.data.online.OnlineEndpoint
import org.json.JSONArray
import org.json.JSONObject
import java.io.StringReader
import java.nio.ByteBuffer
import java.nio.CharBuffer
import java.nio.charset.CodingErrorAction
import java.security.MessageDigest
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

object OnlinePendingBackupCodec {
    const val FORMAT = "closing-count-online-pending-backup"
    const val VERSION = 1
    const val MAX_BYTES = 20 * 1024 * 1024
    private const val MAX_INTEGER = 9007199254740991L
    private val statuses = setOf("QUEUED", "SENT", "AUTH_REQUIRED", "CONFLICT", "FAILED")
    private val entryStatuses = setOf("PENDING", "SYNCING", "AUTH_REQUIRED", "CONFLICT", "FAILED")

    fun encode(model: OnlinePendingBackup): ByteArray {
        val root = JSONObject().put("format", FORMAT).put("schemaVersion", VERSION)
            .put("backupId", model.backupId).put("createdAt", model.createdAt).put("sourceDeviceId", model.sourceDeviceId)
            .put("origin", model.origin)
            .put("account", JSONObject().put("id", model.account.id).put("username", model.account.username).put("name", model.account.name))
            .put("outlet", JSONObject().put("id", model.outlet.id).put("name", model.outlet.name))
            .put("closings", JSONArray(model.closings.map { c ->
                JSONObject().put("id", c.id).put("date", c.date).put("generation", c.generation).put("revision", c.revision).put("state", c.state)
                    .put("entries", JSONArray(c.entries.map { e -> JSONObject().put("menuId", e.menuId).put("name", e.name)
                        .put("baseQuantity", e.baseQuantity).put("baseRevision", e.baseRevision).put("localQuantity", e.localQuantity).put("status", e.status) }))
                    .put("operations", JSONArray(c.operations.map { op -> JSONObject().put("operationId", op.operationId).put("menuId", op.menuId)
                        .put("generation", op.generation).put("expectedRevision", op.expectedRevision ?: JSONObject.NULL)
                        .put("quantity", op.quantity).put("status", op.status) }))
                    .put("lifecycle", c.lifecycle?.let { op -> JSONObject().put("operationId", op.operationId).put("action", op.action)
                        .put("generation", op.generation).put("expectedRevision", op.expectedRevision).put("reason", op.reason)
                        .put("status", op.status).put("wasSent", op.wasSent) } ?: JSONObject.NULL)
            }))
            .put("admin", JSONArray(model.admin.map { op -> JSONObject().put("kind", op.kind).put("operationId", op.operationId)
                .put("expectedRevision", op.expectedRevision ?: JSONObject.NULL).put("status", op.status).put("wasSent", op.wasSent)
                .put("payload", strictObject(op.payloadJson)) }))
        val encoded = Charsets.UTF_8.newEncoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT)
            .encode(CharBuffer.wrap(root.toString()))
        val bytes = ByteArray(encoded.remaining());encoded.get(bytes)
        decode(bytes) // the app must never offer an unreadable or credential-bearing packet
        return bytes
    }

    fun decode(bytes: ByteArray): DecodedPendingBackup {
        require(bytes.isNotEmpty() && bytes.size <= MAX_BYTES) { "Ukuran cadangan draft tidak valid." }
        val root = readObject(bytes)
        exact(root, "format", "schemaVersion", "backupId", "createdAt", "sourceDeviceId", "origin", "account", "outlet", "closings", "admin")
        require(string(root, "format") == FORMAT && number(root, "schemaVersion") == VERSION.toLong()) { "Format atau versi cadangan draft belum didukung." }
        val backupId = id(root, "backupId");val deviceId = id(root, "sourceDeviceId")
        val createdAt = string(root, "createdAt");val at = Instant.parse(createdAt)
        require(at.epochSecond >= 0 && at.atOffset(ZoneOffset.UTC).year <= 9999 && at.toString() == createdAt)
        val origin = string(root, "origin");require(OnlineEndpoint(origin, allowLocalQa = true).origin == origin)
        val a = root.getJSONObject("account");exact(a, "id", "username", "name")
        val account = PendingBackupAccount(id(a, "id"), string(a, "username").also { require(it.matches(Regex("[a-z0-9][a-z0-9._-]{2,63}"))) }, name(a, "name"))
        val o = root.getJSONObject("outlet");exact(o, "id", "name")
        val outlet = PendingBackupOutlet(id(o, "id"), name(o, "name"))
        val operationIds = mutableSetOf<String>()
        fun operationId(row: JSONObject) = id(row, "operationId").also { require(operationIds.add(it)) { "ID operasi cadangan berulang." } }
        var operationCount = 0
        val closings = objects(root, "closings", 1000).map { c ->
            exact(c, "id", "date", "generation", "revision", "state", "entries", "operations", "lifecycle")
            val closingId = id(c, "id");val date = string(c, "date")
            require(LocalDate.parse(date).let { it.year in 1..9999 && it.toString() == date })
            val generation = number(c, "generation", 1);val revision = number(c, "revision")
            val state = string(c, "state").also { require(it in setOf("DRAFT", "SUBMITTED", "LOCKED")) }
            val entries = objects(c, "entries", 500).map { e ->
                exact(e, "menuId", "name", "baseQuantity", "baseRevision", "localQuantity", "status")
                PendingBackupEntry(id(e, "menuId"), name(e, "name"), quantity(e, "baseQuantity"), number(e, "baseRevision"),
                    quantity(e, "localQuantity"), string(e, "status").also { require(it in entryStatuses) })
            }
            require(entries.map { it.menuId }.distinct().size == entries.size)
            val operations = objects(c, "operations", 10000).map { op ->
                exact(op, "operationId", "menuId", "generation", "expectedRevision", "quantity", "status")
                PendingBackupQuantity(operationId(op), id(op, "menuId"), number(op, "generation", 1).also { require(it <= generation) },
                    nullableNumber(op, "expectedRevision"), quantity(op, "quantity"), string(op, "status").also { require(it in statuses) })
            }
            operationCount += operations.size;require(operationCount <= 10000)
            require(operations.map { it.menuId }.toSet() == entries.map { it.menuId }.toSet())
            for (entry in entries) {
                val queue = operations.filter { it.menuId == entry.menuId }
                require(queue.isNotEmpty() && queue.first().expectedRevision != null)
                require(queue.drop(1).all { it.expectedRevision == null })
                require(queue.last().quantity == entry.localQuantity) { "Nilai draft tidak cocok dengan antreannya." }
            }
            val lifecycle = if (c.isNull("lifecycle")) null else c.getJSONObject("lifecycle").let { op ->
                exact(op, "operationId", "action", "generation", "expectedRevision", "reason", "status", "wasSent")
                val action = string(op, "action").also { require(it in setOf("submit", "return", "lock", "reopen")) }
                val reason = string(op, "reason");require(reason == reason.trim())
                require(if (action in setOf("return", "reopen")) reason.length in 1..500 else reason.isEmpty())
                val status = string(op, "status");val wasSent = bool(op, "wasSent");sentState(status, wasSent, false)
                PendingBackupLifecycle(operationId(op), action, number(op, "generation", 1).also { require(it <= generation) },
                    number(op, "expectedRevision"), reason, status, wasSent)
            }
            require((operations.isNotEmpty() || lifecycle != null) && !(operations.isNotEmpty() && lifecycle != null))
            PendingBackupClosing(closingId, date, generation, revision, state, entries, operations, lifecycle)
        }
        require(closings.map { it.id }.distinct().size == closings.size && closings.map { it.date }.distinct().size == closings.size)
        val groups = mutableSetOf<String>()
        val admin = objects(root, "admin", 3).map { op ->
            val parsed=decodeAdmin(op)
            require(operationIds.add(parsed.operationId)) { "ID operasi cadangan berulang." }
            require(groups.add(if(parsed.kind.startsWith("TEAM_"))"TEAM" else parsed.kind))
            parsed
        }
        val model = OnlinePendingBackup(backupId, createdAt, deviceId, origin, account, outlet, closings, admin)
        return DecodedPendingBackup(model, MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }, bytes.size)
    }

    internal fun decodeAdmin(op: JSONObject): PendingBackupAdmin {
        exact(op,"kind","operationId","expectedRevision","status","wasSent","payload")
        val kind=string(op,"kind");require(kind in setOf("SETTINGS","CATALOG","TEAM_CREATE","TEAM_UPDATE"))
        val base=nullableNumber(op,"expectedRevision");require((base==null)==(kind=="TEAM_CREATE"))
        val status=string(op,"status");val wasSent=bool(op,"wasSent");sentState(status,wasSent,kind=="CATALOG")
        val payload=op.getJSONObject("payload");val opId=id(op,"operationId")
        when(kind) {
            "SETTINGS" -> { exact(payload,"name","timezone","cutoffMinutes");name(payload,"name");ZoneId.of(string(payload,"timezone"));require(number(payload,"cutoffMinutes")<=1439) }
            "TEAM_CREATE" -> { exact(payload,"username","displayName","role");require(string(payload,"username").matches(Regex("[a-z0-9][a-z0-9._-]{2,63}")));name(payload,"displayName");role(payload) }
            "TEAM_UPDATE" -> { exact(payload,"accountId","role","active");id(payload,"accountId");role(payload);bool(payload,"active") }
            "CATALOG" -> { require(requireNotNull(base)<MAX_INTEGER);OnlineCatalog.parse(payload)
                require(JSONObject().put("operationId",opId).put("expectedRevision",base).put("catalog",payload).toString().toByteArray(Charsets.UTF_8).size<=524288) }
        }
        return PendingBackupAdmin(kind,opId,base,status,wasSent,payload.toString())
    }

    internal fun readObject(bytes: ByteArray): JSONObject {
        require(bytes.isNotEmpty() && bytes.size<=MAX_BYTES)
        return strictObject(Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT)
            .decode(ByteBuffer.wrap(bytes)).toString())
    }

    private fun sentState(status: String, wasSent: Boolean, editing: Boolean) {
        require(status in statuses || (editing && status == "EDITING"))
        require(!wasSent || status !in setOf("QUEUED", "EDITING"))
        require(status != "SENT" || wasSent)
    }
    private fun role(row: JSONObject) { require(string(row, "role") in setOf("ADMIN", "SUPERVISOR", "EMPLOYEE")) }
    private fun exact(row: JSONObject, vararg keys: String) { require(row.keys().asSequence().toSet() == keys.toSet()) { "Kolom cadangan draft tidak dikenali." } }
    private fun string(row: JSONObject, key: String) = row.get(key).let { require(it is String);it }
    private fun name(row: JSONObject, key: String) = string(row, key).also { require(it == it.trim() && it.length in 1..120) }
    private fun id(row: JSONObject, key: String) = OnlineApi.uuid(string(row, key))
    private fun bool(row: JSONObject, key: String) = row.get(key).let { require(it is Boolean);it }
    private fun number(row: JSONObject, key: String, minimum: Long = 0): Long = row.get(key).let {
        require(it is Int || it is Long);(it as Number).toLong().also { n -> require(n in minimum..MAX_INTEGER) }
    }
    private fun nullableNumber(row: JSONObject, key: String) = if (row.isNull(key)) null else number(row, key)
    private fun quantity(row: JSONObject, key: String) = number(row, key).also { require(it <= 9999) }.toInt()
    private fun objects(row: JSONObject, key: String, limit: Int): List<JSONObject> = row.getJSONArray(key).let { a ->
        require(a.length() <= limit);(0 until a.length()).map { a.getJSONObject(it) }
    }

    /** Strict lexical parse rejects duplicate keys, trailing JSON, coercion and oversized graphs. */
    internal fun strictObject(text: String): JSONObject {
        require(text.length <= MAX_BYTES)
        var nodes = 0
        fun unicode(value: String): String {
            var i = 0
            while (i < value.length) {
                val c = value[i++]
                if (c.isHighSurrogate()) { require(i < value.length && value[i].isLowSurrogate());i++ }
                else require(!c.isLowSurrogate())
            }
            require(value.length <= 1048576)
            return value
        }
        JsonReader(StringReader(text)).use { reader ->
            reader.isLenient = false
            fun value(depth: Int): Any {
                require(depth <= 20 && ++nodes <= 250000)
                return when (reader.peek()) {
                    JsonToken.BEGIN_OBJECT -> {
                        reader.beginObject();val result = JSONObject();val keys = mutableSetOf<String>()
                        while (reader.hasNext()) {
                            val key = unicode(reader.nextName());require(keys.add(key) && keys.size <= 64)
                            result.put(key, value(depth+1))
                        }
                        reader.endObject();result
                    }
                    JsonToken.BEGIN_ARRAY -> {
                        reader.beginArray();val result = JSONArray()
                        while (reader.hasNext()) { require(result.length() < 10000);result.put(value(depth+1)) }
                        reader.endArray();result
                    }
                    JsonToken.STRING -> unicode(reader.nextString())
                    JsonToken.NUMBER -> reader.nextString().also { require(it.matches(Regex("0|[1-9][0-9]*"))) }.toLong().also { require(it <= MAX_INTEGER) }
                    JsonToken.BOOLEAN -> reader.nextBoolean()
                    JsonToken.NULL -> { reader.nextNull();JSONObject.NULL }
                    else -> throw IllegalArgumentException("JSON cadangan draft tidak valid.")
                }
            }
            val result = value(0);require(result is JSONObject && reader.peek() == JsonToken.END_DOCUMENT)
            return result
        }
    }
}
