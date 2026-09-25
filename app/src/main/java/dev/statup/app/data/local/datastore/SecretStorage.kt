package dev.statup.app.data.local.datastore

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * AES-256-GCM encrypted storage for API tokens (Todoist, Gemini key).
 * Backed by AndroidX Security `EncryptedSharedPreferences`; keys are protected by the
 * device's Android Keystore. All read/write APIs are suspending and dispatched on IO.
 *
 * Restore resilience: the master key lives in the device-bound AndroidKeyStore and is never
 * backed up, so after a cloud restore to a new device the encrypted blobs are undecryptable
 * and `EncryptedSharedPreferences.create`/reads can throw. [openWithRecovery] retries once
 * (transient Keystore flakes must not destroy data), then wipes the unreadable prefs file
 * and recreates it so the app still launches cleanly; the user just re-enters their
 * token/key. (The blobs are also excluded from auto-backup - see res/xml/backup_rules.xml -
 * so this is a belt-and-braces guard.)
 */
class SecretStorage(context: Context) {

    private val appContext = context.applicationContext

    private val prefs: SharedPreferences by lazy {
        openWithRecovery(
            open = ::openEncryptedPrefs,
            onCorrupt = { appContext.deleteSharedPreferences(PREFS_NAME) }
        )
    }

    private fun openEncryptedPrefs(): SharedPreferences {
        val masterKey = MasterKey.Builder(appContext)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        return EncryptedSharedPreferences.create(
            appContext,
            PREFS_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    suspend fun getString(key: String): String? = withContext(Dispatchers.IO) {
        // Guard the read too: a partially-corrupt entry shouldn't crash callers.
        runCatching { prefs.getString(key, null) }.getOrNull()
    }

    suspend fun putString(key: String, value: String?) = withContext(Dispatchers.IO) {
        prefs.edit().apply {
            if (value == null) remove(key) else putString(key, value)
        }.apply()
    }

    suspend fun clear() = withContext(Dispatchers.IO) {
        prefs.edit().clear().apply()
    }

    companion object {
        private const val PREFS_NAME = "secret_prefs"
        const val KEY_TODOIST_TOKEN = "todoist_token"
        const val KEY_GEMINI_API_KEY = "gemini_api_key"
    }
}

/**
 * Open a resource, escalating through two recovery stages:
 *
 *  1. First failure → plain retry, NO wipe. The Android Keystore is known to fail
 *     transiently (right after boot, device momentarily locked, intermittent
 *     `KeyStoreException`); destroying the user's stored tokens over a one-off flake
 *     would be worse than the corruption we're guarding against.
 *  2. Retry also fails → treat the file as genuinely undecryptable (e.g. restored to a
 *     new device whose Keystore lacks the master key): [onCorrupt] wipes it, then one
 *     final open recreates it fresh.
 *  3. The post-wipe open failing too propagates (genuinely unrecoverable).
 */
internal fun <T> openWithRecovery(open: () -> T, onCorrupt: () -> Unit): T {
    repeat(2) {
        try {
            return open()
        } catch (_: Exception) {
            // fall through to the next stage
        }
    }
    onCorrupt()
    return open()
}
