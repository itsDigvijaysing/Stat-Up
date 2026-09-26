package dev.statup.app.rpg

import dev.statup.app.domain.model.Rank
import java.time.LocalDate

/**
 * Pure transition rules for cumulative Work Days (extracted from [DecayEngine] for testability).
 * Promotion needs both days AND average stat; demotion is days-only so stat decay never doubles as a demotion.
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
     * Rank a player should hold given only their banked days and stats, ignoring where they are
     * now - lets the one-time recompute jump several ranks in either direction at once.
     */
    fun rankFor(workDays: Int, averageStat: Float): Rank =
        Rank.highestQualified(workDays, averageStat)

    /**
     * Rebuilds Work Days by replaying `+1`/`-1` per day floored at 0 (not `2 * active - span`, so a
     * long early gap can't mortgage later work); stops at [through] (yesterday) - today isn't judged yet.
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
