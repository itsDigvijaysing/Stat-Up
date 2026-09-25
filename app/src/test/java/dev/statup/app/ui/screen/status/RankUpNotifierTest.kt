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

    /**
     * Reproduces the off-screen rank-up bug: a rank-up that fires while the Status screen
     * is not composed (no collector) must still reach the next collector exactly once when
     * the screen returns. A replay=0 SharedFlow drops it; a Channel-backed flow buffers it.
     */
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
