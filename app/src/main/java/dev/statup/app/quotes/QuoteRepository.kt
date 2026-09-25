package dev.statup.app.quotes

import dev.statup.app.domain.model.Quote
import dev.statup.app.domain.model.QuoteSource
import kotlinx.serialization.json.Json
import java.time.LocalDate

/**
 * Narrow persistence slice for the daily quote (implemented by UserPreferences; faked in
 * unit tests - same pattern as PlayerStateProvider).
 */
interface DailyQuoteStore {
    /** The user's chosen [QuoteSource] name (Settings). Defaults to OFFLINE. */
    suspend fun getQuoteSource(): String

    /** The cached quote for [date]+[source], or null. */
    suspend fun getCachedQuote(date: String, source: String): String?

    suspend fun setCachedQuote(date: String, source: String, quoteJson: String)
}

/**
 * Resolves "today's quote" exactly once per local day per source setting.
 *
 * Resolution order:
 *  1. Cache hit for (today, current source) → return it. No network. This is what keeps
 *     Animechan's tight 5 req/HOUR free tier comfortable: at most one request per day,
 *     and only when the user has opted into an online source.
 *  2. OFFLINE source → deterministic pick from the bundled pack (no network ever).
 *  3. ANIME / MOTIVATION → fetch from the API; any failure falls back to the bundled
 *     pack so the card never shows an error or blank.
 *  4. MIXED → alternates anime/motivation by epoch day parity.
 *
 * Changing the source in Settings changes the cache key, so the card updates immediately
 * without waiting for midnight.
 */
class QuoteRepository(
    private val store: DailyQuoteStore,
    private val animeApi: QuoteApi,
    private val motivationApi: QuoteApi,
    private val offlinePack: QuotePack
) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun getDailyQuote(today: LocalDate = LocalDate.now()): Quote {
        val source = QuoteSource.fromName(store.getQuoteSource())
        val dateKey = today.toString()

        store.getCachedQuote(dateKey, source.name)?.let { cached ->
            runCatching { return json.decodeFromString<Quote>(cached) }
            // Corrupt cache entry → fall through and re-resolve.
        }

        val epochDay = today.toEpochDay()
        val wantAnime = when (source) {
            QuoteSource.ANIME -> true
            QuoteSource.MOTIVATION -> false
            // MIXED and OFFLINE alternate flavours day by day.
            QuoteSource.MIXED, QuoteSource.OFFLINE -> epochDay % 2 == 0L
        }

        val quote = when (source) {
            QuoteSource.OFFLINE -> offlinePack.quoteForDay(epochDay, wantAnime)
            else -> {
                val api = if (wantAnime) animeApi else motivationApi
                api.fetchQuote().getOrElse { offlinePack.quoteForDay(epochDay, wantAnime) }
            }
        }

        store.setCachedQuote(dateKey, source.name, json.encodeToString(Quote.serializer(), quote))
        return quote
    }
}
