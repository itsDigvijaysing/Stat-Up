package dev.statup.app.rpg

import dev.statup.app.domain.model.PlayerStats
import dev.statup.app.domain.model.Rank

/**
 * Narrow read/write surface [DecayEngine] needs, implemented by [dev.statup.app.data.repository.PlayerRepository];
 * depending on this interface lets DecayEngine be JVM-unit-tested with a hand-written fake.
 */
interface DecayStatsStore {
    suspend fun getStatsOnce(): PlayerStats?
    suspend fun updateStats(stats: PlayerStats)
    suspend fun updateStreak(streak: Int)
    suspend fun updateRank(rank: Rank)
    /** Persists the cumulative Work Day counter (never reset on promotion). */
    suspend fun updateWorkDays(workDays: Int)
}

/** Persisted "last decay applied" local-day marker. Implemented by UserPreferences (DataStore). */
interface DecayDayStore {
    suspend fun getLastDecayDay(): String?
    suspend fun setLastDecayDay(day: String)
}

/**
 * Runs [block] atomically so the daily tick's read-modify-write can't clobber a concurrent
 * earn/redeem/buy-shield with a stale full-row write.
 */
interface Transactor {
    suspend fun <R> transaction(block: suspend () -> R): R
}
