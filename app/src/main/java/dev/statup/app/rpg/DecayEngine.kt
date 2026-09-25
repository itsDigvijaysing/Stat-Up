package dev.statup.app.rpg

import dev.statup.app.data.local.db.dao.DecayLogDao
import dev.statup.app.data.local.db.dao.TransactionDao
import dev.statup.app.data.local.db.entity.DecayLogEntity
import dev.statup.app.domain.model.PlayerStats
import dev.statup.app.domain.model.Rank
import dev.statup.app.domain.model.StatType
import dev.statup.app.widget.StatsWidgetUpdater
import java.time.LocalDate
import java.time.ZoneId

class DecayEngine(
    private val statsStore: DecayStatsStore,
    private val decayLogDao: DecayLogDao,
    private val dayStore: DecayDayStore,
    private val transactor: Transactor,
    private val transactionDao: TransactionDao? = null,
    private val achievementTracker: AchievementTracker? = null,
    private val widgetUpdater: StatsWidgetUpdater? = null
) {
    /**
     * Called at midnight by DecayWorker.
     * Checks if any tasks/points were earned today.
     * If yes: recordSuccessfulDay()
     * If no: applyDecay()
     *
     * Idempotent within a local day - returns [DailyDecayResult.AlreadyApplied] if today's
     * boundary was already processed. Guards against WorkManager retries, manual `runNow`
     * calls during the same day, and overlapping schedules.
     */
    suspend fun applyDailyDecay(): DailyDecayResult {
        val today = LocalDate.now().toString() // yyyy-MM-dd in local zone
        val lastRun = dayStore.getLastDecayDay()
        if (lastRun == today) {
            return DailyDecayResult.AlreadyApplied
        }

        // Yesterday's window is [yesterday 00:00, today 00:00). Both bounds come from LocalDate
        // rather than "today - 24h" so a DST day (23h or 25h long) still maps to exactly one
        // calendar day - otherwise an earn in the shifted hour falls outside the window and
        // costs the user an active day. Robust to WorkManager firing late: even at 02:15 the
        // boundaries are unchanged.
        val zone = ZoneId.systemDefault()
        val todayMidnight = LocalDate.now(zone).atStartOfDay(zone).toInstant().toEpochMilli()
        val yesterdayMidnight = LocalDate.now(zone).minusDays(1)
            .atStartOfDay(zone).toInstant().toEpochMilli()
        val tomorrowMidnight = LocalDate.now(zone).plusDays(1)
            .atStartOfDay(zone).toInstant().toEpochMilli()

        // Read the activity signal and apply the day's mutation inside ONE DB transaction so a
        // concurrent earn / redeem / buy-shield (each its own transaction) can't be clobbered by
        // the full-row stats write below - which would otherwise erase a just-purchased Streak
        // Shield or freshly-earned stat points.
        val dailyResult = transactor.transaction {
            // Authoritative idempotency check, INSIDE the transaction. The DataStore check above is
            // only a cheap pre-filter (and a fallback for days processed by builds that predate this
            // marker); it is written after the transaction commits, so a crash in between used to
            // re-apply decay or burn a second Streak Shield on the retry. This marker commits with
            // the mutation, so that window is closed.
            if (decayLogDao.countByReasonInRange(DAY_MARKER, todayMidnight, tomorrowMidnight) > 0) {
                return@transaction DailyDecayResult.AlreadyApplied
            }

            // Check if any EARN transactions happened yesterday
            val earnedYesterday = transactionDao?.getEarnedInRange(yesterdayMidnight, todayMidnight) ?: 0

            val outcome = if (earnedYesterday > 0) {
                // User was active, record success
                when (val result = recordSuccessfulDay()) {
                    is StreakResult.StreakWithRankUp -> DailyDecayResult.ActiveWithRankUp(result.newRank)
                    is StreakResult.StreakContinued -> DailyDecayResult.ActiveDay(result.newStreak)
                    else -> DailyDecayResult.ActiveDay(0)
                }
            } else {
                // User was idle. A Streak Freeze Shield (if owned) absorbs the idle day as a
                // "rest day": consume one shield, leave stats / streak / Work Days untouched.
                // Otherwise decay applies as usual.
                val stats = statsStore.getStatsOnce()
                if (stats != null && stats.streakShields > 0) {
                    statsStore.updateStats(
                        stats.copy(
                            streakShields = stats.streakShields - 1,
                            updatedAt = System.currentTimeMillis()
                        )
                    )
                    DailyDecayResult.ShieldConsumed(shieldsLeft = stats.streakShields - 1)
                } else {
                    when (val result = applyDecay("daily_idle")) {
                        is DecayResult.DecayWithRankDown -> DailyDecayResult.IdleWithRankDown(result.newRank)
                        is DecayResult.DecayApplied -> DailyDecayResult.IdleDay(result.statsLost)
                        else -> DailyDecayResult.IdleDay(0)
                    }
                }
            }

            // Last write of the transaction: the day is done. Committing this alongside the mutation
            // is the whole point - either both land or neither does.
            decayLogDao.insert(
                DecayLogEntity(
                    idleHours = null,
                    reason = DAY_MARKER,
                    createdAt = System.currentTimeMillis()
                )
            )
            outcome
        }

        // Legacy marker, kept for one reason: a day processed by a build that predates the Room
        // marker has no row in decay_log, and this is the only record that it was handled. It is no
        // longer what guards against double-application - the in-transaction marker above is.
        dayStore.setLastDecayDay(today)

        // Update streak/rank achievements, then push fresh state to any home-screen widgets
        // (rank/streak/balance may have changed).
        achievementTracker?.onStreakUpdated()
        widgetUpdater?.refresh()

        return dailyResult
    }

    suspend fun applyDecay(reason: String = "daily_idle"): DecayResult {
        val stats = statsStore.getStatsOnce() ?: return DecayResult.NoStats

        // Stat loss: 1 point from the SINGLE highest stat above BASE_STAT. At 5 points per stat
        // point an active day earns +2..+4, so one missed day stings but recovers same-day -
        // unlike the old all-six loop, which dug a six-day hole per miss. Ties resolve to enum
        // order (STR first) so the outcome is deterministic.
        val decayed: StatType? = StatType.entries
            .filter { stats.getStat(it) > PlayerStats.BASE_STAT }
            .maxByOrNull { stats.getStat(it) }
        val totalLost = if (decayed == null) 0 else 1

        // Rank-state transition: delegate to RankLogic so the threshold rules are owned by a
        // single tested module. See RankLogicTest for the full truth table.
        val transition = RankLogic.applyIdleDay(stats.workDays, stats.rank)
        val newRank = when (transition) {
            is RankLogic.Transition.RankDown -> transition.newRank
            else -> stats.rank
        }
        val newWorkDays = transition.workDays

        val updatedStats = stats.copy(
            strStat = stats.strStat - if (decayed == StatType.STR) 1 else 0,
            intStat = stats.intStat - if (decayed == StatType.INT) 1 else 0,
            wisStat = stats.wisStat - if (decayed == StatType.WIS) 1 else 0,
            dexStat = stats.dexStat - if (decayed == StatType.DEX) 1 else 0,
            chaStat = stats.chaStat - if (decayed == StatType.CHA) 1 else 0,
            vitStat = stats.vitStat - if (decayed == StatType.VIT) 1 else 0,
            streak = 0,
            rankUpStreakCounter = newWorkDays,
            rank = newRank,
            updatedAt = System.currentTimeMillis()
        )
        statsStore.updateStats(updatedStats)

        if (decayed != null) {
            decayLogDao.insert(
                DecayLogEntity(
                    strLost = if (decayed == StatType.STR) 1 else 0,
                    intLost = if (decayed == StatType.INT) 1 else 0,
                    wisLost = if (decayed == StatType.WIS) 1 else 0,
                    dexLost = if (decayed == StatType.DEX) 1 else 0,
                    chaLost = if (decayed == StatType.CHA) 1 else 0,
                    vitLost = if (decayed == StatType.VIT) 1 else 0,
                    idleHours = null, reason = reason,
                    createdAt = System.currentTimeMillis()
                )
            )
        }

        return when (transition) {
            is RankLogic.Transition.RankDown ->
                DecayResult.DecayWithRankDown(
                    statsLost = totalLost,
                    newRank = newRank,
                    workDays = newWorkDays
                )
            else -> if (totalLost > 0) {
                DecayResult.DecayApplied(statsLost = totalLost, workDays = newWorkDays)
            } else DecayResult.NoDecay
        }
    }

    suspend fun recordSuccessfulDay(): StreakResult {
        val stats = statsStore.getStatsOnce() ?: return StreakResult.NoStats

        val newStreak = stats.streak + 1
        statsStore.updateStreak(newStreak)

        // Delegate the rank decision to RankLogic. Promotion needs the day count AND the
        // average stat, so today's earns (already banked in `stats`) count toward it.
        val transition = RankLogic.applyActiveDay(stats.workDays, stats.rank, stats.averageStat())
        // Work Days are written unconditionally - they survive promotion by design, so unlike
        // the old model there is no "reset to 0 at the new rank" branch.
        statsStore.updateWorkDays(transition.workDays)

        return when (transition) {
            is RankLogic.Transition.RankUp -> {
                statsStore.updateRank(transition.newRank)
                StreakResult.StreakWithRankUp(
                    newStreak = newStreak,
                    newRank = transition.newRank
                )
            }
            else -> StreakResult.StreakContinued(
                newStreak = newStreak,
                workDays = transition.workDays,
                daysToNextRank = stats.rank.nextRank()
                    ?.let { (it.daysRequired - transition.workDays).coerceAtLeast(0) } ?: 0
            )
        }
    }

    companion object {
        /**
         * `decay_log.reason` for the per-day bookkeeping row. Distinct from the decay reasons
         * (`daily_idle`) so a decay-history UI can filter these synthetic rows out.
         */
        const val DAY_MARKER = "day_processed"
    }
}

