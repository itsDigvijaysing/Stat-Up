package dev.statup.app.ai

import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Gemini Generative Language API client.
 *
 * Endpoint: POST `https://generativelanguage.googleapis.com/v1beta/models/{model}:generateContent?key=API_KEY`
 *
 * Model: `gemini-3.5-flash-lite` (since 2026-08-21).
 *
 * Chosen on measurement, not on the name Google's deprecation 404 happens to suggest.
 * Same prompt, same day: `gemini-3.6-flash` took 4.2s and spent 409 thinking tokens and
 * rate-limited at 5 req/min; `gemini-3.5-flash-lite` took 1.1s, spent ZERO thinking tokens,
 * and took 8 rapid requests without a 429. For a terse coaching reply the lite model is
 * strictly better: 4x faster, no thinking tokens eating maxOutputTokens, higher free quota.
 *
 * `gemini-2.5-flash` is CLOSED TO NEW USERS - it returns
 * `404 "no longer available to new users"` for any API key whose project had not already
 * used it. An existing project keeps working, which makes this trap easy to miss: testing
 * with a grandfathered developer key shows 200 while every real user gets 404. This app is
 * bring-your-own-key, so ALWAYS validate a model with a fresh key/project, never the .env one.
 *
 * When changing MODEL, re-test the whole payload, not just the name - `generationConfig`
 * is not portable across generations. Gemini 3.x rejects the 2.x `thinkingBudget` with
 * 400 INVALID_ARGUMENT and uses `thinkingLevel` instead.
 *
 * Free-tier quota is generous on lite, but rapid-fire sends
 * can legitimately 429 - that is quota, not a bad key, and is deliberately not retried.
 * A transient `503 UNAVAILABLE` is capacity; the shared client's HttpRequestRetry absorbs it.
 * Verify with scripts/verify_gemini_key.sh. See: https://ai.google.dev/gemini-api/docs/models
 *
 * Request shape (simplified):
 * ```
 * { "system_instruction": { "parts": [{ "text": "..." }] },
 *   "contents": [{ "role": "user"|"model", "parts": [{ "text": "..." }] }, ...],
 *   "generationConfig": { "temperature": 0.7, "maxOutputTokens": 1024,
 *     "thinkingConfig": { "thinkingLevel": "low" } } }
 * ```
 *
 * Response shape: `candidates[0].content.parts[0].text` (or `finishReason=SAFETY` if blocked).
 */
