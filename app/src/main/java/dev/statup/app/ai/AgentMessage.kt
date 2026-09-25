package dev.statup.app.ai

import java.util.UUID

/**
 * In-memory chat message. Ephemeral - never written to the DB (the user opted out of
 * conversation persistence). The [role] follows Gemini's vocabulary: "user" for the
 * human, "model" for the assistant.
 *
 * [id] is a process-unique UUID used as the stable LazyColumn key. Do NOT key by
 * [createdAt] - two messages enqueued in the same millisecond will collide and crash
 * Compose with "Key X was already used".
 */
data class AgentMessage(
    val role: Role,
    val content: String,
    val createdAt: Long = System.currentTimeMillis(),
    /** Tags an in-progress assistant turn so the UI can show a typing indicator. */
    val isPending: Boolean = false,
    val id: String = UUID.randomUUID().toString()
) {
    enum class Role { USER, MODEL }
}
