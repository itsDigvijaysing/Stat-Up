package dev.statup.app.rpg

import dev.statup.app.domain.model.Rank
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Black-box tests for the cumulative Work Day model.
 *
 * Contract:
 *   - Active day: Work Days +1. Idle day: -1, floored at 0.
 *   - Work Days are NEVER reset - promotion keeps the banked total.
 *   - Promotion needs days AND average stat. Demotion looks at days only.
 */
class RankLogicTest {

    @Test fun `promotion needs both requirements - days alone is not enough`() {
        // D needs 7 days and avg stat 6. Six days banked, seventh day earned, but stats are
        // still at base 5.
        val t = RankLogic.applyActiveDay(workDays = 6, currentRank = Rank.E, averageStat = 5f)
        assertTrue("stats below the gate must block promotion", t is RankLogic.Transition.DaysUpdated)
        assertEquals(7, t.workDays)
    }

    @Test fun `promotion needs both requirements - stats alone is not enough`() {
        val t = RankLogic.applyActiveDay(workDays = 2, currentRank = Rank.E, averageStat = 40f)
        assertTrue("days below the gate must block promotion", t is RankLogic.Transition.DaysUpdated)
        assertEquals(3, t.workDays)
    }

    @Test fun `promotion fires when both requirements are met`() {
        val t = RankLogic.applyActiveDay(workDays = 6, currentRank = Rank.E, averageStat = 6f)
        assertTrue(t is RankLogic.Transition.RankUp)
        assertEquals(Rank.D, (t as RankLogic.Transition.RankUp).newRank)
        assertEquals(7, t.workDays)
    }

    @Test fun `work days survive promotion - no reset at the new rank`() {
        // This is the whole point of the model: the day-after-promotion cliff is gone.
        val promoted = RankLogic.applyActiveDay(14, Rank.D, averageStat = 14f)
        assertTrue(promoted is RankLogic.Transition.RankUp)
        assertEquals("banked days carry over", 15, promoted.workDays)

        // One idle day right after promoting is survivable: 14 >= C's 15? No - but it is well
        // above D's 7, so the fall is at most to D, not a cliff to the floor.
        val idle = RankLogic.applyIdleDay(promoted.workDays, Rank.C)
        assertEquals(14, idle.workDays)
        assertTrue(idle is RankLogic.Transition.RankDown)
        assertEquals(Rank.D, (idle as RankLogic.Transition.RankDown).newRank)
    }

    @Test fun `a comfortable margin absorbs idle days without any demotion`() {
        // 40 days banked at B (needs 30): ten idle days in a row and the rank never moves.
        var days = 40
        repeat(10) {
            val t = RankLogic.applyIdleDay(days, Rank.B)
            assertTrue("no demotion while above the requirement", t is RankLogic.Transition.DaysUpdated)
            days = t.workDays
        }
        assertEquals(30, days)
    }

    @Test fun `losing stats never demotes - only days do`() {
        // Sitting at A (60 days) with an average stat far below A's gate of 36.
        val t = RankLogic.applyIdleDay(workDays = 100, currentRank = Rank.A)
        assertTrue("stats are not consulted on the idle path", t is RankLogic.Transition.DaysUpdated)
        assertEquals(Rank.A, Rank.highestByDays(99))
    }

    @Test fun `demotion is reversible by a single active day`() {
        val down = RankLogic.applyIdleDay(workDays = 30, currentRank = Rank.B)
        assertTrue(down is RankLogic.Transition.RankDown)
        assertEquals(29, down.workDays)

        val back = RankLogic.applyActiveDay(down.workDays, Rank.C, averageStat = 30f)
        assertTrue(back is RankLogic.Transition.RankUp)
        assertEquals(Rank.B, (back as RankLogic.Transition.RankUp).newRank)
        assertEquals(30, back.workDays)
    }

    @Test fun `work days floor at zero`() {
        var days = 1
        repeat(5) { days = RankLogic.applyIdleDay(days, Rank.E).workDays }
        assertEquals("never goes negative", 0, days)
    }

    @Test fun `EX is reachable and is the top of the ladder`() {
        val t = RankLogic.applyActiveDay(workDays = 239, currentRank = Rank.S, averageStat = 80f)
        assertTrue(t is RankLogic.Transition.RankUp)
        assertEquals(Rank.EX, (t as RankLogic.Transition.RankUp).newRank)
        assertEquals(240, t.workDays)

        // Nothing above EX: further active days only bank more days.
        val beyond = RankLogic.applyActiveDay(500, Rank.EX, averageStat = 100f)
        assertTrue(beyond is RankLogic.Transition.DaysUpdated)
        assertEquals(501, beyond.workDays)
    }

    @Test fun `rankFor resolves several ranks at once for the recompute`() {
        assertEquals(Rank.E, RankLogic.rankFor(workDays = 0, averageStat = 0f))
        assertEquals(Rank.E, RankLogic.rankFor(workDays = 200, averageStat = 5f))
        assertEquals(Rank.B, RankLogic.rankFor(workDays = 45, averageStat = 30f))
        assertEquals(Rank.EX, RankLogic.rankFor(workDays = 900, averageStat = 95f))
    }

    @Test fun `rank ladder is E through EX ascending`() {
        assertEquals(0, Rank.E.order)
        assertEquals(6, Rank.EX.order)
        assertEquals(7, Rank.entries.size)
        assertEquals(Rank.D, Rank.E.nextRank())
        assertEquals(null, Rank.EX.nextRank())
        assertEquals(null, Rank.E.previousRank())
        assertEquals(Rank.S, Rank.EX.previousRank())
        assertTrue(Rank.S.canRankUp())
        assertTrue(!Rank.EX.canRankUp())
    }

    // ---- Work Day reconstruction (the one-time upgrade) ----

    @Test fun `a perfect run reconstructs to one work day per active day`() {
        val yesterday = LocalDate.now().minusDays(1)
        val days = (0 until 30).map { yesterday.minusDays(it.toLong()) }.toSet()
        assertEquals(30, RankLogic.reconstructWorkDays(days, yesterday))
    }

    @Test fun `idle days between active days subtract`() {
        // Active, idle, idle, active, active over five days ending yesterday: +1 -1 -1 +1 +1.
        val yesterday = LocalDate.now().minusDays(1)
        val start = yesterday.minusDays(4)
        val days = setOf(start, yesterday.minusDays(1), yesterday)
        assertEquals(2, RankLogic.reconstructWorkDays(days, yesterday))
    }

    @Test fun `an early gap cannot mortgage later work`() {
        // The floor at 0 is why this is a day-by-day replay and not `2 * active - span`:
        // 60 idle days followed by 20 active ones must reconstruct to 20, not -20.
        val yesterday = LocalDate.now().minusDays(1)
        val first = yesterday.minusDays(79)
        val active = mutableSetOf(first)
        (0 until 20).forEach { active += yesterday.minusDays(it.toLong()) }
        assertEquals(20, RankLogic.reconstructWorkDays(active, yesterday))
    }

    @Test fun `no history reconstructs to zero`() {
        assertEquals(0, RankLogic.reconstructWorkDays(emptySet(), LocalDate.now()))
    }

    @Test fun `today is not counted - tonight's tick will judge it`() {
        val today = LocalDate.now()
        assertEquals(0, RankLogic.reconstructWorkDays(setOf(today), today.minusDays(1)))
    }
}
