package com.sole.cinevault

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

// NOTE ON SECURITY: passwords are stored in plain SharedPreferences here,
// matching how the rest of this app already stores things (API keys via
// BuildConfig, secret-folder state, etc. — none of it uses
// EncryptedSharedPreferences). Flagging it rather than silently doing
// something different from the rest of the codebase; upgrading everything
// to encrypted storage would be a separate, broader pass if it's ever wanted.
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

private const val SMB_SHARES_PREF = "cinevault_smb_shares"
private const val SMB_SHARES_KEY = "shares"

fun loadSmbShares(context: Context): List<SmbShare> {
    val raw = context.getSharedPreferences(SMB_SHARES_PREF, Context.MODE_PRIVATE)
        .getString(SMB_SHARES_KEY, "[]") ?: "[]"
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
    context.getSharedPreferences(SMB_SHARES_PREF, Context.MODE_PRIVATE)
        .edit()
        .putString(SMB_SHARES_KEY, array.toString())
        .apply()
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
