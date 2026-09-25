package dev.statup.app.ui.screen.tutorial

import dev.statup.app.data.local.db.dao.MissionDao
import dev.statup.app.data.local.db.entity.MissionEntity
import dev.statup.app.domain.model.PlayerStats
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The tour's state machine. Two real defects lived here before it was covered: progress that
 * only existed in memory (a restart replayed the intro and asked for work already paid for),
 * and a seed keyed on a remembered row id (a restart seeded a second sample mission).
 */
class TutorialCoordinatorTest {

    @Test
    fun `start seeds one sample mission and opens on the intro`() = runTest {
        val dao = FakeMissionDao()
        val store = FakeStore()
        val c = TutorialCoordinator(store, dao)

        c.start()

        assertEquals(TutorialStep.INTRO, c.step.value)
        assertEquals(1, dao.missions.count { it.name == TutorialCoordinator.TUTORIAL_TASK_NAME })
        assertEquals(
            "5 is POINTS_PER_STAT, so the stat visibly moves",
            PlayerStats.POINTS_PER_STAT,
            dao.missions.first().pointsReward
        )
    }

    @Test
    fun `a restart mid-tour resumes the step and does not seed a second mission`() = runTest {
        val dao = FakeMissionDao()
        val store = FakeStore()
        TutorialCoordinator(store, dao).apply { start(); beginTour(); onTaskCompleted() }

        // New instance = process death; the completion flag is still unset.
        val revived = TutorialCoordinator(store, dao)
        revived.start()

        assertEquals(TutorialStep.SEE_ACHIEVEMENT, revived.step.value)
        assertEquals("no duplicate sample mission", 1, dao.missions.size)
    }

    @Test
    fun `the full run reaches the end and cleans up the sample mission`() = runTest {
        val dao = FakeMissionDao()
        val store = FakeStore()
        val c = TutorialCoordinator(store, dao)

        c.start()
        c.beginTour()
        c.onTaskCompleted()
        c.onAchievementAcknowledged()
        assertEquals(TutorialStep.REDEEM_REWARD, c.step.value)
        c.onRewardRedeemed()

        assertNull("tour is over", c.step.value)
        assertTrue("completion flag set", store.complete)
        assertNull("saved step cleared", store.step)
        assertTrue("sample mission removed", dao.missions.isEmpty())
    }

    @Test
    fun `steps only advance from their own step, so stray events are ignored`() = runTest {
        val c = TutorialCoordinator(FakeStore(), FakeMissionDao())
        c.start()

        // A redemption or completion before the tour begins must not skip ahead.
        c.onTaskCompleted()
        c.onRewardRedeemed()
        c.onAchievementAcknowledged()

        assertEquals(TutorialStep.INTRO, c.step.value)
    }

    @Test
    fun `events are inert when no tour is running`() = runTest {
        val store = FakeStore()
        val c = TutorialCoordinator(store, FakeMissionDao())

        c.onTaskCompleted()
        c.onRewardRedeemed()

        assertNull(c.step.value)
        assertTrue("must not mark the tutorial done", !store.complete)
    }

    @Test
    fun `only the achievement step has no tab to point at`() {
        assertNull(TutorialStep.SEE_ACHIEVEMENT.targetRoute)
        assertEquals("tasks", TutorialStep.COMPLETE_TASK.targetRoute)
        assertEquals("rewards", TutorialStep.REDEEM_REWARD.targetRoute)
    }

    private class FakeStore : TutorialStore {
        var step: String? = null
        var complete = false
        override suspend fun getTutorialStep(): String? = step
        override suspend fun setTutorialStep(step: String?) { this.step = step }
        override suspend fun setTutorialComplete(complete: Boolean) { this.complete = complete }
    }

    private class FakeMissionDao : MissionDao {
        val missions = mutableListOf<MissionEntity>()
        private var nextId = 1L
        override fun getAllMissions(): Flow<List<MissionEntity>> = flowOf(missions.toList())
        override suspend fun insert(mission: MissionEntity): Long {
            missions += mission.copy(id = nextId)
            return nextId++
        }
        override suspend fun delete(mission: MissionEntity) { missions.removeAll { it.name == mission.name } }
        override fun getAll(): Flow<List<MissionEntity>> = error("unused")
        override fun getDailyMissions(): Flow<List<MissionEntity>> = error("unused")
        override suspend fun getById(id: Long): MissionEntity? = missions.firstOrNull { it.id == id }
        override suspend fun update(mission: MissionEntity) = error("unused")
        override suspend fun markCompleted(id: Long, completed: Boolean, completedAt: Long) = error("unused")
        override suspend fun resetDailyCompletions() = error("unused")
        override suspend fun deleteById(id: Long) = error("unused")
    }
}
