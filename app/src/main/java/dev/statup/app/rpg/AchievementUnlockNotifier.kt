package dev.statup.app.rpg

import dev.statup.app.domain.model.Achievement
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow

/**
 * A buffered [Channel], not a StateFlow: an unlock is a one-shot event that must fire exactly once
 * even with no screen collecting (background sync, the midnight decay tick), and must never replay.
 */
class AchievementUnlockNotifier {
    private val channel = Channel<Achievement>(capacity = Channel.BUFFERED)
    val events: Flow<Achievement> = channel.receiveAsFlow()

    fun notify(achievement: Achievement) {
        channel.trySend(achievement)
    }
}