class GeminiAgentApi(
    private val httpClient: HttpClient,
    private val apiKeyProvider: suspend () -> String?
) : AgentApi {

    companion object {
        private const val MODEL = "gemini-3.5-flash-lite"
        private const val BASE = "https://generativelanguage.googleapis.com/v1beta/models"

        // STOP = normal completion; MAX_TOKENS = hit our maxOutputTokens cap but the
        // partial reply is still valid (don't error). Everything else in Gemini's v1beta
        // finish-reason taxonomy (SAFETY, RECITATION, BLOCKLIST, PROHIBITED_CONTENT,
        // SPII, OTHER, MALFORMED_FUNCTION_CALL …) means the response was blocked or
        // scrubbed - surface as AgentSafetyException.
        private val BENIGN_FINISH_REASONS = setOf("STOP", "MAX_TOKENS")
    }

    private val json = Json { ignoreUnknownKeys = true; isLenient = true; encodeDefaults = true }

    override suspend fun chat(
        systemInstruction: String,
        transcript: List<AgentMessage>
    ): Result<String> {
        val apiKey = apiKeyProvider()
        if (apiKey.isNullOrBlank()) {
            return Result.failure(AgentAuthException("No Gemini API key. Add one in Settings."))
        }
        return runCatching {
            val request = GeminiRequest(
                systemInstruction = GeminiContent(
                    parts = listOf(GeminiPart(systemInstruction))
                ),
                contents = transcript
                    .filter { !it.isPending }
                    .map {
                        GeminiContent(
                            role = if (it.role == AgentMessage.Role.USER) "user" else "model",
                            parts = listOf(GeminiPart(it.content))
                        )
                    },
                generationConfig = GeminiGenerationConfig(
                    temperature = 0.7,
                    // flash-lite reports 0 thinking tokens with or without this, but pin it
                    // anyway so a future change to the model's default can't silently reintroduce
                    // dynamic thinking - which draws from the SAME maxOutputTokens cap and, on
                    // 3.6-flash, ate 488 of 512 and truncated the reply at MAX_TOKENS.
                    // Gemini 3 rejects the 2.x thinkingBudget with 400 INVALID_ARGUMENT.
                    thinkingConfig = GeminiThinkingConfig(thinkingLevel = "low"),
                    // 1024 leaves headroom if a model variant ever does spend thinking tokens.
                    maxOutputTokens = 1024
                )
            )
            val body = json.encodeToString(GeminiRequest.serializer(), request)

            val response = httpClient.post("$BASE/$MODEL:generateContent") {
                // Send the key as a header, not a query parameter, so it can't leak into
                // proxy / CDN / server access logs the way a ?key=... URL can.
                header("x-goog-api-key", apiKey)
                contentType(ContentType.Application.Json)
                setBody(body)
            }

            when {
                response.status == HttpStatusCode.Unauthorized ||
                    response.status == HttpStatusCode.Forbidden ->
                    throw AgentAuthException("Gemini rejected the API key (HTTP ${response.status.value}).")
                response.status == HttpStatusCode.TooManyRequests ->
                    throw AgentRateLimitException("Gemini rate limit reached. Wait a minute and try again.")
                // 5xx (overloaded / temporary outage) - surface as rate-limit so the UI
                // shows the same friendly "try again later" copy and the user isn't
                // confronted with a raw HTTP code.
                response.status.value in 500..599 ->
                    throw AgentRateLimitException("Gemini is temporarily unavailable (HTTP ${response.status.value}). Try again shortly.")
            }
            if (!response.status.isSuccess()) {
                val text = response.bodyAsText().take(300)
                throw Exception("Gemini error ${response.status.value}: $text")
            }

            val parsed = json.decodeFromString(GeminiResponse.serializer(), response.bodyAsText())
            val candidate = parsed.candidates.firstOrNull()
                ?: throw Exception("Gemini returned no candidates.")

            // Any non-STOP / non-MAX_TOKENS finish reason from Gemini's v1beta safety
            // taxonomy means the response was blocked or scrubbed. MAX_TOKENS is benign
            // (just a truncated reply) and should fall through to the normal text path.
            val finish = candidate.finishReason
            if (finish != null && finish !in BENIGN_FINISH_REASONS) {
                throw AgentSafetyException("Gemini blocked the response ($finish).")
            }

            val text = candidate.content?.parts?.firstOrNull()?.text
                ?: throw Exception("Gemini returned an empty response.")
            text.trim()
        }
    }
}

// --- Wire types ---

@Serializable
private data class GeminiRequest(
    @SerialName("system_instruction")
    val systemInstruction: GeminiContent? = null,
    val contents: List<GeminiContent>,
    val generationConfig: GeminiGenerationConfig? = null
)

@Serializable
private data class GeminiContent(
    val role: String? = null,
    val parts: List<GeminiPart>
)

@Serializable
private data class GeminiPart(val text: String)

@Serializable
private data class GeminiGenerationConfig(
    val temperature: Double = 0.7,
    val maxOutputTokens: Int = 512,
    val thinkingConfig: GeminiThinkingConfig? = null
)

@Serializable
private data class GeminiThinkingConfig(
    /** Gemini 3.x field. The 2.x `thinkingBudget` is rejected with 400 on these models. */
    val thinkingLevel: String
)

@Serializable
private data class GeminiResponse(
    val candidates: List<GeminiCandidate> = emptyList()
)

@Serializable
private data class GeminiCandidate(
    val content: GeminiContent? = null,
    val finishReason: String? = null
)
