package com.sole.cinevault

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.security.KeyStore

private const val TAG = "SecurePrefs"

// Where the security-crypto library keeps its wrapped encryption keyset.
private const val KEYSET_PREFS = "__androidx_security_crypto_encrypted_prefs__"

/**
 * Opens an encrypted preferences file. If it cannot be opened because the phone's secure key no
 * longer matches the saved data (for example a reinstall whose old data came back from a backup),
 * that data is unreadable by anyone, so it is cleared and recreated instead of crashing the app.
 */
internal fun openSecurePrefs(context: Context, name: String): SharedPreferences {
    val app = context.applicationContext
    return try {
        createSecurePrefs(app, name)
    } catch (e: Exception) {
        Log.w(TAG, "Encrypted prefs '$name' unreadable, resetting", e)
        resetSecureStorage(app, name)
        createSecurePrefs(app, name)
    }
}

private fun createSecurePrefs(app: Context, name: String): SharedPreferences =
    EncryptedSharedPreferences.create(
        app,
        name,
        MasterKey.Builder(app).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )

private fun resetSecureStorage(app: Context, name: String) {
    app.deleteSharedPreferences(name)
    app.deleteSharedPreferences(KEYSET_PREFS)
    runCatching {
        KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
            .deleteEntry(MasterKey.DEFAULT_MASTER_KEY_ALIAS)
    }
}
