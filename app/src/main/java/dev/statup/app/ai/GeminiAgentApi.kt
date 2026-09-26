package dev.statup.app.ai

import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Gemini client. Model pinned to `gemini-3.5-flash-lite` - `gemini-2.5-flash` 404s for any
 * fresh API key (closed to new users) even though a grandfathered dev key still works.
 */
class GeminiAgentApi(
    private val httpClient: HttpClient,
    private val apiKeyProvider: suspend () -> String?
) : AgentApi {

    companion object {
        private const val MODEL = "gemini-3.5-flash-lite"
        private const val BASE = "https://generativelanguage.googleapis.com/v1beta/models"

        // STOP/MAX_TOKENS are benign; every other Gemini finish reason means the response
        // was blocked or scrubbed - surface as AgentSafetyException.
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
                    // Pin thinkingLevel explicitly - dynamic thinking draws from the same
                    // maxOutputTokens cap and has previously truncated replies at MAX_TOKENS.
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
                // 5xx is a temporary outage - surface as rate-limit so the UI shows a
                // friendly "try again later" instead of a raw HTTP code.
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
