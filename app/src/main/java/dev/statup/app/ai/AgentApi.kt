package dev.statup.app.ai

/**
 * Provider-agnostic chat API. Concrete impls today: [GeminiAgentApi].
 *
 * The interface exists so the rest of the app talks to "an AI" rather than to Gemini
 * specifically - swapping in a different provider (a local on-device model, another
 * cloud LLM, etc.) only requires another impl + a Koin rebind.
 */
interface AgentApi {

    /**
     * Send the full conversation transcript + a system instruction, get the next assistant turn.
     *
     * @param systemInstruction sets the assistant's persona + injects current player-state context.
     * @param transcript ordered list of prior turns (user + assistant). Most-recent last.
     * @return assistant's reply text on success, or a typed failure ([AgentAuthException],
     *         [AgentRateLimitException], or generic [Exception]).
     */
    suspend fun chat(systemInstruction: String, transcript: List<AgentMessage>): Result<String>
}

/** Invalid/missing/expired API key. UI should redirect user back to Settings. */
class AgentAuthException(message: String) : Exception(message)

/** Free-tier quota exhausted or per-minute limit hit. UI should ask user to retry later. */
class AgentRateLimitException(message: String) : Exception(message)

/** Provider returned a non-empty response but it was blocked by safety filters. */
class AgentSafetyException(message: String) : Exception(message)
