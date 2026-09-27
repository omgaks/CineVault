package com.sole.cinevault.network

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import org.json.JSONObject

data class NetworkCredential(
    val username: String = "",
    val secret: String = "",
)

class NetworkCredentialStore(context: Context) {
    private val prefs = EncryptedSharedPreferences.create(
        context.applicationContext,
        "cinevault_network_credentials_secure",
        MasterKey.Builder(context.applicationContext)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )

    fun save(sourceId: String, credential: NetworkCredential) {
        require(isSafeNetworkSourceId(sourceId)) { "Unsafe network source id" }
        prefs.edit().putString(
            sourceId,
            JSONObject()
                .put("username", credential.username)
                .put("secret", credential.secret)
                .toString(),
        ).apply()
    }

    fun load(sourceId: String): NetworkCredential? {
        if (!isSafeNetworkSourceId(sourceId)) return null
        val raw = prefs.getString(sourceId, null) ?: return null
        return try {
            val json = JSONObject(raw)
            NetworkCredential(
                username = json.optString("username"),
                secret = json.optString("secret"),
            )
        } catch (_: Exception) {
            null
        }
    }

    fun remove(sourceId: String) {
        if (isSafeNetworkSourceId(sourceId)) prefs.edit().remove(sourceId).apply()
    }
}

internal fun isSafeNetworkSourceId(value: String): Boolean =
    value.matches(Regex("^[a-z0-9_]+-[a-f0-9]{20}$"))
