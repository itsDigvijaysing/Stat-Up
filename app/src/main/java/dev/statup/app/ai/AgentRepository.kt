package dev.statup.app.ai

/** Thin orchestrator: builds the system instruction, delegates to [AgentApi]. Owns no state. */
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
