package dev.statup.app.data.repository

import dev.statup.app.data.local.db.dao.MissionDao
import dev.statup.app.data.local.db.entity.MissionEntity
import dev.statup.app.rpg.Transactor
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Mission completion, which had two independent ways to pay for the same work twice.
 *
 * The double-tap: the old implementation read the row, checked the flag and wrote it back as three
 * separate steps. `getById` suspends, so a second tap interleaves there, both callers see
 * `isCompletedToday = false`, and both award. The fix is a conditional UPDATE - these tests drive it
 * through a fake DAO that honours the condition, so they fail against an unconditional write.
 *
 * The one-off: `resetDailyCompletions()` had no `WHERE isDaily = 1`, so a mission created with
 * "Repeats Daily" off was un-completed every midnight and could be farmed indefinitely. That needed
 * no race at all. [MissionDaoResetTest] covers the SQL; here we cover the repository's guard for rows
 * the shipped build had already un-completed.
 */
class MissionRepositoryTest {

    @Test
    fun `a single completion awards once`() = runTest {
        val dao = FakeMissionDao(daily(id = 1))
        val awards = mutableListOf<Long>()
        val repo = repo(dao, awards)

        val result = repo.completeMission(1)

        assertEquals(listOf(1L), awards)
        assertEquals(1L, result?.id)
        assertTrue("mission is marked done", dao.rows.getValue(1L).isCompletedToday)
    }

    @Test
    fun `two interleaved completions award once`() = runTest {
        val dao = FakeMissionDao(daily(id = 1))
        val awards = mutableListOf<Long>()
        val repo = repo(dao, awards)

        // Sequential here, but the conditional UPDATE is what makes the interleaved case safe: the
        // second caller's UPDATE matches zero rows regardless of what it read beforehand.
        repo.completeMission(1)
        val second = repo.completeMission(1)

        assertEquals("points paid exactly once", listOf(1L), awards)
        assertNull("second call reports nothing awarded", second)
    }

    @Test
    fun `an already-paid one-off is not awarded again`() = runTest {
        // Shape left behind by the shipped build: a one-off that was paid, then un-completed by the
        // nightly reset. It reads as incomplete, so only the award history can tell us it was paid.
        val dao = FakeMissionDao(oneOff(id = 7))
        val awards = mutableListOf<Long>()
        val repo = repo(dao, awards, alreadyAwarded = setOf(7L))

        val result = repo.completeMission(7)

        assertNull("no second payout", result)
        assertTrue("awards list untouched", awards.isEmpty())
        assertTrue(
            "still marked done so it stops reappearing",
            dao.rows.getValue(7L).isCompletedToday
        )
    }

    @Test
    fun `a daily mission is never blocked by its own award history`() = runTest {
        // The dedupe must not apply to dailies: they are supposed to pay again every day, so probing
        // them would break the core loop rather than protect it.
        val dao = FakeMissionDao(daily(id = 3))
        val awards = mutableListOf<Long>()
        val repo = repo(dao, awards, alreadyAwarded = setOf(3L))

        val result = repo.completeMission(3)

        assertEquals(listOf(3L), awards)
        assertEquals(3L, result?.id)
    }

    @Test
    fun `a failing award rolls the completion back`() = runTest {
        val dao = FakeMissionDao(daily(id = 1))
        val repo = MissionRepository(
            missionDao = dao,
            dayStore = FakeDayStore(),
            transactor = RollbackTransactor(dao),
            awardProbe = FakeAwardProbe(emptySet()),
            pointsAwarder = { error("payout failed") }
        )

        runCatching { repo.completeMission(1) }

        assertFalse(
            "a mission marked done with no points paid is worse than one still pending",
            dao.rows.getValue(1L).isCompletedToday
        )
    }

    // ---- helpers ----

    private fun daily(id: Long) = MissionEntity(
        id = id, name = "Daily $id", pointsReward = 4, statType = "STR",
        isDaily = true, isCompletedToday = false
    )

    private fun oneOff(id: Long) = MissionEntity(
        id = id, name = "One-off $id", pointsReward = 4, statType = "STR",
        isDaily = false, isCompletedToday = false
    )

    private fun repo(
        dao: FakeMissionDao,
        awards: MutableList<Long>,
        alreadyAwarded: Set<Long> = emptySet()
    ) = MissionRepository(
        missionDao = dao,
        dayStore = FakeDayStore(),
        transactor = ImmediateTransactor,
        awardProbe = FakeAwardProbe(alreadyAwarded),
        pointsAwarder = { awards += it.id }
    )

    /** Honours the conditional UPDATE, so an unconditional implementation fails these tests. */
    private class FakeMissionDao(vararg seed: MissionEntity) : MissionDao {
        val rows = seed.associateBy { it.id }.toMutableMap()

        override suspend fun completeIfNotDone(id: Long, completedAt: Long): Int {
            val row = rows[id] ?: return 0
            if (row.isCompletedToday) return 0
            rows[id] = row.copy(isCompletedToday = true, lastCompletedAt = completedAt)
            return 1
        }

        override suspend fun resetDailyCompletions() {
            rows.replaceAll { _, row -> if (row.isDaily) row.copy(isCompletedToday = false) else row }
        }

        override suspend fun getById(id: Long): MissionEntity? = rows[id]
        override fun getAllMissions(): Flow<List<MissionEntity>> = flowOf(rows.values.toList())
        override fun getAll(): Flow<List<MissionEntity>> = error("unused")
        override fun getDailyMissions(): Flow<List<MissionEntity>> = error("unused")
        override suspend fun insert(mission: MissionEntity): Long = error("unused")
        override suspend fun update(mission: MissionEntity) = error("unused")
        override suspend fun delete(mission: MissionEntity) = error("unused")
        override suspend fun deleteById(id: Long) = error("unused")
    }

    private class FakeAwardProbe(private val awarded: Set<Long>) : MissionAwardProbe {
        override suspend fun hasBeenAwarded(missionId: Long): Boolean = missionId in awarded
    }

    private class FakeDayStore : MissionResetDayStore {
        var day: String? = null
        override suspend fun getLastMissionResetDay(): String? = day
        override suspend fun setLastMissionResetDay(day: String) { this.day = day }
    }

    private object ImmediateTransactor : Transactor {
        override suspend fun <R> transaction(block: suspend () -> R): R = block()
    }

    /**
     * Snapshots the rows and restores them if the block throws - a pass-through transactor would let
     * the rollback test pass without any rollback actually happening.
     */
    private class RollbackTransactor(private val dao: FakeMissionDao) : Transactor {
        override suspend fun <R> transaction(block: suspend () -> R): R {
            val snapshot = dao.rows.toMap()
            return try {
                block()
            } catch (t: Throwable) {
                dao.rows.clear()
                dao.rows.putAll(snapshot)
                throw t
            }
        }
    }
}
