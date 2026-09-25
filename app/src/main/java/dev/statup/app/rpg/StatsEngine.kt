package dev.statup.app.rpg

import dev.statup.app.domain.model.PlayerStats

/** A stat value plus the leftover points carried toward the next point. */
data class StatProgress(val stat: Int, val accumulator: Int)

/**
 * Pure math for stat/points calculations. Used to host instance methods that wrapped
 * `PointsRepository.earnPoints` for each earn type (task / manual / mood / mission),
 * but every ViewModel went straight to `PointsRepository.addPoints` instead, so those
 * wrappers were dead code and were removed.
 *
 * Kept as a class (not an `object`) so it can stay in Koin and keep room for future
 * stat math that legitimately needs DI.
 */
@Suppress("unused")
class StatsEngine {

    companion object {
        const val MOOD_POINTS = 2

        /**
         * Map Todoist's API priority (1=normal … 4=urgent — inverted from the UI p1..p4
         * scheme) to reward points. p1 urgent → 4 pts, p4 normal → 1 pt.
         */
        fun calculateTaskPoints(apiPriority: Int): Int = when (apiPriority) {
            4 -> 4
            3 -> 3
            2 -> 2
            1 -> 1
            else -> 1
        }

        /**
         * Add [points] to one stat's accumulator and convert every whole
         * [PlayerStats.POINTS_PER_STAT] into a stat point.
         *
         * A stat already at [PlayerStats.MAX_STAT] freezes: the accumulator is left exactly as
         * it was and the points are discarded rather than being computed into a gain that the
         * cap then erases (a leak that was invisible to the user). Points that would push past
         * the cap drop their remainder for the same reason.
         *
         * Extracted from `PointsRepository` so the conversion rule is unit-testable on the JVM
         * and so the one-time recompute can reuse the exact same arithmetic.
         */
        fun applyPoints(currentStat: Int, currentAccumulator: Int, points: Int): StatProgress {
            if (currentStat >= PlayerStats.MAX_STAT) {
                return StatProgress(PlayerStats.MAX_STAT, currentAccumulator)
            }
            val newAcc = currentAccumulator + points
            val uncapped = currentStat + newAcc / PlayerStats.POINTS_PER_STAT
            return if (uncapped <= PlayerStats.MAX_STAT) {
                StatProgress(uncapped, newAcc % PlayerStats.POINTS_PER_STAT)
            } else {
                StatProgress(PlayerStats.MAX_STAT, 0)
            }
        }

        /**
         * Rebuild one stat from the lifetime points ever earned in it. Used by the one-time
         * recompute after the conversion rate changed — equivalent to replaying every earn
         * through [applyPoints] from a fresh character.
         */
        fun statFromLifetimePoints(lifetimePoints: Int): StatProgress =
            applyPoints(PlayerStats.BASE_STAT, 0, lifetimePoints)
    }
}
