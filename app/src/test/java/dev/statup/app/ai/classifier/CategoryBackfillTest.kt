package dev.statup.app.ai.classifier

import dev.statup.app.domain.model.PlayerStats
import dev.statup.app.domain.model.Rank
import dev.statup.app.domain.model.StatType
import dev.statup.app.rpg.DecayStatsStore
import dev.statup.app.rpg.Transactor
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The backfill's whole safety property is that it only ever fills in blanks. These tests pin
 * that, plus the accumulator credit that makes newly-categorised history actually count.
 */
class CategoryBackfillTest {

    @Test
    fun `categorises only the rows the model is confident about`() = runTest {
        val store = FakeEarnStore(
            listOf(
                UncategorisedEarn(1, "go to the gym", 4),
                UncategorisedEarn(2, "asdfgh", 2),
                UncategorisedEarn(3, "read a book", 3)
            )
        )
        val classifier = FakeClassifier(
            mapOf(
                "go to the gym" to StatType.STR,
                "read a book" to StatType.WIS
            )
        )
        val stats = FakeStatsStore(PlayerStats())

        val result = CategoryBackfill(store, stats, classifier, ImmediateTransactor).run()

        assertEquals(3, result.scanned)
        assertEquals("the unclassifiable row is skipped, not guessed", 2, result.categorised)
        assertEquals(StatType.STR, store.assigned[1L])
        assertEquals(StatType.WIS, store.assigned[3L])
        assertTrue("no stat written for the unclear row", !store.assigned.containsKey(2L))
    }

    @Test
    fun `a row that gains a category in the meantime is not credited twice`() = runTest {
        // assignStat returns false when the UPDATE's `statType IS NULL` guard no longer holds.
        val store = FakeEarnStore(listOf(UncategorisedEarn(1, "go to the gym", 40)), assignSucceeds = false)
        val stats = FakeStatsStore(PlayerStats())

        val result = CategoryBackfill(
            store, stats, FakeClassifier(mapOf("go to the gym" to StatType.STR)), ImmediateTransactor
        ).run()

        assertEquals(0, result.categorised)
        assertEquals("no stat credit for a row we did not win", null, stats.written)
    }

    @Test
    fun `credited points raise the stat on the new curve`() = runTest {
        val store = FakeEarnStore(
            listOf(
                UncategorisedEarn(1, "go to the gym", 20),
                UncategorisedEarn(2, "go to the gym", 3)
            )
        )
        val stats = FakeStatsStore(PlayerStats(strStat = 10, strPointsAcc = 1))

        CategoryBackfill(
            store, stats, FakeClassifier(mapOf("go to the gym" to StatType.STR)), ImmediateTransactor
        ).run()

        // 1 carried + 23 new = 24 → +4 stat points, 4 carried.
        assertEquals(14, stats.written!!.strStat)
        assertEquals(4, stats.written!!.strPointsAcc)
        assertEquals("other stats untouched", PlayerStats.BASE_STAT, stats.written!!.intStat)
    }

    @Test
    fun `the Todoist description prefix is not fed to the model`() = runTest {
        val store = FakeEarnStore(listOf(UncategorisedEarn(1, "Todoist: go to the gym", 4)))
        val classifier = FakeClassifier(mapOf("go to the gym" to StatType.STR))

        val result = CategoryBackfill(store, FakeStatsStore(PlayerStats()), classifier, ImmediateTransactor).run()

        assertEquals(1, result.categorised)
        assertEquals("go to the gym", classifier.seen.single())
    }

    @Test
    fun `nothing to do is a cheap no-op`() = runTest {
        val stats = FakeStatsStore(PlayerStats())
        val result = CategoryBackfill(
            FakeEarnStore(emptyList()), stats, FakeClassifier(emptyMap()), ImmediateTransactor
        ).run()

        assertEquals(BackfillResult(scanned = 0, categorised = 0), result)
        assertEquals(null, stats.written)
    }

    @Test
    fun `progress is reported for every row`() = runTest {
        val store = FakeEarnStore((1L..5L).map { UncategorisedEarn(it, "row $it", 1) })
        val seen = mutableListOf<Pair<Int, Int>>()

        CategoryBackfill(store, FakeStatsStore(PlayerStats()), FakeClassifier(emptyMap()), ImmediateTransactor)
            .run { done, total -> seen += done to total }

        assertEquals(listOf(1 to 5, 2 to 5, 3 to 5, 4 to 5, 5 to 5), seen)
    }

    // ---- Fakes ----

    private class FakeClassifier(
        private val answers: Map<String, StatType>,
        private val confidence: Float = 0.9f
    ) : TaskClassifier {
        val seen = mutableListOf<String>()
        override fun classify(text: String): StatSuggestion? {
            seen += text
            return answers[text]?.let { StatSuggestion(it, confidence) }
        }
    }

    private class FakeEarnStore(
        private val rows: List<UncategorisedEarn>,
        private val assignSucceeds: Boolean = true
    ) : UncategorisedEarnStore {
        val assigned = mutableMapOf<Long, StatType>()
        override suspend fun uncategorisedEarns(): List<UncategorisedEarn> = rows
        override suspend fun assignStat(id: Long, stat: StatType): Boolean {
            if (!assignSucceeds) return false
            assigned[id] = stat
            return true
        }
    }

    private class FakeStatsStore(private val initial: PlayerStats) : DecayStatsStore {
        var written: PlayerStats? = null
        override suspend fun getStatsOnce(): PlayerStats = written ?: initial
        override suspend fun updateStats(stats: PlayerStats) { written = stats }
        override suspend fun updateStreak(streak: Int) = error("backfill must not touch the streak")
        override suspend fun updateRank(rank: Rank) = error("backfill must not touch the rank")
        override suspend fun updateWorkDays(workDays: Int) = error("backfill must not touch work days")
    }

    private object ImmediateTransactor : Transactor {
        override suspend fun <R> transaction(block: suspend () -> R): R = block()
    }
}
