package dev.statup.app.data.local.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import dev.statup.app.quotes.DailyQuoteStore
import dev.statup.app.data.repository.MissionResetDayStore
import dev.statup.app.rpg.DecayDayStore
import dev.statup.app.rpg.StatUpgradeStore
import dev.statup.app.ui.screen.tutorial.TutorialStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "user_preferences")

/**
 * App preferences. Plain values live in DataStore; secrets (API tokens) are routed
 * through [SecretStorage] (AES-256-GCM encrypted). The first read of a secret
 * migrates any legacy plain-text value out of DataStore and into encrypted storage.
 *
 * Implements [DailyQuoteStore] - the narrow slice QuoteRepository needs (source setting +
 * day-keyed quote cache) - so the repository stays unit-testable without a Context.
 */
class UserPreferences(private val context: Context) :
    DailyQuoteStore,
    DecayDayStore,
    StatUpgradeStore,
    TutorialStore,
    MissionResetDayStore {

    private val secretStorage = SecretStorage(context)

    // In-memory cached secrets for cheap Flow access. Loaded lazily via [loadSecretsIfNeeded].
    private val todoistTokenFlow = MutableStateFlow<String?>(null)
    private val geminiApiKeyFlow = MutableStateFlow<String?>(null)
    @Volatile private var secretsLoaded = false

    private object Keys {
        val USERNAME = stringPreferencesKey("username")
        // Legacy keys - read once by migrateLegacySecret(), then deleted from DataStore.
        val TODOIST_TOKEN_LEGACY = stringPreferencesKey("todoist_token")
        val GEMINI_API_KEY_LEGACY = stringPreferencesKey("gemini_api_key")
        val DEFAULT_STAT = stringPreferencesKey("default_stat")
        val SYNC_INTERVAL_MINUTES = intPreferencesKey("sync_interval_minutes")
        val LAST_SYNC_TIME = longPreferencesKey("last_sync_time")
        val SHOW_DECAY_ANIMATIONS = booleanPreferencesKey("show_decay_animations")
        val HAPTIC_FEEDBACK = booleanPreferencesKey("haptic_feedback")
        // Master switch for the on-device stat classifier. Off means the model is never asked
        // for a guess anywhere - and since the 96 KB blob loads lazily on first use, it is
        // never even read off disk.
        val AUTO_CATEGORISE = booleanPreferencesKey("auto_categorise")
        val ONBOARDING_COMPLETE = booleanPreferencesKey("onboarding_complete")
        // Guided tutorial that runs once after onboarding, before the main shell unlocks.
        val TUTORIAL_COMPLETE = booleanPreferencesKey("tutorial_complete")
        // Which tutorial step the user is on. Persisted because the tour spans several real
        // screens and can easily outlive the process - without this, a restart mid-tour
        // replays the intro and asks for work the user has already done.
        val TUTORIAL_STEP = stringPreferencesKey("tutorial_step")
        // Starter missions/rewards are seeded once and never again - deleting the samples
        // must be permanent, so this is a flag rather than an "is the table empty" check.
        val STARTER_CONTENT_SEEDED = booleanPreferencesKey("starter_content_seeded")
        // Set once the "is this a brand-new install?" question has been answered and the flags
        // above written accordingly. The UI waits on this, so first-run state is never read while
        // it is still being decided - see resolveFirstRunFlags.
        val FIRST_RUN_RESOLVED = booleanPreferencesKey("first_run_resolved")
        // Version of the stat curve the stored stats were built with. Bumping the constant
        // triggers exactly one rebuild of every stat from lifetime points.
        val STAT_CURVE_VERSION = intPreferencesKey("stat_curve_version")
        val HEXAGON_STYLE = stringPreferencesKey("hexagon_style")
        // Local-date string (yyyy-MM-dd) of the most recent successful DecayEngine run.
        // Guards against double-application when WorkManager retries, runNow() fires,
        // or scheduling overlaps the next tick.
        val LAST_DECAY_DAY = stringPreferencesKey("last_decay_day")
        val LAST_MISSION_RESET_DAY = stringPreferencesKey("last_mission_reset_day")
        // Achievement title the user chose to display under their name on the status
        // window. Display preference only (the unlock state lives in the titles table).
        val EQUIPPED_TITLE_ID = stringPreferencesKey("equipped_title_id")
        // Daily Quote feature: chosen source (QuoteSource enum name; default OFFLINE) and
        // the day's cached quote (date + source it was resolved for + the quote as JSON).
        val QUOTE_SOURCE = stringPreferencesKey("quote_source")
        val DAILY_QUOTE_DATE = stringPreferencesKey("daily_quote_date")
        val DAILY_QUOTE_SRC = stringPreferencesKey("daily_quote_src")
        val DAILY_QUOTE_JSON = stringPreferencesKey("daily_quote_json")
    }

    val username: Flow<String> = context.dataStore.data.map { it[Keys.USERNAME] ?: "Player" }
    val defaultStat: Flow<String> = context.dataStore.data.map { it[Keys.DEFAULT_STAT] ?: "INT" }
    val syncIntervalMinutes: Flow<Int> = context.dataStore.data.map { it[Keys.SYNC_INTERVAL_MINUTES] ?: 15 }
    val lastSyncTime: Flow<Long> = context.dataStore.data.map { it[Keys.LAST_SYNC_TIME] ?: 0L }
    val showDecayAnimations: Flow<Boolean> = context.dataStore.data.map { it[Keys.SHOW_DECAY_ANIMATIONS] ?: true }
    val hapticFeedback: Flow<Boolean> = context.dataStore.data.map { it[Keys.HAPTIC_FEEDBACK] ?: true }
    val autoCategorise: Flow<Boolean> = context.dataStore.data.map { it[Keys.AUTO_CATEGORISE] ?: true }
    val onboardingComplete: Flow<Boolean> = context.dataStore.data.map { it[Keys.ONBOARDING_COMPLETE] ?: false }
    val tutorialComplete: Flow<Boolean> = context.dataStore.data.map { it[Keys.TUTORIAL_COMPLETE] ?: false }
    val hexagonStyle: Flow<String> = context.dataStore.data.map { it[Keys.HEXAGON_STYLE] ?: "simple" }
    val lastDecayDay: Flow<String?> = context.dataStore.data.map { it[Keys.LAST_DECAY_DAY] }
    val quoteSource: Flow<String> = context.dataStore.data.map { it[Keys.QUOTE_SOURCE] ?: "OFFLINE" }
    val equippedTitleId: Flow<String?> = context.dataStore.data.map { it[Keys.EQUIPPED_TITLE_ID] }

    // ---- Secrets (encrypted) ----
    // These StateFlows start as null. [loadSecretsIfNeeded] (called from StatUpApp init)
    // populates them with values from EncryptedSharedPreferences. Suspend callers should
    // prefer [getTodoistToken] / [getGeminiApiKey], which guarantee the load has completed.

    val todoistToken: Flow<String?> = todoistTokenFlow.asStateFlow()
    val geminiApiKey: Flow<String?> = geminiApiKeyFlow.asStateFlow()

    /** Eagerly populates the secret flows; safe to call multiple times. */
    suspend fun loadSecretsIfNeeded() {
        if (secretsLoaded) return
        val token = secretStorage.getString(SecretStorage.KEY_TODOIST_TOKEN)
            ?: migrateLegacySecret(Keys.TODOIST_TOKEN_LEGACY, SecretStorage.KEY_TODOIST_TOKEN)
        val gemini = secretStorage.getString(SecretStorage.KEY_GEMINI_API_KEY)
            ?: migrateLegacySecret(Keys.GEMINI_API_KEY_LEGACY, SecretStorage.KEY_GEMINI_API_KEY)
        todoistTokenFlow.value = token
        geminiApiKeyFlow.value = gemini
        secretsLoaded = true
    }

    /** Suspend accessor that ensures secrets are loaded before returning. */
    suspend fun getTodoistToken(): String? {
        loadSecretsIfNeeded()
        return todoistTokenFlow.value
    }

    suspend fun getGeminiApiKey(): String? {
        loadSecretsIfNeeded()
        return geminiApiKeyFlow.value
    }

    /**
     * Moves a plaintext secret out of DataStore and into encrypted storage.
     *
     * Order matters and so does durability: write encrypted, confirm it can be read back, and only
     * then delete the plaintext. The previous version deleted first, so a failure in between
     * destroyed the token outright - and a plain `apply()` write would not even have reported one.
     * The realistic failure is a Keystore flake, which is not hypothetical here:
     * `SecretStorage.openWithRecovery` exists precisely because those happen right after boot, which
     * is exactly when `loadSecretsIfNeeded()` runs.
     *
     * On any failure the plaintext is left in place and returned, so the next launch retries.
     */
    private suspend fun migrateLegacySecret(legacyKey: Preferences.Key<String>, secretKey: String): String? {
        val legacy = context.dataStore.data.first()[legacyKey]
        if (legacy.isNullOrBlank()) return null
        val stored = secretStorage.putStringDurable(secretKey, legacy) &&
            secretStorage.getString(secretKey) == legacy
        if (stored) context.dataStore.edit { it.remove(legacyKey) }
        return legacy
    }

    suspend fun setUsername(username: String) {
        context.dataStore.edit { it[Keys.USERNAME] = username }
    }

    suspend fun setTodoistToken(token: String?) {
        secretStorage.putString(SecretStorage.KEY_TODOIST_TOKEN, token)
        todoistTokenFlow.value = token
    }

    suspend fun setGeminiApiKey(key: String?) {
        secretStorage.putString(SecretStorage.KEY_GEMINI_API_KEY, key)
        geminiApiKeyFlow.value = key
    }

    suspend fun setDefaultStat(stat: String) {
        context.dataStore.edit { it[Keys.DEFAULT_STAT] = stat }
    }

    suspend fun setSyncIntervalMinutes(minutes: Int) {
        context.dataStore.edit { it[Keys.SYNC_INTERVAL_MINUTES] = minutes }
    }

    suspend fun setLastSyncTime(time: Long) {
        context.dataStore.edit { it[Keys.LAST_SYNC_TIME] = time }
    }

    suspend fun setShowDecayAnimations(show: Boolean) {
        context.dataStore.edit { it[Keys.SHOW_DECAY_ANIMATIONS] = show }
    }

    suspend fun setHapticFeedback(enabled: Boolean) {
        context.dataStore.edit { it[Keys.HAPTIC_FEEDBACK] = enabled }
    }

    suspend fun setOnboardingComplete(complete: Boolean) {
        context.dataStore.edit { it[Keys.ONBOARDING_COMPLETE] = complete }
    }

    override suspend fun getTutorialStep(): String? = context.dataStore.data.first()[Keys.TUTORIAL_STEP]

    override suspend fun setTutorialStep(step: String?) {
        context.dataStore.edit {
            if (step == null) it.remove(Keys.TUTORIAL_STEP) else it[Keys.TUTORIAL_STEP] = step
        }
    }

    suspend fun isOnboardingComplete(): Boolean =
        context.dataStore.data.first()[Keys.ONBOARDING_COMPLETE] ?: false

    override suspend fun setTutorialComplete(complete: Boolean) {
        context.dataStore.edit { it[Keys.TUTORIAL_COMPLETE] = complete }
    }

    suspend fun isStarterContentSeeded(): Boolean =
        context.dataStore.data.first()[Keys.STARTER_CONTENT_SEEDED] ?: false

    suspend fun setStarterContentSeeded(seeded: Boolean) {
        context.dataStore.edit { it[Keys.STARTER_CONTENT_SEEDED] = seeded }
    }

    /** Gate the UI waits on before acting on any first-run flag. */
    val firstRunResolved: Flow<Boolean> =
        context.dataStore.data.map { it[Keys.FIRST_RUN_RESOLVED] ?: false }

    /**
     * Decides once, per install, whether this is a brand-new user, and writes the first-run flags to
     * match. Runs before anything reads them.
     *
     * `tutorial_complete` and `starter_content_seeded` both default to `false`, which is
     * indistinguishable from "an install that predates them". Left alone, every updating user is
     * treated as new: 13 sample items appear in their lists and the tutorial tries to start. Worse,
     * the flags were written asynchronously from two different places while the UI was already
     * reading them, so the outcome came down to which coroutine won.
     *
     * [isFreshInstall] comes from `firstInstallTime == lastUpdateTime`. The `onboardingComplete`
     * term covers the one case that gets wrong: someone who installs, never opens the app, then
     * updates and opens it for the first time has diverging timestamps but is genuinely new, and
     * would otherwise be denied the tutorial.
     *
     * Deciding is separate from [markFirstRunResolved] so the caller can seed the starter content in
     * between - the gate opens only once there is something to show.
     */
    suspend fun resolveFirstRunFlags(isFreshInstall: Boolean) {
        context.dataStore.edit { prefs ->
            if (prefs[Keys.FIRST_RUN_RESOLVED] == true) return@edit
            val fresh = FirstRunPolicy.isFirstRun(isFreshInstall, prefs[Keys.ONBOARDING_COMPLETE])
            if (!fresh) {
                prefs[Keys.TUTORIAL_COMPLETE] = true
                prefs[Keys.STARTER_CONTENT_SEEDED] = true
            }
        }
    }

    suspend fun markFirstRunResolved() {
        context.dataStore.edit { it[Keys.FIRST_RUN_RESOLVED] = true }
    }

    /**
     * Post-reset first-run state, written in one edit.
     *
     * A full reset clears every key, including the gate - and it happens in the same process, long
     * after [resolveFirstRunFlags] ran at startup. Without this the UI would sit behind the gate on a
     * blank frame until the user force-quit. Someone who deliberately wipes their data knows the app,
     * so the tour is marked done rather than replayed, and the stat curve is stamped because there is
     * no history left to rebuild from.
     */
    suspend fun markResetComplete(statCurveVersion: Int) {
        context.dataStore.edit {
            it[Keys.STARTER_CONTENT_SEEDED] = true
            it[Keys.TUTORIAL_COMPLETE] = true
            it[Keys.STAT_CURVE_VERSION] = statCurveVersion
            it[Keys.FIRST_RUN_RESOLVED] = true
        }
    }

    override suspend fun getStatCurveVersion(): Int =
        context.dataStore.data.first()[Keys.STAT_CURVE_VERSION] ?: 0

    override suspend fun setStatCurveVersion(version: Int) {
        context.dataStore.edit { it[Keys.STAT_CURVE_VERSION] = version }
    }

    override suspend fun isAutoCategoriseEnabled(): Boolean =
        context.dataStore.data.first()[Keys.AUTO_CATEGORISE] ?: true

    suspend fun setAutoCategorise(enabled: Boolean) {
        context.dataStore.edit { it[Keys.AUTO_CATEGORISE] = enabled }
    }

    suspend fun setHexagonStyle(style: String) {
        context.dataStore.edit { it[Keys.HEXAGON_STYLE] = style }
    }

    override suspend fun getLastDecayDay(): String? = context.dataStore.data.first()[Keys.LAST_DECAY_DAY]

    override suspend fun setLastDecayDay(day: String) {
        context.dataStore.edit { it[Keys.LAST_DECAY_DAY] = day }
    }

    override suspend fun getLastMissionResetDay(): String? = context.dataStore.data.first()[Keys.LAST_MISSION_RESET_DAY]

    override suspend fun setLastMissionResetDay(day: String) {
        context.dataStore.edit { it[Keys.LAST_MISSION_RESET_DAY] = day }
    }

    suspend fun setQuoteSource(source: String) {
        context.dataStore.edit { it[Keys.QUOTE_SOURCE] = source }
    }

    suspend fun setEquippedTitleId(id: String?) {
        context.dataStore.edit {
            if (id == null) it.remove(Keys.EQUIPPED_TITLE_ID) else it[Keys.EQUIPPED_TITLE_ID] = id
        }
    }

    // ---- DailyQuoteStore ----

    override suspend fun getQuoteSource(): String =
        context.dataStore.data.first()[Keys.QUOTE_SOURCE] ?: "OFFLINE"

    override suspend fun getCachedQuote(date: String, source: String): String? {
        val prefs = context.dataStore.data.first()
        return prefs[Keys.DAILY_QUOTE_JSON]?.takeIf {
            prefs[Keys.DAILY_QUOTE_DATE] == date && prefs[Keys.DAILY_QUOTE_SRC] == source
        }
    }

    override suspend fun setCachedQuote(date: String, source: String, quoteJson: String) {
        context.dataStore.edit {
            it[Keys.DAILY_QUOTE_DATE] = date
            it[Keys.DAILY_QUOTE_SRC] = source
            it[Keys.DAILY_QUOTE_JSON] = quoteJson
        }
    }

    suspend fun clearAll() {
        context.dataStore.edit { it.clear() }
        secretStorage.clear()
        todoistTokenFlow.value = null
        geminiApiKeyFlow.value = null
    }
}
