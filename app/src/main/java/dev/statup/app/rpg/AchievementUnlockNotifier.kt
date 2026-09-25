package dev.statup.app.rpg

import dev.statup.app.domain.model.Achievement
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow

/**
 * Carries "an achievement just unlocked" from the repository to whatever UI is on screen.
 *
 * A buffered [Channel] rather than a StateFlow: an unlock is a one-shot event that must be
 * delivered exactly once, and it can fire while no screen is collecting (a background Todoist
 * sync, the midnight decay tick). The buffer holds those until the UI comes back, and
 * re-collecting never replays an already-celebrated unlock.
 */
class AchievementUnlockNotifier {
    private val channel = Channel<Achievement>(capacity = Channel.BUFFERED)
    val events: Flow<Achievement> = channel.receiveAsFlow()

    fun notify(achievement: Achievement) {
        channel.trySend(achievement)
    }
}
