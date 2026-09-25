package dev.statup.app.rpg

import dev.statup.app.data.local.db.dao.DecayLogDao
import dev.statup.app.data.local.db.dao.TransactionDao
import dev.statup.app.data.local.db.entity.DecayLogEntity
import dev.statup.app.data.local.db.entity.TransactionEntity
import dev.statup.app.domain.model.PlayerStats
import dev.statup.app.domain.model.Rank
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * JVM tests for [DecayEngine.applyDailyDecay] - the daily-tick heart. Uses hand-written fakes for
 * the narrow ports (no Android / Room), matching the project's test style. The atomicity wrapper
 * is exercised via a pass-through [Transactor]; the read-modify-write logic and the idempotency
 * gate are what's under test here.
 */
class DecayEngineTest {

    private fun engine(
        statsStore: FakeStatsStore,
        dayStore: FakeDayStore,
        logDao: FakeDecayLogDao = FakeDecayLogDao(),
        earnedYesterday: Int = 0,
    ) = DecayEngine(
        statsStore = statsStore,
        decayLogDao = logDao,
        dayStore = dayStore,
        transactor = ImmediateTransactor,
        transactionDao = FakeTransactionDao(earnedYesterday),
        achievementTracker = null,
        widgetUpdater = null,
    )

    @Test
    fun `already applied today is a no-op`() = runTest {
        val stats = FakeStatsStore(PlayerStats(strStat = 50))
        val result = engine(stats, FakeDayStore(lastDay = LocalDate.now().toString())).applyDailyDecay()

        assertTrue(result is DailyDecayResult.AlreadyApplied)
        assertTrue("no stat writes on a no-op", stats.updatedStats.isEmpty())
    }

    @Test
    fun `a handled day writes its marker inside the transaction`() = runTest {
        val log = FakeDecayLogDao()
        engine(FakeStatsStore(PlayerStats(strStat = 50)), FakeDayStore(), log).applyDailyDecay()

        assertEquals("exactly one bookkeeping row for the day", 1, log.markerRows.size)
    }

    /**
     * The crash window: the old guard was a DataStore key written *after* the transaction committed,
     * so a process death in between meant the next run saw no marker and applied the day twice -
     * a second stat point lost, or a second Streak Shield burned. The Room marker commits with the
     * mutation, so an empty DataStore key is no longer enough to re-run it.
     */
    @Test
    fun `a second run with no DataStore marker is still a no-op`() = runTest {
        val stats = FakeStatsStore(PlayerStats(strStat = 50))
        val log = FakeDecayLogDao()
        engine(stats, FakeDayStore(), log).applyDailyDecay()
        val writesAfterFirstRun = stats.updatedStats.size

        // Fresh day store = the marker write that never landed.
        val second = engine(stats, FakeDayStore(), log).applyDailyDecay()

        assertTrue("the Room marker catches it", second is DailyDecayResult.AlreadyApplied)
        assertEquals("no second mutation", writesAfterFirstRun, stats.updatedStats.size)
        assertEquals("and no second marker row", 1, log.markerRows.size)
    }

    @Test
    fun `an active day is marked too, not just a decayed one`() = runTest {
        val log = FakeDecayLogDao()
        engine(
            FakeStatsStore(PlayerStats(strStat = 50)),
            FakeDayStore(),
            log,
            earnedYesterday = 12
        ).applyDailyDecay()

        assertEquals("otherwise an active day could be replayed", 1, log.markerRows.size)
        assertEquals("and it is not a decay row", 0, log.decayRows.size)
    }

    @Test
    fun `idle day with a shield consumes one shield and skips decay`() = runTest {
        val stats = FakeStatsStore(PlayerStats(strStat = 50, streakShields = 2, rankUpStreakCounter = 20))
        val day = FakeDayStore()

        val result = engine(stats, day, earnedYesterday = 0).applyDailyDecay()

        assertEquals(DailyDecayResult.ShieldConsumed(shieldsLeft = 1), result)
        assertEquals("shield decremented", 1, stats.stats!!.streakShields)
        assertEquals("stat NOT decayed", 50, stats.stats!!.strStat)
        assertEquals("work days untouched", 20, stats.stats!!.workDays)
        assertEquals("idempotency marker advanced", LocalDate.now().toString(), day.lastDay)
    }

