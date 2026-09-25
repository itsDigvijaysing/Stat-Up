package dev.statup.app.quotes

import dev.statup.app.domain.model.Quote
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * A provider of one quote. Implementations must NEVER throw - network/parse failures come
 * back as `Result.failure` so [QuoteRepository] can fall back to the bundled offline pack.
 */
interface QuoteApi {
    suspend fun fetchQuote(): Result<Quote>
}

/**
 * Animechan v1 - `GET https://api.animechan.io/v1/quotes/random`.
 *
 * Live-verified 2026-06-10. Free tier: **5 requests/hour** (exceeding it earns a 1-hour
 * block), which is why the repository caches the day's quote and never refetches - one
 * request per day per device stays far inside the limit.
 *
 * Response: `{"status":"success","data":{"content","anime":{"name"},"character":{"name"}}}`
 */
class AnimechanApi(private val httpClient: HttpClient) : QuoteApi {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    override suspend fun fetchQuote(): Result<Quote> = runCatching {
        val response = httpClient.get("https://api.animechan.io/v1/quotes/random")
        if (response.status.value != 200) {
            error("Animechan returned HTTP ${response.status.value}")
        }
        val parsed = json.decodeFromString<AnimechanResponse>(response.bodyAsText())
        val data = parsed.data ?: error("Animechan returned no quote data")
        Quote(
            text = data.content.trim(),
            author = data.character?.name ?: "Unknown",
            origin = data.anime?.name,
            attribution = "Animechan"
        )
    }

    @Serializable
    private data class AnimechanResponse(val status: String? = null, val data: AnimechanData? = null)

    @Serializable
    private data class AnimechanData(
        val content: String,
        val anime: AnimechanNamed? = null,
        val character: AnimechanNamed? = null
    )

    @Serializable
    private data class AnimechanNamed(val name: String? = null)
}

/**
 * ZenQuotes - `GET https://zenquotes.io/api/today` (the canonical quote-of-the-day).
 *
 * Live-verified 2026-06-10. Free tier: 5 requests/30s, no key. ZenQuotes **requires a
 * visible attribution link** on the free tier - the returned [Quote.attribution] carries
 * it and the UI renders it.
 *
 * Gotcha: when rate-limited ZenQuotes still answers HTTP 200 with a quote-shaped body
 * whose author is "zenquotes.io" ("Too many requests...") - that is treated as a failure
 * so the rate-limit message is never shown as the day's wisdom.
 */
class ZenQuotesApi(private val httpClient: HttpClient) : QuoteApi {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    override suspend fun fetchQuote(): Result<Quote> = runCatching {
        val response = httpClient.get("https://zenquotes.io/api/today")
        if (response.status.value != 200) {
            error("ZenQuotes returned HTTP ${response.status.value}")
        }
        val parsed = json.decodeFromString<List<ZenQuote>>(response.bodyAsText())
        val q = parsed.firstOrNull() ?: error("ZenQuotes returned an empty list")
        if (q.a.equals("zenquotes.io", ignoreCase = true)) {
            error("ZenQuotes rate limit response")
        }
        Quote(
            text = q.q.trim(),
            author = q.a.trim(),
            origin = null,
            attribution = "ZenQuotes.io"
        )
    }

    @Serializable
    private data class ZenQuote(val q: String, val a: String)
}
