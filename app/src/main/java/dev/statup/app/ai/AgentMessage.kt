package dev.statup.app.ai

import java.util.UUID

/** Ephemeral, never persisted. [id] is a process-unique UUID used as the LazyColumn key -
 * do NOT key by [createdAt], which can collide within the same millisecond and crash Compose. */
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
