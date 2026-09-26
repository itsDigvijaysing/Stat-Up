package dev.statup.app.ai

/** Provider-agnostic chat API (impl: [GeminiAgentApi]) - swapping providers only needs
 * another impl + a Koin rebind. */
interface AgentApi {

    /** Sends the transcript + system instruction; returns the reply or a typed failure
     * ([AgentAuthException], [AgentRateLimitException]). */
    suspend fun chat(systemInstruction: String, transcript: List<AgentMessage>): Result<String>
}

/** Invalid/missing/expired API key. UI should redirect user back to Settings. */
class AgentAuthException(message: String) : Exception(message)

/** Free-tier quota exhausted or per-minute limit hit. UI should ask user to retry later. */
class AgentRateLimitException(message: String) : Exception(message)

/** Provider returned a non-empty response but it was blocked by safety filters. */
class AgentSafetyException(message: String) : Exception(message)
