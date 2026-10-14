package dev.statup.app.rpg

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

/** Contract: a mission's streak counts consecutive local days it was completed, and reads as 0
 * once a whole day passes without a completion. */
class MissionStreakTest {

    private val today = LocalDate.of(2026, 10, 15)

    @Test fun `completing the day after the last completion extends the run`() {
        assertEquals(4, MissionStreak.afterCompletion(3, today.minusDays(1), today))
    }

    @Test fun `completing after a gap restarts the run at one`() {
        assertEquals(1, MissionStreak.afterCompletion(9, today.minusDays(3), today))
    }

    @Test fun `first ever completion starts at one`() {
        assertEquals(1, MissionStreak.afterCompletion(0, null, today))
    }

    @Test fun `a second completion on the same day does not double count`() {
        assertEquals(3, MissionStreak.afterCompletion(3, today, today))
        assertEquals(1, MissionStreak.afterCompletion(0, today, today))
    }

    @Test fun `the run is still alive while yesterday's completion stands`() {
        assertEquals(5, MissionStreak.current(5, today.minusDays(1), today))
        assertEquals(5, MissionStreak.current(5, today, today))
    }

    @Test fun `the run reads as broken once a full day is missed`() {
        // Checked at read time, so a stale count shows 0 even if no reset job ever ran.
        assertEquals(0, MissionStreak.current(12, today.minusDays(2), today))
        assertEquals(0, MissionStreak.current(12, null, today))
    }

    @Test fun `rebuild counts back from today`() {
        val days = listOf(today, today.minusDays(1), today.minusDays(2), today.minusDays(5))
        assertEquals(3, MissionStreak.fromCompletionDays(days, today))
    }

    @Test fun `rebuild counts back from yesterday when today is not done yet`() {
        val days = listOf(today.minusDays(1), today.minusDays(2), today.minusDays(4))
        assertEquals(2, MissionStreak.fromCompletionDays(days, today))
    }

    @Test fun `rebuild of a broken run is zero`() {
        // The exact bug report: completed on scattered days, never on consecutive ones lately.
        val days = listOf(today.minusDays(2), today.minusDays(4), today.minusDays(6))
        assertEquals(0, MissionStreak.fromCompletionDays(days, today))
        assertEquals(0, MissionStreak.fromCompletionDays(emptyList(), today))
    }

    @Test fun `rebuild ignores duplicate completions on the same day`() {
        val days = listOf(today, today, today.minusDays(1), today.minusDays(1))
        assertEquals(2, MissionStreak.fromCompletionDays(days, today))
    }
}
