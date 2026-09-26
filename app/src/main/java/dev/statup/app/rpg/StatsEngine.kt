package dev.statup.app.rpg

import dev.statup.app.domain.model.PlayerStats

/** A stat value plus the leftover points carried toward the next point. */
data class StatProgress(val stat: Int, val accumulator: Int)

/**
 * Pure math for stat/points calculations. Kept as a class (not `object`) so it stays in
 * Koin and leaves room for future stat math needing DI.
 */
@Suppress("unused")
class StatsEngine {

    companion object {
        const val MOOD_POINTS = 2

        /**
         * Map Todoist's API priority (1=normal … 4=urgent - inverted from the UI p1..p4
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
         * A stat at [PlayerStats.MAX_STAT] freezes: points are discarded rather than computed
         * into a gain the cap then erases (previously an invisible leak).
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
         * Rebuild one stat from lifetime points earned - used by the one-time recompute after
         * the conversion rate changed, equivalent to replaying every earn from a fresh character.
         */
        fun statFromLifetimePoints(lifetimePoints: Int): StatProgress =
            applyPoints(PlayerStats.BASE_STAT, 0, lifetimePoints)
    }
}
