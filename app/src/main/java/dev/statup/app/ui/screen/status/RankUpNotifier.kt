package dev.statup.app.ui.screen.status

import dev.statup.app.domain.model.Rank
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow

/**
 * One-shot rank-up event bus. A buffered [Channel], not a SharedFlow: replay=0 would drop an
 * event fired while the Status tab is off-composition, replay=1 would re-fire it on return.
 */
class RankUpNotifier {
    private val _events = Channel<Rank>(capacity = Channel.BUFFERED)
    val events: Flow<Rank> = _events.receiveAsFlow()

    fun notify(rank: Rank) {
        _events.trySend(rank)
    }
}
