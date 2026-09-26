package dev.statup.app.ui.screen.status

import dev.statup.app.domain.model.Rank
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RankUpNotifierTest {

    /** Reproduces the off-screen rank-up bug: a replay=0 SharedFlow drops an event fired with
     * no collector; the Channel-backed flow buffers it for the next one instead. */
    @Test
    fun `rank-up sent with no active collector is delivered to the next collector`() = runTest {
        val notifier = RankUpNotifier()

        // Rank-up happens while Status is off-composition - nobody is collecting yet.
        notifier.notify(Rank.D)

        // Screen returns and starts collecting; the buffered event must arrive.
        val received = withTimeout(2_000) { notifier.events.first() }

        assertEquals(Rank.D, received)
    }
}
