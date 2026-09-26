package dev.statup.app.data.repository

import dev.statup.app.domain.model.PlayerStats
import kotlinx.coroutines.flow.Flow

/**
 * Narrow read-only slice of player state (username + a stats snapshot) so collaborators like
 * [dev.statup.app.ai.AgentContextBuilder] stay unit-testable without the full [PlayerRepository].
 */
interface PlayerStateProvider {
    val username: Flow<String>
    suspend fun getStatsOnce(): PlayerStats?
}
