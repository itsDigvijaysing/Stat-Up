package dev.statup.app.rpg

import dev.statup.app.domain.model.PlayerStats
import dev.statup.app.domain.model.StatType
import java.time.LocalDate

/** Lifetime EARN points per stat. Implemented by `PointsRepository` over a single SQL SUM. */
interface LifetimeStatPointsSource {
    suspend fun lifetimePoints(stat: StatType): Int

    /** Local dates (yyyy-MM-dd) on which anything was earned, ascending. */
    suspend fun activeEarnDays(): List<String>
}

/**
 * Rebuilds every stat from the points the user has already earned, once, after the
 * points-per-stat rate changed. Beta has few installs, so recomputing is both cheaper and
 * more honest than grandfathering two curves.
 *
 * Reads `transactions` and writes only the singleton `player_stats` row: transactions,
 * balance, redemptions and achievements are never touched. `decay_log` is deliberately
 * ignored — the old all-six-stats decay is the bug being fixed and its record is incomplete.
 *
 * Rank is re-derived from the rebuilt stats plus the banked Work Days, and because
 * [RankLogic.rankFor] is a lookup it can move the player several ranks at once.
 */
class StatRecomputer(
    private val statsStore: DecayStatsStore,
    private val lifetimePoints: LifetimeStatPointsSource,
    private val transactor: Transactor
) {
    suspend fun recompute(): RecomputeResult = transactor.transaction {
        val stats = statsStore.getStatsOnce() ?: return@transaction RecomputeResult.NoStats

        val rebuilt = StatType.entries.associateWith {
            StatsEngine.statFromLifetimePoints(lifetimePoints.lifetimePoints(it))
        }
        fun stat(t: StatType) = rebuilt.getValue(t).stat
        fun acc(t: StatType) = rebuilt.getValue(t).accumulator

        val updated = stats.copy(
            strStat = stat(StatType.STR), strPointsAcc = acc(StatType.STR),
            intStat = stat(StatType.INT), intPointsAcc = acc(StatType.INT),
            wisStat = stat(StatType.WIS), wisPointsAcc = acc(StatType.WIS),
            dexStat = stat(StatType.DEX), dexPointsAcc = acc(StatType.DEX),
            chaStat = stat(StatType.CHA), chaPointsAcc = acc(StatType.CHA),
            vitStat = stat(StatType.VIT), vitPointsAcc = acc(StatType.VIT),
            updatedAt = System.currentTimeMillis()
        )
        // The stored counter is the pre-v4 one (0..5, reset on every promotion), so it is
        // discarded and rebuilt from the real earn history. Without this every existing player
        // would land on rank E regardless of how long they had been playing.
        val workDays = RankLogic.reconstructWorkDays(
            activeDays = lifetimePoints.activeEarnDays()
                .mapNotNull { runCatching { LocalDate.parse(it) }.getOrNull() }
                .toSet(),
            through = LocalDate.now().minusDays(1)
        )
        val rebuiltDays = updated.copy(rankUpStreakCounter = workDays)
        val newRank = RankLogic.rankFor(workDays, rebuiltDays.averageStat())
        statsStore.updateStats(rebuiltDays.copy(rank = newRank))

        RecomputeResult.Recomputed(
            previousAverage = stats.averageStat(),
            newAverage = rebuiltDays.averageStat(),
            previousRank = stats.rank.name,
            newRank = newRank.name,
            workDays = workDays
        )
    }

    companion object {
        /** Bump to force one more rebuild for every existing install. */
        const val CURVE_VERSION = 1
    }
}

sealed class RecomputeResult {
    data object NoStats : RecomputeResult()
    data class Recomputed(
        val previousAverage: Float,
        val newAverage: Float,
        val previousRank: String,
        val newRank: String,
        val workDays: Int
    ) : RecomputeResult()
}
