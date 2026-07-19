package com.sole.cinevault

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

// ── Credential storage — now Keystore-encrypted ────────────────────────────
// Previously plain SharedPreferences: NAS/PC share usernames and passwords
// sat in cleartext on disk, readable by anything with root access or a plain
// (non-encrypted) adb backup of the app. EncryptedSharedPreferences
// transparently encrypts both keys and values using a key that only exists
// inside the device's hardware-backed Android Keystore — from every other
// function in this file's point of view it behaves exactly like a normal
// SharedPreferences, so nothing calling loadSmbShares()/saveSmbShares()
// anywhere else in the app needs to change at all.

data class SmbShare(
    val id: String = UUID.randomUUID().toString(),
    val displayName: String,
    val host: String,
    val shareName: String,
    // Optional path within the share, e.g. "Movies/" — blank scans the whole share.
    val subPath: String = "",
    // Blank username = anonymous/guest access.
    val username: String = "",
    val password: String = "",
    val domain: String = ""
)

private const val SMB_SHARES_PREF = "cinevault_smb_shares_secure"
private const val SMB_SHARES_PREF_LEGACY = "cinevault_smb_shares"
private const val SMB_SHARES_KEY = "shares"

private fun securePrefs(context: Context): SharedPreferences {
    val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()
    return EncryptedSharedPreferences.create(
        context,
        SMB_SHARES_PREF,
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )
}

// One-time migration for anyone upgrading from before encrypted storage
// existed. Reads whatever's sitting in the old plain-text prefs file, copies
// it into the new encrypted store, then wipes the old file — leaving it
// alone would mean the plaintext passwords just sit there unused on disk
// forever, defeating the point of encrypting anything going forward.
private fun migrateLegacySharesIfNeeded(context: Context) {
    val legacyPrefs = context.getSharedPreferences(SMB_SHARES_PREF_LEGACY, Context.MODE_PRIVATE)
    val legacyRaw = legacyPrefs.getString(SMB_SHARES_KEY, null)
    if (legacyRaw.isNullOrBlank() || legacyRaw == "[]") {
        if (legacyRaw != null) legacyPrefs.edit().clear().apply()
        return
    }
    try {
        val secure = securePrefs(context)
        // Only migrate if the encrypted store is empty — never overwrite
        // anything already saved there.
        if (secure.getString(SMB_SHARES_KEY, null).isNullOrBlank()) {
            secure.edit().putString(SMB_SHARES_KEY, legacyRaw).apply()
        }
        legacyPrefs.edit().clear().apply()
    } catch (_: Exception) {
        // If migration fails for any reason, leave the legacy file in place
        // rather than risk losing saved shares outright — loadSmbShares()
        // below still works against the encrypted store either way, so a
        // failed migration just means old shares need re-adding manually.
    }
}

fun loadSmbShares(context: Context): List<SmbShare> {
    migrateLegacySharesIfNeeded(context)
    val raw = try {
        securePrefs(context).getString(SMB_SHARES_KEY, "[]") ?: "[]"
    } catch (_: Exception) {
        "[]"
    }
    return try {
        val array = JSONArray(raw)
        buildList {
            for (i in 0 until array.length()) {
                val o = array.optJSONObject(i) ?: continue
                val host = o.optString("host")
                val shareName = o.optString("shareName")
                if (host.isBlank() || shareName.isBlank()) continue
                add(
                    SmbShare(
                        id = o.optString("id").ifBlank { UUID.randomUUID().toString() },
                        displayName = o.optString("displayName").ifBlank { "$host/$shareName" },
                        host = host,
                        shareName = shareName,
                        subPath = o.optString("subPath", ""),
                        username = o.optString("username", ""),
                        password = o.optString("password", ""),
                        domain = o.optString("domain", "")
                    )
                )
            }
        }
    } catch (_: Exception) {
        emptyList()
    }
}

fun saveSmbShares(context: Context, shares: List<SmbShare>) {
    val array = JSONArray()
    shares.forEach { s ->
        array.put(
            JSONObject().apply {
                put("id", s.id)
                put("displayName", s.displayName)
                put("host", s.host)
                put("shareName", s.shareName)
                put("subPath", s.subPath)
                put("username", s.username)
                put("password", s.password)
                put("domain", s.domain)
            }
        )
    }
    try {
        securePrefs(context).edit().putString(SMB_SHARES_KEY, array.toString()).apply()
    } catch (_: Exception) {
        // Deliberately no plaintext fallback write here — if the encrypted
        // store can't be created for some reason, silently failing to save
        // is the safer failure mode compared to writing credentials back
        // out in the clear.
    }
}

fun addOrUpdateSmbShare(context: Context, share: SmbShare) {
    val current = loadSmbShares(context).toMutableList()
    val idx = current.indexOfFirst { it.id == share.id }
    if (idx >= 0) current[idx] = share else current.add(share)
    saveSmbShares(context, current)
}

fun removeSmbShare(context: Context, shareId: String) {
    saveSmbShares(context, loadSmbShares(context).filterNot { it.id == shareId })
}

// The plain smb:// path CineVault scans — deliberately WITHOUT embedded
// credentials. jcifs-ng is finicky about credentials embedded in the URL
// string (missing-domain edge cases produce confusing auth failures); the
// authenticated CIFSContext built in SmbVideoScanner.kt is passed alongside
// this URL instead, which is the more reliable pattern.
fun SmbShare.rootUrl(): String {
    val cleanSubPath = subPath.trim('/').let { if (it.isBlank()) "" else "$it/" }
    return "smb://$host/$shareName/$cleanSubPath"
}
