package com.closingcount.app

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.closingcount.app.data.transfer.*
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.security.MessageDigest

@RunWith(AndroidJUnit4::class)
class OnlinePendingBackupInstrumentedTest {
    private fun id(n: Int) = "00000000-0000-4000-8000-${n.toString().padStart(12,'0')}"
    private fun sample(): OnlinePendingBackup {
        val entry = PendingBackupEntry(id(4), "Kopi susu \u2615 \uD83C\uDF75", 6, 2, 8, "PENDING")
        val queue = listOf(PendingBackupQuantity(id(5), id(4), 1, 0, 4, "SENT"),
            PendingBackupQuantity(id(6), id(4), 2, null, 8, "QUEUED"))
        return OnlinePendingBackup(id(1), "2026-10-23T00:00:00Z", id(2), "https://closing.example.test",
            PendingBackupAccount(id(3), "rani.qa", "Rani QA"), PendingBackupOutlet(id(7), "Outlet QA"),
            listOf(PendingBackupClosing(id(8), "2026-10-23", 2, 5, "DRAFT", listOf(entry), queue, null),
                PendingBackupClosing(id(9), "2026-10-22", 2, 4, "DRAFT", emptyList(), emptyList(),
                    PendingBackupLifecycle(id(10), "reopen", 1, 3, "Koreksi angka", "AUTH_REQUIRED", true))),
            listOf(PendingBackupAdmin("SETTINGS", id(11), 2, "QUEUED", false,
                JSONObject().put("name","Outlet berikutnya").put("timezone","Asia/Jakarta").put("cutoffMinutes",0).toString()),
                PendingBackupAdmin("TEAM_CREATE", id(12), null, "SENT", true,
                    JSONObject().put("username","dimas.qa").put("displayName","Dimas QA").put("role","EMPLOYEE").toString()),
                PendingBackupAdmin("CATALOG", id(13), 4, "EDITING", false,
                    JSONObject().put("ingredientCategories",JSONArray()).put("ingredients",JSONArray())
                        .put("menuCategories",JSONArray()).put("menus",JSONArray()).toString())))
    }
    private fun bytes() = OnlinePendingBackupCodec.encode(sample())
    private fun json() = JSONObject(String(bytes(), Charsets.UTF_8))
    private fun reject(block: () -> Unit) { assertNotNull(runCatching(block).exceptionOrNull()) }
    private fun rejectJson(change: (JSONObject) -> Unit) {
        val root=json();change(root);reject { OnlinePendingBackupCodec.decode(root.toString().toByteArray(Charsets.UTF_8)) }
    }
    @Test fun completePacketRoundTripsWithoutCredentialsAndKeepsUncertainDependentIntent() {
        val encoded=bytes();val decoded=OnlinePendingBackupCodec.decode(encoded)
        assertEquals(sample(),decoded.model);assertEquals(encoded.size,decoded.bytes)
        assertEquals(MessageDigest.getInstance("SHA-256").digest(encoded).joinToString(""){"%02x".format(it)},decoded.sha256)
        assertArrayEquals(encoded,OnlinePendingBackupCodec.encode(decoded.model))
        val q=decoded.model.closings.first().operations
        assertEquals(listOf(1L,2L),q.map { it.generation });assertNull(q[1].expectedRevision)
        val text=String(encoded,Charsets.UTF_8)
        for(key in listOf("token","password","passwordHash","expiresAt","session","lockedBy","updatedBy"))assertFalse(text.contains("\"$key\""))
    }
    @Test fun teamMembershipIntentAndNumericBoundsRemainExact() {
        val update=PendingBackupAdmin("TEAM_UPDATE",id(20),9007199254740991L,"CONFLICT",true,
            JSONObject().put("accountId",id(21)).put("role","SUPERVISOR").put("active",false).toString())
        val model=sample().copy(admin=listOf(update),closings=sample().closings.map{it.copy(revision=9007199254740991L)})
        assertEquals(model,OnlinePendingBackupCodec.decode(OnlinePendingBackupCodec.encode(model)).model)
        rejectJson { it.getJSONArray("admin").getJSONObject(0).put("expectedRevision",9007199254740992L) }
        rejectJson { it.getJSONArray("closings").getJSONObject(0).getJSONArray("entries").getJSONObject(0).put("localQuantity",10000) }
    }
    @Test fun rejectsUnknownOrCredentialFieldsInEveryLayer() {
        rejectJson { it.put("token","not-a-session") }
        rejectJson { it.getJSONObject("account").put("role","ADMIN") }
        rejectJson { it.getJSONArray("admin").getJSONObject(1).getJSONObject("payload").put("password","never-export-this") }
        rejectJson { it.getJSONArray("closings").getJSONObject(0).put("snapshotJson","historical-authorship") }
        rejectJson { it.put("schemaVersion",2) }
    }
    @Test fun rejectsCoercionFractionsExponentsAndNoncanonicalNumbers() {
        rejectJson { it.put("schemaVersion","1") }
        rejectJson { it.getJSONArray("closings").getJSONObject(0).put("generation",1.5) }
        rejectJson { it.getJSONArray("closings").getJSONObject(0).getJSONArray("entries").getJSONObject(0).put("baseQuantity",true) }
        rejectJson { it.getJSONArray("admin").getJSONObject(0).put("wasSent","false") }
        val raw=String(bytes(),Charsets.UTF_8)
        for(number in listOf("1e0","1.0","-0","01","9223372036854775807")) {
            reject { OnlinePendingBackupCodec.decode(raw.replace("\"schemaVersion\":1","\"schemaVersion\":$number").toByteArray()) }
        }
    }
    @Test fun rejectsDuplicateMembersOperationIdsAndInconsistentQueues() {
        val raw=String(bytes(),Charsets.UTF_8)
        reject { OnlinePendingBackupCodec.decode(raw.replace("\"schemaVersion\":1","\"schemaVersion\":1,\"schemaVersion\":1").toByteArray()) }
        rejectJson { it.getJSONArray("closings").getJSONObject(0).getJSONArray("operations").getJSONObject(1).put("operationId",id(5)) }
        rejectJson { it.getJSONArray("closings").getJSONObject(0).getJSONArray("operations").getJSONObject(0).put("expectedRevision",JSONObject.NULL) }
        rejectJson { it.getJSONArray("closings").getJSONObject(0).getJSONArray("operations").getJSONObject(1).put("expectedRevision",2) }
        rejectJson { it.getJSONArray("closings").getJSONObject(0).getJSONArray("entries").getJSONObject(0).put("localQuantity",7) }
        rejectJson { it.getJSONArray("closings").getJSONObject(0).getJSONArray("operations").getJSONObject(0).put("generation",3) }
    }
    @Test fun rejectsScopeDateAndLifecycleContradictions() {
        rejectJson { it.put("origin","https://closing.example.test/path") }
        rejectJson { it.getJSONObject("account").put("id","3") }
        rejectJson { it.getJSONArray("closings").getJSONObject(0).put("date","2026-02-29") }
        rejectJson { it.getJSONArray("closings").getJSONObject(0).put("date","2026-10-22") }
        rejectJson { it.getJSONArray("closings").getJSONObject(1).getJSONObject("lifecycle").put("reason","") }
        rejectJson { it.getJSONArray("admin").getJSONObject(1).put("wasSent",false) }
        rejectJson { it.getJSONArray("admin").getJSONObject(2).put("wasSent",true) }
    }
    @Test fun rejectsMalformedUtf8SurrogatesTrailingDataAndOversizedInput() {
        reject { OnlinePendingBackupCodec.decode(byteArrayOf(0xc3.toByte(),0x28)) }
        reject { OnlinePendingBackupCodec.decode(bytes()+" true".toByteArray()) }
        reject { OnlinePendingBackupCodec.decode(ByteArray(OnlinePendingBackupCodec.MAX_BYTES+1)) }
        reject { OnlinePendingBackupCodec.encode(sample().copy(account=sample().account.copy(name="Bad\uD800"))) }
        val raw=String(bytes(),Charsets.UTF_8).replace("Rani QA","Bad\\ud800")
        reject { OnlinePendingBackupCodec.decode(raw.toByteArray()) }
        val nested="[".repeat(22)+"0"+"]".repeat(22)
        reject { OnlinePendingBackupCodec.decode("{\"extra\":$nested}".toByteArray()) }
    }
    @Test fun stableFileFingerprintDiffersWhenTheProviderChangesBytes() {
        val original=bytes();val pretty=json().toString(2).toByteArray()
        assertEquals(OnlinePendingBackupCodec.decode(original).model,OnlinePendingBackupCodec.decode(pretty).model)
        assertNotEquals(OnlinePendingBackupCodec.decode(original).sha256,OnlinePendingBackupCodec.decode(pretty).sha256)
        assertEquals(OnlinePendingBackupCodec.decode(original).sha256,OnlinePendingBackupCodec.decode(original.copyOf()).sha256)
    }
}
