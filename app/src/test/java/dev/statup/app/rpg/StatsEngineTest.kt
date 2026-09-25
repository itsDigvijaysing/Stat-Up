package dev.statup.app.rpg

import dev.statup.app.domain.model.PlayerStats
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Pure tests for the priority→points table.
 * Todoist inverts: their API `priority=4` is the user-facing "p1 urgent".
 */
class StatsEngineTest {

    @Test fun `p1 urgent maps to 4 points`() {
        assertEquals(4, StatsEngine.calculateTaskPoints(4))
    }

    @Test fun `p2 high maps to 3 points`() {
        assertEquals(3, StatsEngine.calculateTaskPoints(3))
    }

    @Test fun `p3 medium maps to 2 points`() {
        assertEquals(2, StatsEngine.calculateTaskPoints(2))
    }

    @Test fun `p4 normal maps to 1 point`() {
        assertEquals(1, StatsEngine.calculateTaskPoints(1))
    }

    @Test fun `out-of-range priority defaults to 1 point`() {
        assertEquals(1, StatsEngine.calculateTaskPoints(0))
        assertEquals(1, StatsEngine.calculateTaskPoints(5))
        assertEquals(1, StatsEngine.calculateTaskPoints(-1))
    }

    // ---- points -> stat conversion ----

    @Test fun `five points is one stat point`() {
        val result = StatsEngine.applyPoints(currentStat = 5, currentAccumulator = 0, points = 5)
        assertEquals(6, result.stat)
        assertEquals(0, result.accumulator)
    }

    @Test fun `a partial amount carries in the accumulator without moving the stat`() {
        val result = StatsEngine.applyPoints(currentStat = 5, currentAccumulator = 0, points = 4)
        assertEquals("4 of 5 is not a stat point yet", 5, result.stat)
        assertEquals(4, result.accumulator)
    }

    @Test fun `the carried remainder completes the next point`() {
        val first = StatsEngine.applyPoints(5, 0, 4)
        val second = StatsEngine.applyPoints(first.stat, first.accumulator, 3)
        assertEquals("4 + 3 = 7 → one point, 2 carried", 6, second.stat)
        assertEquals(2, second.accumulator)
    }

    @Test fun `a large amount converts several points at once`() {
        val result = StatsEngine.applyPoints(5, 0, 23)
        assertEquals(9, result.stat)
        assertEquals(3, result.accumulator)
    }

    @Test fun `gains clamp at MAX_STAT and drop the overflow`() {
        val result = StatsEngine.applyPoints(currentStat = 98, currentAccumulator = 0, points = 100)
        assertEquals(PlayerStats.MAX_STAT, result.stat)
        assertEquals("nothing is credited past the cap", 0, result.accumulator)
    }

    @Test fun `an already-maxed stat discards points and freezes its accumulator`() {
        val result = StatsEngine.applyPoints(PlayerStats.MAX_STAT, currentAccumulator = 3, points = 50)
        assertEquals(PlayerStats.MAX_STAT, result.stat)
        assertEquals("accumulator untouched, not recomputed", 3, result.accumulator)
    }

    // ---- lifetime rebuild (the one-time recompute) ----

    @Test fun `lifetime points rebuild a stat from base`() {
        assertEquals(PlayerStats.BASE_STAT, StatsEngine.statFromLifetimePoints(0).stat)
        assertEquals(6, StatsEngine.statFromLifetimePoints(5).stat)
        assertEquals(25, StatsEngine.statFromLifetimePoints(100).stat)
        assertEquals(3, StatsEngine.statFromLifetimePoints(103).accumulator)
    }

    @Test fun `a rebuild can never exceed MAX_STAT`() {
        assertEquals(PlayerStats.MAX_STAT, StatsEngine.statFromLifetimePoints(1_000_000).stat)
    }

    @Test fun `replaying earns one at a time matches one lifetime rebuild`() {
        // The recompute must be equivalent to having always used the new rate.
        var stat = PlayerStats.BASE_STAT
        var acc = 0
        repeat(37) {
            val step = StatsEngine.applyPoints(stat, acc, 3)
            stat = step.stat; acc = step.accumulator
        }
        val rebuilt = StatsEngine.statFromLifetimePoints(37 * 3)
        assertEquals(stat, rebuilt.stat)
        assertEquals(acc, rebuilt.accumulator)
    }
}
