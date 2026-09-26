package dev.statup.app.data.local.datastore

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * AES-256-GCM encrypted storage for API tokens (Todoist, Gemini key), keyed by the device's
 * Android Keystore. See [openWithRecovery] for the cloud-restore recovery path.
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

    /**
     * Like [putString] but synchronous, returning whether the write reached disk - needed only by
     * the legacy-secret migration, which must not delete the plaintext against an unlanded write.
     */
    // Not the KTX `edit { }` extension on purpose: it returns Unit, and the whole point here is
    // commit()'s boolean - without it we would delete the plaintext against an unverified write.
    @Suppress("UseKtx")
    suspend fun putStringDurable(key: String, value: String): Boolean = withContext(Dispatchers.IO) {
        runCatching { prefs.edit().putString(key, value).commit() }.getOrDefault(false)
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
 * First failure retries without wiping (transient Keystore flakes must not destroy data);
 * a second failure treats the file as genuinely undecryptable and [onCorrupt] wipes it.
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
