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
 * Animechan v1 (`GET /v1/quotes/random`). Free tier is 5 req/hour - the once-a-day cache
 * in QuoteRepository keeps usage far inside that limit.
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
 * ZenQuotes `GET /api/today`. Rate-limited responses come back as HTTP 200 with author
 * "zenquotes.io" - treated as failure here, not shown as the day's quote.
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