    @Test
    fun `idle day takes one point from the single highest stat only`() = runTest {
        // INT is the highest; every other stat above base must be left alone. The old model
        // took a point from all six at once, which dug a six-day hole per missed day.
        val stats = FakeStatsStore(
            PlayerStats(
                strStat = 20, intStat = 31, wisStat = 30, dexStat = 12,
                chaStat = 9, vitStat = 7, rank = Rank.D, rankUpStreakCounter = 15
            )
        )
        val log = FakeDecayLogDao()

        val result = engine(stats, FakeDayStore(), log, earnedYesterday = 0).applyDailyDecay()

        assertEquals(DailyDecayResult.IdleDay(1), result)
        val after = stats.stats!!
        assertEquals("highest stat decremented", 30, after.intStat)
        assertEquals("others untouched", 20, after.strStat)
        assertEquals("others untouched", 30, after.wisStat)
        assertEquals("others untouched", 12, after.dexStat)
        assertEquals("others untouched", 9, after.chaStat)
        assertEquals("others untouched", 7, after.vitStat)
        assertEquals("one work day lost", 14, after.workDays)
        assertEquals("streak reset on idle decay", 0, after.streak)
        assertEquals("decay row written", 1, log.decayRows.size)
        assertEquals("row attributes the loss to INT", 1, log.lastDecayRow!!.intLost)
        assertEquals(0, log.lastDecayRow!!.wisLost)
    }

    @Test
    fun `decay floors at base - no loss and no log row when already at base`() = runTest {
        val stats = FakeStatsStore(PlayerStats(rank = Rank.D, rankUpStreakCounter = 15)) // all at BASE_STAT
        val log = FakeDecayLogDao()

        val result = engine(stats, FakeDayStore(), log, earnedYesterday = 0).applyDailyDecay()

        assertEquals(DailyDecayResult.IdleDay(0), result)
        assertEquals("no stat falls below base", PlayerStats.BASE_STAT, stats.stats!!.strStat)
        assertEquals("nothing lost → no decay row", 0, log.decayRows.size)
        assertEquals("the work day is still lost", 14, stats.stats!!.workDays)
    }

    @Test
    fun `idle day that drops below the rank requirement demotes`() = runTest {
        // B needs 30 work days. Sitting exactly on the line, one miss falls to C.
        val stats = FakeStatsStore(PlayerStats(strStat = 40, rank = Rank.B, rankUpStreakCounter = 30))

        val result = engine(stats, FakeDayStore(), earnedYesterday = 0).applyDailyDecay()

        assertEquals(DailyDecayResult.IdleWithRankDown(Rank.C), result)
        assertEquals(Rank.C, stats.stats!!.rank)
        assertEquals(29, stats.stats!!.workDays)
    }

    @Test
    fun `active day increments streak and banks a work day`() = runTest {
        // Average stat is still at base 5, below D's gate of 6, so no promotion yet.
        val stats = FakeStatsStore(PlayerStats(streak = 3, rank = Rank.E, rankUpStreakCounter = 10))
        val day = FakeDayStore()

        val result = engine(stats, day, earnedYesterday = 4).applyDailyDecay()

        assertTrue(result is DailyDecayResult.ActiveDay)
        assertEquals("streak +1", 4, stats.lastStreak)
        assertEquals("work day banked", 11, stats.lastWorkDays)
        assertEquals("no promotion while the stat gate is unmet", null, stats.lastRank)
        assertEquals("idempotency marker advanced", LocalDate.now().toString(), day.lastDay)
    }

    @Test
    fun `active day promotes when both gates are met and keeps the banked days`() = runTest {
        // Six stats at 10 → average 10, above C's gate of 14? No. Above D's 6? Yes.
        val stats = FakeStatsStore(
            PlayerStats(
                strStat = 10, intStat = 10, wisStat = 10, dexStat = 10, chaStat = 10, vitStat = 10,
                streak = 5, rank = Rank.E, rankUpStreakCounter = 6
            )
        )

        val result = engine(stats, FakeDayStore(), earnedYesterday = 4).applyDailyDecay()

        assertEquals(DailyDecayResult.ActiveWithRankUp(Rank.D), result)
        assertEquals(Rank.D, stats.lastRank)
        assertEquals("promotion does NOT reset the counter", 7, stats.lastWorkDays)
    }

    // ---- Fakes ----

