package dev.statup.app.rpg

import dev.statup.app.domain.model.PlayerStats
import dev.statup.app.domain.model.Rank
import dev.statup.app.domain.model.StatType
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The one-time rebuild after the points-per-stat rate changed. The contract that matters is
 * that it derives stats purely from lifetime earns and never touches anything else.
 */
class StatRecomputerTest {

    @Test
    fun `known lifetime points land on the expected stats`() = runTest {
        val store = FakeStatsStore(PlayerStats(strStat = 9, intStat = 9))
        val recomputer = StatRecomputer(store, FakePoints(str = 100, int = 53), ImmediateTransactor)

        recomputer.recompute()

        val after = store.stats!!
        assertEquals("BASE + 100/5", 25, after.strStat)
        assertEquals(0, after.strPointsAcc)
        assertEquals("BASE + 53/5", 15, after.intStat)
        assertEquals("53 % 5 carried", 3, after.intPointsAcc)
        assertEquals("a stat with no history returns to base", PlayerStats.BASE_STAT, after.wisStat)
    }

    @Test
    fun `rank is re-derived and can jump several ranks at once`() = runTest {
        // 45 consecutive active days, every stat rebuilt to 25 → clears B (30 days / 24 avg)
        // but not A (60 days), from a stored rank of E.
        val store = FakeStatsStore(PlayerStats(rank = Rank.E, rankUpStreakCounter = 3))
        val points = FakePoints(
            str = 100, int = 100, wis = 100, dex = 100, cha = 100, vit = 100,
            days = consecutiveDaysEndingYesterday(45)
        )

        val result = StatRecomputer(store, points, ImmediateTransactor).recompute()

        assertEquals(Rank.B, store.stats!!.rank)
        assertTrue(result is RecomputeResult.Recomputed)
        assertEquals("E", (result as RecomputeResult.Recomputed).previousRank)
        assertEquals("B", result.newRank)
    }

    @Test
    fun `stats alone cannot carry a rank without the work days`() = runTest {
        val store = FakeStatsStore(PlayerStats(rank = Rank.S, rankUpStreakCounter = 5))
        val points = FakePoints(
            str = 500, int = 500, wis = 500, dex = 500, cha = 500, vit = 500,
            days = consecutiveDaysEndingYesterday(3)
        )

        StatRecomputer(store, points, ImmediateTransactor).recompute()

        assertEquals("three work days can only support E", Rank.E, store.stats!!.rank)
    }

    @Test
    fun `everything that is not a stat is left alone`() = runTest {
        val original = PlayerStats(
            totalPointsEarned = 4321,
            streak = 12,
            longestStreak = 30,
            rankUpStreakCounter = 40,
            streakShields = 2
        )
        val store = FakeStatsStore(original)

        StatRecomputer(store, FakePoints(str = 60), ImmediateTransactor).recompute()

        val after = store.stats!!
        assertEquals("lifetime points total untouched", 4321, after.totalPointsEarned)
        assertEquals("streak untouched", 12, after.streak)
        assertEquals("best streak untouched", 30, after.longestStreak)
        assertEquals("work days rebuilt from history, not the legacy counter", 0, after.workDays)
        assertEquals("paid shields untouched", 2, after.streakShields)
    }

    @Test
    fun `no stats row is a no-op rather than a crash`() = runTest {
        val result = StatRecomputer(FakeStatsStore(null), FakePoints(), ImmediateTransactor).recompute()
        assertEquals(RecomputeResult.NoStats, result)
    }

    @Test
    fun `work days are rebuilt from earn history, not the legacy counter`() = runTest {
        // The pre-v4 counter maxed out at 5, so a long-standing player would otherwise be
        // dumped back to rank E. 80 consecutive active days must survive the upgrade.
        val store = FakeStatsStore(PlayerStats(rank = Rank.S, rankUpStreakCounter = 5))
        val points = FakePoints(
            str = 300, int = 300, wis = 300, dex = 300, cha = 300, vit = 300,
            days = consecutiveDaysEndingYesterday(80)
        )

        val result = StatRecomputer(store, points, ImmediateTransactor).recompute()

        assertEquals(80, store.stats!!.workDays)
        assertEquals("80 days + avg 65 clears A, not S", Rank.A, store.stats!!.rank)
        assertEquals(80, (result as RecomputeResult.Recomputed).workDays)
    }

    @Test
    fun `a player with no history rebuilds to zero work days`() = runTest {
        val store = FakeStatsStore(PlayerStats(rankUpStreakCounter = 5))

        StatRecomputer(store, FakePoints(), ImmediateTransactor).recompute()

        assertEquals(0, store.stats!!.workDays)
        assertEquals(Rank.E, store.stats!!.rank)
    }

    private fun consecutiveDaysEndingYesterday(count: Int): List<String> {
        val yesterday = java.time.LocalDate.now().minusDays(1)
        return (0 until count).map { yesterday.minusDays((count - 1 - it).toLong()).toString() }
    }

    // ---- Fakes ----

    private class FakePoints(
        private val str: Int = 0, private val int: Int = 0, private val wis: Int = 0,
        private val dex: Int = 0, private val cha: Int = 0, private val vit: Int = 0,
        private val days: List<String> = emptyList()
    ) : LifetimeStatPointsSource {
        override suspend fun activeEarnDays(): List<String> = days
        override suspend fun lifetimePoints(stat: StatType): Int = when (stat) {
            StatType.STR -> str
            StatType.INT -> int
            StatType.WIS -> wis
            StatType.DEX -> dex
            StatType.CHA -> cha
            StatType.VIT -> vit
        }
    }

    private class FakeStatsStore(var stats: PlayerStats?) : DecayStatsStore {
        override suspend fun getStatsOnce(): PlayerStats? = stats
        override suspend fun updateStats(stats: PlayerStats) { this.stats = stats }
        override suspend fun updateStreak(streak: Int) = error("recompute must not touch the streak")
        override suspend fun updateRank(rank: Rank) = error("rank is written with the stats row")
        override suspend fun updateWorkDays(workDays: Int) = error("recompute must not touch work days")
    }

    private object ImmediateTransactor : Transactor {
        override suspend fun <R> transaction(block: suspend () -> R): R = block()
    }
}
