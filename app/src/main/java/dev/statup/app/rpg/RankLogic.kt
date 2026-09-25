package dev.statup.app.rpg

import dev.statup.app.domain.model.Rank
import java.time.LocalDate

/**
 * Pure transition rules for the cumulative Work Day model. Extracted from [DecayEngine] so
 * they are testable without the DB / preferences plumbing.
 *
 * Mental model:
 *   - Each active day: Work Days `+1`. Each idle day: `-1`, floored at 0.
 *   - **Work Days are never reset.** Promotion keeps the banked total, so the safety margin
 *     against demotion grows the longer the user works - no day-after-promotion cliff.
 *   - Promotion needs BOTH requirements: `workDays >= rank.daysRequired` and
 *     `averageStat >= rank.statsRequired`.
 *   - Demotion looks at days only ([Rank.highestByDays]). Stat decay must never demote,
 *     or a single missed day would punish twice.
 *
 * Because both directions are a lookup over [Rank], a jump of more than one rank resolves in
 * one step - which is what the one-time stat recompute needs.
 */
object RankLogic {

    sealed class Transition {
        abstract val workDays: Int

        data class DaysUpdated(override val workDays: Int) : Transition()
        data class RankUp(val newRank: Rank, override val workDays: Int) : Transition()
        data class RankDown(val newRank: Rank, override val workDays: Int) : Transition()
    }

    fun applyActiveDay(workDays: Int, currentRank: Rank, averageStat: Float): Transition {
        val next = workDays + 1
        val target = Rank.highestQualified(next, averageStat)
        return if (target.order > currentRank.order) {
            Transition.RankUp(target, next)
        } else {
            Transition.DaysUpdated(next)
        }
    }

    fun applyIdleDay(workDays: Int, currentRank: Rank): Transition {
        val next = (workDays - 1).coerceAtLeast(0)
        val floor = Rank.highestByDays(next)
        return if (floor.order < currentRank.order) {
            Transition.RankDown(floor, next)
        } else {
            Transition.DaysUpdated(next)
        }
    }

    /**
     * Rank a player should hold given their banked days and stats, ignoring where they are
     * now. Used by the one-time recompute, which must be able to move a player several ranks
     * in either direction at once.
     */
    fun rankFor(workDays: Int, averageStat: Float): Rank =
        Rank.highestQualified(workDays, averageStat)

    /**
     * Rebuilds the Work Day counter by replaying this model over a player's real history:
     * `+1` for every day they earned something, `-1` for every day they didn't, floored at 0.
     *
     * Needed because the pre-v4 counter reset to 0 on each promotion and capped at 5, so it
     * holds no recoverable history - reusing it as-is would drop every existing player to rank
     * E no matter how long they had been playing.
     *
     * Replaying day by day is not the same as `2 * active - span`: the floor at 0 means a long
     * early gap cannot mortgage later work. Counting stops at [through] (yesterday), because
     * today has not been judged yet - tonight's tick will count it.
     */
    fun reconstructWorkDays(activeDays: Set<LocalDate>, through: LocalDate): Int {
        val first = activeDays.minOrNull() ?: return 0
        var days = 0
        var cursor = first
        while (!cursor.isAfter(through)) {
            days = if (cursor in activeDays) days + 1 else (days - 1).coerceAtLeast(0)
            cursor = cursor.plusDays(1)
        }
        return days
    }
}