    private class FakeStatsStore(var stats: PlayerStats?) : DecayStatsStore {
        val updatedStats = mutableListOf<PlayerStats>()
        var lastStreak: Int? = null
        var lastRank: Rank? = null
        var lastWorkDays: Int? = null
        override suspend fun getStatsOnce(): PlayerStats? = stats
        override suspend fun updateStats(stats: PlayerStats) {
            this.stats = stats
            updatedStats.add(stats)
        }
        override suspend fun updateStreak(streak: Int) { lastStreak = streak }
        override suspend fun updateRank(rank: Rank) {
            lastRank = rank
            stats = stats?.copy(rank = rank)
        }
        override suspend fun updateWorkDays(workDays: Int) {
            lastWorkDays = workDays
            stats = stats?.copy(rankUpStreakCounter = workDays)
        }
    }

    private class FakeDayStore(var lastDay: String? = null) : DecayDayStore {
        override suspend fun getLastDecayDay(): String? = lastDay
        override suspend fun setLastDecayDay(day: String) { lastDay = day }
    }

    private object ImmediateTransactor : Transactor {
        override suspend fun <R> transaction(block: suspend () -> R): R = block()
    }

    /**
     * Stores rows so the in-transaction day marker actually works. The engine now writes a synthetic
     * `day_processed` row per handled day alongside any decay row, so the two are kept apart here -
     * [decayRows] is what the loss assertions care about.
     */
    private class FakeDecayLogDao : DecayLogDao {
        val rows = mutableListOf<DecayLogEntity>()
        val decayRows get() = rows.filter { it.reason != DecayEngine.DAY_MARKER }
        val markerRows get() = rows.filter { it.reason == DecayEngine.DAY_MARKER }
        val lastDecayRow get() = decayRows.lastOrNull()

        override fun getAll(): Flow<List<DecayLogEntity>> = error("unused")
        override fun getRecent(limit: Int): Flow<List<DecayLogEntity>> = error("unused")
        override suspend fun countByReasonInRange(reason: String, start: Long, end: Long): Int =
            rows.count { it.reason == reason && it.createdAt >= start && it.createdAt < end }
        override suspend fun insert(log: DecayLogEntity): Long {
            rows.add(log); return rows.size.toLong()
        }
        override suspend fun deleteAll() = error("unused")
    }

    private class FakeTransactionDao(private val earned: Int) : TransactionDao {
        override suspend fun getEarnedInRange(startTime: Long, endTime: Long): Int? = earned
        override fun getAll(): Flow<List<TransactionEntity>> = error("unused")
        override fun getRecent(limit: Int): Flow<List<TransactionEntity>> = error("unused")
        override fun getByType(type: String): Flow<List<TransactionEntity>> = error("unused")
        override fun getRecentByType(type: String, limit: Int): Flow<List<TransactionEntity>> = error("unused")
        override fun getByStatType(statType: String): Flow<List<TransactionEntity>> = error("unused")
        override fun getByDateRange(startTime: Long, endTime: Long): Flow<List<TransactionEntity>> = error("unused")
        override suspend fun getByExternalId(externalId: String): TransactionEntity? = error("unused")
        override suspend fun getActiveEarnDays(): List<String> = error("unused")
        override suspend fun countByDescription(description: String): Int = error("unused")
        override suspend fun countMissionAwards(missionId: String): Int = error("unused")
        override suspend fun deleteByDescriptionPrefix(prefix: String) = error("unused")
        override suspend fun getLifetimePointsForStat(statType: String): Int = error("unused")
        override suspend fun getUncategorisedEarns(): List<TransactionEntity> = error("unused")
        override suspend fun assignStatTypeIfMissing(id: Long, statType: String): Int = error("unused")
        override fun countUncategorisedEarns(): Flow<Int> = error("unused")
        override suspend fun getTotalEarned(): Int? = error("unused")
        override suspend fun getTotalRedeemed(): Int? = error("unused")
        override fun getBalance(): Flow<Int> = error("unused")
        override fun observeEarnedInRange(startTime: Long, endTime: Long): Flow<Int> = error("unused")
        override suspend fun insert(transaction: TransactionEntity): Long = error("unused")
        override suspend fun insertIgnore(transaction: TransactionEntity): Long = error("unused")
        override suspend fun delete(transaction: TransactionEntity) = error("unused")
        override suspend fun getTaskTransactionCount(): Int = error("unused")
        override fun countBySourceInRange(source: String, startTime: Long, endTime: Long): Flow<Int> = error("unused")
        override suspend fun deleteAll() = error("unused")
    }
}
