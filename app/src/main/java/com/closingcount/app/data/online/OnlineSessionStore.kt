package com.closingcount.app.data.online

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.security.KeyStore
import java.time.Instant
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Encrypted, backup-excluded session only. Passwords and queue data are never stored here. */
class OnlineSessionStore(context: Context, private val alias: String = "ccs-online-session-v1") {
    private val preferences = context.getSharedPreferences("ccs_online_session", Context.MODE_PRIVATE)
    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(alias, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
        }.generateKey()
    }
    @Synchronized fun save(session: OnlineSession) {
        val outlets = JSONArray()
        session.identity.outlets.forEach { outlets.put(JSONObject().put("id", it.id).put("name", it.name).put("role", it.role)) }
        val json = JSONObject().put("origin", session.origin).put("id", session.identity.id)
            .put("username", session.identity.username).put("displayName", session.identity.displayName)
            .put("outlets", outlets).put("token", session.token).put("expiresAt", session.expiresAt.toString())
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key()) }
        val bytes = cipher.doFinal(json.toString().toByteArray(Charsets.UTF_8))
        check(preferences.edit().clear().putString("iv", Base64.encodeToString(cipher.iv, Base64.NO_WRAP))
            .putString("value", Base64.encodeToString(bytes, Base64.NO_WRAP)).commit())
    }
    @Synchronized fun load(): OnlineSession? {
        val value = preferences.getString("value", null) ?: return null
        return try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply {
                init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, Base64.decode(preferences.getString("iv", null), Base64.NO_WRAP)))
            }
            val json = JSONObject(cipher.doFinal(Base64.decode(value, Base64.NO_WRAP)).toString(Charsets.UTF_8))
            val raw = json.getJSONArray("outlets")
            val outlets = (0 until raw.length()).map { i -> val item = raw.getJSONObject(i)
                OnlineOutlet(item.getString("id"), item.getString("name"), item.getString("role")) }
            OnlineSession(json.getString("origin"), OnlineIdentity(json.getString("id"), json.getString("username"),
                json.getString("displayName"), outlets), json.getString("token"), Instant.parse(json.getString("expiresAt")))
        } catch (_: Exception) { clear(); null }
    }
    @Synchronized fun clear() { check(preferences.edit().clear().commit()) }
}
