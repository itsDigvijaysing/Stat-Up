package dev.statup.app.rpg

import dev.statup.app.ai.classifier.CategoryBackfill
import dev.statup.app.ai.classifier.StatSuggestion
import dev.statup.app.ai.classifier.TaskClassifier
import dev.statup.app.ai.classifier.UncategorisedEarn
import dev.statup.app.ai.classifier.UncategorisedEarnStore
import dev.statup.app.domain.model.PlayerStats
import dev.statup.app.domain.model.Rank
import dev.statup.app.domain.model.StatType
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The one-time upgrade sequences backfill → recompute behind a version gate. Every user's
 * stats pass through this exactly once, so the gate and the ordering are worth pinning.
 */
class StatUpgradeRunnerTest {

    @Test
    fun `runs once and is a no-op on every later start`() = runTest {
        val store = FakeStore()
        val earns = FakeEarnStore(mutableListOf(UncategorisedEarn(1, "go to the gym", 20)))
        val stats = FakeStatsStore(PlayerStats())
        val runner = runner(store, earns, stats)

        runner.runIfNeeded()
        assertEquals(StatRecomputer.CURVE_VERSION, store.curveVersion)
        assertEquals(1, earns.reads)

        runner.runIfNeeded()
        assertEquals("second start must not touch anything", 1, earns.reads)
    }

    @Test
    fun `categorises before recomputing, so new categories count`() = runTest {
        // 20 STR points exist but the row has no statType. Only a backfill-then-recompute
        // order turns them into stat points: the recompute sums transactions.statType.
        val store = FakeStore()
        val earns = FakeEarnStore(mutableListOf(UncategorisedEarn(1, "go to the gym", 20)))
        val stats = FakeStatsStore(PlayerStats())

        runner(store, earns, stats).runIfNeeded()

        assertEquals("row was categorised", StatType.STR, earns.assigned[1L])
        assertEquals("BASE + 20/5", 9, stats.stats!!.strStat)
    }

    @Test
    fun `an install that already onboarded is not sent through the tutorial`() = runTest {
        val store = FakeStore(onboarded = true)

        runner(store, FakeEarnStore(mutableListOf()), FakeStatsStore(PlayerStats())).runIfNeeded()

        assertTrue("upgrading users must skip the first-run walkthrough", store.tutorialComplete)
    }

    @Test
    fun `a fresh install still gets the tutorial`() = runTest {
        val store = FakeStore(onboarded = false)

        runner(store, FakeEarnStore(mutableListOf()), FakeStatsStore(PlayerStats())).runIfNeeded()

        assertFalse(store.tutorialComplete)
    }

    @Test
    fun `a failure leaves the gate open for a retry instead of wedging the app`() = runTest {
        val store = FakeStore()
        val exploding = object : UncategorisedEarnStore {
            override suspend fun uncategorisedEarns(): List<UncategorisedEarn> = error("db down")
            override suspend fun assignStat(id: Long, stat: StatType) = false
        }
        val runner = StatUpgradeRunner(
            store,
            CategoryBackfill(exploding, FakeStatsStore(PlayerStats()), FakeClassifier, ImmediateTransactor),
            StatRecomputer(FakeStatsStore(PlayerStats()), NoPoints, ImmediateTransactor)
        )

        runCatching { runner.runIfNeeded() }

        assertEquals("version not advanced, so the next launch retries", 0, store.curveVersion)
        assertEquals("never left holding the progress screen", StatUpgradeState.Done, runner.state.value)
    }

    // ---- helpers ----

    private fun runner(store: FakeStore, earns: FakeEarnStore, stats: FakeStatsStore) =
        StatUpgradeRunner(
            store = store,
            backfill = CategoryBackfill(earns, stats, FakeClassifier, ImmediateTransactor),
            recomputer = StatRecomputer(
                stats,
                // Mirrors the real SQL SUM: only rows the backfill actually categorised count,
                // which is what makes the backfill-then-recompute ordering observable here.
                object : LifetimeStatPointsSource {
                    override suspend fun lifetimePoints(stat: StatType): Int =
                        earns.pointsAssignedTo(stat)
                    override suspend fun activeEarnDays(): List<String> = emptyList()
                },
                ImmediateTransactor
            )
        )

    private class FakeStore(
        var curveVersion: Int = 0,
        private val onboarded: Boolean = false
    ) : StatUpgradeStore {
        var tutorialComplete = false
        override suspend fun getStatCurveVersion(): Int = curveVersion
        override suspend fun setStatCurveVersion(version: Int) { curveVersion = version }
        override suspend fun isOnboardingComplete(): Boolean = onboarded
        override suspend fun setTutorialComplete(complete: Boolean) { tutorialComplete = complete }
    }

    private class FakeEarnStore(private val rows: MutableList<UncategorisedEarn>) : UncategorisedEarnStore {
        var reads = 0
        val assigned = mutableMapOf<Long, StatType>()
        override suspend fun uncategorisedEarns(): List<UncategorisedEarn> {
            reads++
            return rows.toList()
        }
        override suspend fun assignStat(id: Long, stat: StatType): Boolean {
            assigned[id] = stat
            return true
        }
        fun pointsAssignedTo(stat: StatType): Int =
            rows.filter { assigned[it.id] == stat }.sumOf { it.points }
    }

    private object NoPoints : LifetimeStatPointsSource {
        override suspend fun lifetimePoints(stat: StatType): Int = 0
        override suspend fun activeEarnDays(): List<String> = emptyList()
    }

    private object FakeClassifier : TaskClassifier {
        override fun classify(text: String): StatSuggestion? =
            if (text.contains("gym")) StatSuggestion(StatType.STR, 0.9f) else null
    }

    private class FakeStatsStore(var stats: PlayerStats?) : DecayStatsStore {
        override suspend fun getStatsOnce(): PlayerStats? = stats
        override suspend fun updateStats(stats: PlayerStats) { this.stats = stats }
        override suspend fun updateStreak(streak: Int) = error("unused")
        override suspend fun updateRank(rank: Rank) = error("unused")
        override suspend fun updateWorkDays(workDays: Int) = error("unused")
    }

    private object ImmediateTransactor : Transactor {
        override suspend fun <R> transaction(block: suspend () -> R): R = block()
    }
}
