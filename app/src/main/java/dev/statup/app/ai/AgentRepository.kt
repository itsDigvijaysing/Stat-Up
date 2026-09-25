package dev.statup.app.ai

/**
 * Thin orchestrator: build the system instruction (persona + fresh player state),
 * then delegate to the [AgentApi] impl. Owns no state - the ViewModel keeps the transcript.
 *
 * Separated from the ViewModel so the chat flow stays testable without Compose/Lifecycle deps.
 */
class AgentRepository(
    private val agentApi: AgentApi,
    private val contextBuilder: AgentContextBuilder
) {
    suspend fun sendMessage(transcript: List<AgentMessage>): Result<String> {
        val context = contextBuilder.build()
        val systemInstruction = AgentPersona.SYSTEM_PROMPT + "\n\n" + context
        return agentApi.chat(systemInstruction, transcript)
    }
}
