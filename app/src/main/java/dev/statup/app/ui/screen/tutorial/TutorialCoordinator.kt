package dev.statup.app.ui.screen.tutorial

import dev.statup.app.data.local.db.StarterContentSeeder
import dev.statup.app.data.local.db.dao.MissionDao
import dev.statup.app.data.local.db.entity.MissionEntity
import dev.statup.app.domain.model.Achievements
import dev.statup.app.domain.model.PlayerStats
import dev.statup.app.domain.model.StatType
import dev.statup.app.ui.navigation.Routes
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first

/** [targetRoute] is pointed at (a bottom-bar pulse), never auto-navigated to - that used to
 * disable the bar and could strand the user for good. */
enum class TutorialStep(val targetRoute: String?) {
    INTRO(null),
    COMPLETE_TASK(Routes.TASKS),
    // No target tab: the unlock popup comes to the user, so there is nowhere to send them.
    SEE_ACHIEVEMENT(null),
    REDEEM_REWARD(Routes.REWARDS)
}

/** The preference slice the tour needs. Implemented by `UserPreferences`. */
interface TutorialStore {
    suspend fun getTutorialStep(): String?
    suspend fun setTutorialStep(step: String?)
    suspend fun setTutorialComplete(complete: Boolean)
}

/** Whether an achievement's payout has committed. Checked instead of timed - advancing early
 * would show the user a balance they can't yet actually spend. */
interface AchievementPayoutProbe {
    suspend fun isAchievementPaid(achievementId: String): Boolean
}

class TutorialCoordinator(
    private val store: TutorialStore,
    private val missionDao: MissionDao,
    private val payoutProbe: AchievementPayoutProbe,
    /** Re-runs achievement evaluation. Idempotent (`updateProgress` ignores an already-unlocked
     * row), so the tour can safely retry it while a payout hasn't landed yet. */
    private val reevaluateAchievements: suspend () -> Unit
) {
    private val _step = MutableStateFlow<TutorialStep?>(null)
    val step: StateFlow<TutorialStep?> = _step.asStateFlow()

    /** Seeds the sample mission and opens on the intro. Idempotent across process death - the
     * step flag only clears at [finish], so a restart mid-tutorial resumes here instead of re-seeding. */
    suspend fun start() {
        if (_step.value != null) return

        // Resume instead of replaying the intro - the tour can die mid-flow, and replaying
        // would re-pay work already credited, breaking the 5 + 45 = 50 arithmetic.
        val saved = store.getTutorialStep()
            ?.let { name -> TutorialStep.entries.firstOrNull { it.name == name } }
        if (saved != null) {
            _step.value = saved
            return
        }

        ensureTutorialMission()
        moveTo(TutorialStep.INTRO)
    }

    suspend fun beginTour() {
        if (_step.value == TutorialStep.INTRO) moveTo(TutorialStep.COMPLETE_TASK)
    }

    /** Must be the tutorial mission specifically, not any mission - the starter missions pay 4,
     * one short of [TUTORIAL_REWARD_COST], which would dead-end an unredeemable step three. */
    suspend fun onTaskCompleted(mission: MissionEntity) {
        if (_step.value != TutorialStep.COMPLETE_TASK) return
        if (mission.name != TUTORIAL_TASK_NAME) {
            // Completing something else is not progress, but it is a chance to notice the tutorial
            // mission has been deleted and put it back so the step stays completable.
            ensureTutorialMission()
            return
        }
        if (awaitFirstTaskPayout()) moveTo(TutorialStep.SEE_ACHIEVEMENT)
    }

    /** True once the first-task payout lands. Retries the achievement check between probes
     * (idempotent) so a check that actually failed gets another chance, not just a wait. */
    private suspend fun awaitFirstTaskPayout(): Boolean {
        repeat(PAYOUT_ATTEMPTS) { attempt ->
            if (payoutProbe.isAchievementPaid(FIRST_TASK_ACHIEVEMENT_ID)) return true
            if (attempt < PAYOUT_ATTEMPTS - 1) {
                runCatching { reevaluateAchievements() }
                delay(PAYOUT_RETRY_DELAY_MS)
            }
        }
        return payoutProbe.isAchievementPaid(FIRST_TASK_ACHIEVEMENT_ID)
    }

    /** The achievement step is a "look at this" beat, so the user acknowledges it. */
    suspend fun onAchievementAcknowledged() {
        if (_step.value == TutorialStep.SEE_ACHIEVEMENT) moveTo(TutorialStep.REDEEM_REWARD)
    }

    suspend fun onRewardRedeemed() {
        if (_step.value == TutorialStep.REDEEM_REWARD) finish()
    }

    /** Escape hatch from any step (same effect as [finish]) - only the intro used to have a
     * Skip, so an unanticipated stuck step had no way out. */
    suspend fun skip() = finish()

    /** Clears the sample mission and closes the gate for good. */
    suspend fun finish() {
        existingTutorialMission()?.let { missionDao.delete(it) }
        _step.value = null
        store.setTutorialStep(null)
        store.setTutorialComplete(true)
    }

    private suspend fun moveTo(step: TutorialStep) {
        _step.value = step
        store.setTutorialStep(step.name)
    }

    private suspend fun existingTutorialMission(): MissionEntity? =
        missionDao.getAllMissions().first().firstOrNull { it.name == TUTORIAL_TASK_NAME }

    /** Restores the tutorial mission by name (not row id) if missing - safe across process
     * death, no duplicate seeding. */
    private suspend fun ensureTutorialMission() {
        if (existingTutorialMission() != null) return
        missionDao.insert(
            MissionEntity(
                name = TUTORIAL_TASK_NAME,
                description = "Your first one - tap this card",
                pointsReward = TUTORIAL_TASK_POINTS,
                statType = StatType.VIT.name,
                isDaily = false,
                isCompletedToday = false,
                createdAt = System.currentTimeMillis()
            )
        )
    }

    companion object {
        const val TUTORIAL_TASK_NAME = "Drink a glass of water"
        const val TUTORIAL_TASK_POINTS = PlayerStats.POINTS_PER_STAT

        /** The achievement the tutorial's second step is waiting on. */
        const val FIRST_TASK_ACHIEVEMENT_ID = "first_task"

        /** Read from [Achievements] rather than a literal - the tour tells the user this number,
         * so a stale hardcoded value here would make the app visibly lie. */
        val FIRST_TASK_REWARD: Int
            get() = Achievements.getById(FIRST_TASK_ACHIEVEMENT_ID)?.rewardPoints ?: 0

        /** Sourced from [StarterContentSeeder] (single source of truth for the ladder).
         * Invariant: [TUTORIAL_TASK_POINTS] + [FIRST_TASK_REWARD] must cover this cost. */
        val TUTORIAL_REWARD_NAME: String get() = StarterContentSeeder.CHEAPEST_REWARD_NAME
        val TUTORIAL_REWARD_COST: Int get() = StarterContentSeeder.CHEAPEST_REWARD_COST

        /** Probe attempts for the first-task payout before giving up and holding the step. */
        private const val PAYOUT_ATTEMPTS = 3
        private const val PAYOUT_RETRY_DELAY_MS = 400L
    }
}