sealed class DailyDecayResult {
    data class ActiveDay(val streak: Int) : DailyDecayResult()
    data class ActiveWithRankUp(val newRank: Rank) : DailyDecayResult()
    data class IdleDay(val statsLost: Int) : DailyDecayResult()
    data class IdleWithRankDown(val newRank: Rank) : DailyDecayResult()
    /** An idle day absorbed by a Streak Freeze Shield - no decay, streak/Work Days intact. */
    data class ShieldConsumed(val shieldsLeft: Int) : DailyDecayResult()
    /** Today's window was already processed - current call is a no-op (idempotency guard). */
    data object AlreadyApplied : DailyDecayResult()
}

sealed class DecayResult {
    data object NoStats : DecayResult()
    data object NoDecay : DecayResult()
    data class DecayApplied(val statsLost: Int, val workDays: Int) : DecayResult()
    data class DecayWithRankDown(
        val statsLost: Int,
        val newRank: Rank,
        val workDays: Int
    ) : DecayResult()
}

sealed class StreakResult {
    data object NoStats : StreakResult()
    data class StreakContinued(
        val newStreak: Int,
        val workDays: Int,
        val daysToNextRank: Int
    ) : StreakResult()
    data class StreakWithRankUp(
        val newStreak: Int,
        val newRank: Rank
    ) : StreakResult()
}
