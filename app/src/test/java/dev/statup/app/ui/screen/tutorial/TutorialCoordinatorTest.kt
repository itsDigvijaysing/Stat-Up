package dev.statup.app.ui.screen.tutorial

import dev.statup.app.data.local.db.dao.MissionDao
import dev.statup.app.data.local.db.entity.MissionEntity
import dev.statup.app.domain.model.Achievements
import dev.statup.app.domain.model.PlayerStats
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Covers two real defects: in-memory-only progress (a restart replayed the intro) and a
 * seed keyed on a remembered row id (a restart seeded a second sample mission). */
class TutorialCoordinatorTest {

    @Test
    fun `start seeds one sample mission and opens on the intro`() = runTest {
        val dao = FakeMissionDao()
        val store = FakeStore()
        val c = coordinator(store, dao)

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
        coordinator(store, dao).apply { start(); beginTour(); onTaskCompleted(tutorialMission) }

        // New instance = process death; the completion flag is still unset.
        val revived = coordinator(store, dao)
        revived.start()

        assertEquals(TutorialStep.SEE_ACHIEVEMENT, revived.step.value)
        assertEquals("no duplicate sample mission", 1, dao.missions.size)
    }

    @Test
    fun `the full run reaches the end and cleans up the sample mission`() = runTest {
        val dao = FakeMissionDao()
        val store = FakeStore()
        val c = coordinator(store, dao)

        c.start()
        c.beginTour()
        c.onTaskCompleted(tutorialMission)
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
        val c = coordinator(FakeStore(), FakeMissionDao())
        c.start()

        // A redemption or completion before the tour begins must not skip ahead.
        c.onTaskCompleted(tutorialMission)
        c.onRewardRedeemed()
        c.onAchievementAcknowledged()

        assertEquals(TutorialStep.INTRO, c.step.value)
    }

    @Test
    fun `events are inert when no tour is running`() = runTest {
        val store = FakeStore()
        val c = coordinator(store, FakeMissionDao())

        c.onTaskCompleted(tutorialMission)
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

    // ---- the two dead ends: wrong mission, and advancing before the payout lands ----

    @Test
    fun `completing a starter mission does not advance step one`() = runTest {
        val dao = FakeMissionDao()
        val c = coordinator(FakeStore(), dao)
        c.start(); c.beginTour()

        // 4 points, not the tutorial's 5. Accepting this left the user on 4 + 45 = 49 against a
        // 50-point reward: one point short, and step three only completes on a *successful* redeem.
        c.onTaskCompleted(MissionEntity(id = 99, name = "Workout", pointsReward = 4, statType = "STR"))

        assertEquals("only the tutorial mission counts", TutorialStep.COMPLETE_TASK, c.step.value)
    }

    @Test
    fun `completing something else repairs a deleted tutorial mission`() = runTest {
        val dao = FakeMissionDao()
        val c = coordinator(FakeStore(), dao)
        c.start(); c.beginTour()
        dao.missions.clear()

        c.onTaskCompleted(MissionEntity(id = 99, name = "Workout", pointsReward = 4, statType = "STR"))

        assertEquals(
            "step one stays completable rather than dead-ending",
            1,
            dao.missions.count { it.name == TutorialCoordinator.TUTORIAL_TASK_NAME }
        )
    }

    @Test
    fun `step one waits for the first-task payout to commit`() = runTest {
        val probe = FakePayoutProbe(paid = false)
        val c = coordinator(FakeStore(), FakeMissionDao(), probe, reevaluate = {})
        c.start(); c.beginTour()

        c.onTaskCompleted(tutorialMission)

        assertEquals(
            "advancing without the 45 points puts the user in front of a reward they cannot afford",
            TutorialStep.COMPLETE_TASK,
            c.step.value
        )
    }

    @Test
    fun `step one advances once a retried achievement check pays out`() = runTest {
        val probe = FakePayoutProbe(paid = false)
        // Stands in for a check that was slow or failed the first time and succeeds on retry.
        val c = coordinator(FakeStore(), FakeMissionDao(), probe, reevaluate = { probe.paid = true })
        c.start(); c.beginTour()

        c.onTaskCompleted(tutorialMission)

        assertEquals(TutorialStep.SEE_ACHIEVEMENT, c.step.value)
    }

    @Test
    fun `skip ends the tour from any step`() = runTest {
        val dao = FakeMissionDao()
        val store = FakeStore()
        val c = coordinator(store, dao)
        c.start(); c.beginTour()

        c.skip()

        assertNull("no step left", c.step.value)
        assertTrue("gate closed for good", store.complete)
        assertTrue("sample mission cleaned up", dao.missions.isEmpty())
    }

    /** The arithmetic is exact (5 + 45 = 50) with no slack - fails if any of the three
     * constants moves. */
    @Test
    fun `the tutorial's earnings cover the reward it steers toward`() {
        val earned = TutorialCoordinator.TUTORIAL_TASK_POINTS + TutorialCoordinator.FIRST_TASK_REWARD
        assertTrue(
            "tutorial pays $earned but its reward costs ${TutorialCoordinator.TUTORIAL_REWARD_COST}",
            earned >= TutorialCoordinator.TUTORIAL_REWARD_COST
        )
        assertEquals(
            "the achievement payout quoted to the user must be the real one",
            Achievements.getById(TutorialCoordinator.FIRST_TASK_ACHIEVEMENT_ID)?.rewardPoints,
            TutorialCoordinator.FIRST_TASK_REWARD
        )
    }

    // ---- helpers ----

    private val tutorialMission = MissionEntity(
        id = 1,
        name = TutorialCoordinator.TUTORIAL_TASK_NAME,
        pointsReward = TutorialCoordinator.TUTORIAL_TASK_POINTS,
        statType = "VIT"
    )

    /** Default: the payout has already landed, which is the normal case now that the caller runs
     *  the achievement check before notifying the coordinator. */
    private fun coordinator(
        store: TutorialStore,
        dao: MissionDao,
        probe: AchievementPayoutProbe = FakePayoutProbe(paid = true),
        reevaluate: suspend () -> Unit = {}
    ) = TutorialCoordinator(store, dao, probe, reevaluate)

    private class FakePayoutProbe(var paid: Boolean) : AchievementPayoutProbe {
        override suspend fun isAchievementPaid(achievementId: String): Boolean = paid
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
        override suspend fun completeIfNotDone(id: Long, completedAt: Long): Int = error("unused")
        override suspend fun resetDailyCompletions() = error("unused")
        override suspend fun deleteById(id: Long) = error("unused")
    }
}
