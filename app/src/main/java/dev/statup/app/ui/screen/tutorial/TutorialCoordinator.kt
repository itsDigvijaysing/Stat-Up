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

/**
 * Where the guided first run currently is. `null` means no tutorial is running.
 *
 * [targetRoute] is the tab the step lives on. The tutorial **points** at it (a pulse on the
 * bottom bar) instead of navigating there itself - being teleported between tabs teaches
 * nothing about where things are, and it also meant the bottom bar had to be disabled, which
 * could strand the user for good (delete the sample mission on step one and there was no way
 * back). The user drives; the tutorial guides.
 */
enum class TutorialStep(val targetRoute: String?) {
    INTRO(null),
    COMPLETE_TASK(Routes.TASKS),
    // No target tab: the unlock popup comes to the user, so there is nowhere to send them.
    SEE_ACHIEVEMENT(null),
    REDEEM_REWARD(Routes.REWARDS)
}

/**
 * Drives the guided first run across the **real** tabs rather than a mock screen.
 *
 * The numbers are arranged to close the loop exactly: the seeded tutorial mission pays
 * [TUTORIAL_TASK_POINTS], the first-task achievement pays 45, and that totals the 50 that buys
 * "Favorite Meal" - so the user sees earn → unlock → spend end to end with nothing dangling.
 *
 * The tutorial mission is worth 5 rather than the 4 the starter missions pay, because 5 is
 * [PlayerStats.POINTS_PER_STAT]: a 4-point task leaves the bar at 4/5 and the stat does not
 * visibly move, which is the anticlimax this exists to prevent. It is deleted when the
 * tutorial ends so it does not linger as a stray one-off.
 *
 * **The arithmetic has no slack, so step one is specific, not generic.** It completes on the
 * tutorial mission alone: the starter missions pay 4, and completing one of those instead left the
 * user a point short at step three, which then could not be redeemed - and step three only
 * completes on a *successful* redemption. Deleting the sample content is handled by re-seeding it
 * ([ensureTutorialMission]) rather than by accepting any completion, and [skip] is always available
 * as a last resort, so no path can dead-end the flow.
 */
/** The preference slice the tour needs. Implemented by `UserPreferences`. */
interface TutorialStore {
    suspend fun getTutorialStep(): String?
    suspend fun setTutorialStep(step: String?)
    suspend fun setTutorialComplete(complete: Boolean)
}

/**
 * Whether an achievement's payout has actually committed. Implemented by `PointsRepository`.
 *
 * The tour needs this rather than a timer: it tells the user their balance and then asks them to
 * spend all of it, so advancing while the payout is still in flight leaves them short.
 */
interface AchievementPayoutProbe {
    suspend fun isAchievementPaid(achievementId: String): Boolean
}

class TutorialCoordinator(
    private val store: TutorialStore,
    private val missionDao: MissionDao,
    private val payoutProbe: AchievementPayoutProbe,
    /**
     * Re-runs the achievement evaluation. Idempotent - `AchievementTracker.onPointsEarned` writes
     * absolute progress and `updateProgress` ignores an already-unlocked row - so the tour can
     * safely retry it when the payout has not landed yet.
     */
    private val reevaluateAchievements: suspend () -> Unit
) {
    private val _step = MutableStateFlow<TutorialStep?>(null)
    val step: StateFlow<TutorialStep?> = _step.asStateFlow()

    /**
     * Called once the tutorial gate opens. Seeds the sample mission and opens on the intro,
     * so the user is told a tour is starting rather than being dropped into one.
     *
     * Idempotent across process death as well as within a process: the flag only clears at
     * [finish], so a restart mid-tutorial re-enters here, and keying off the mission's name
     * rather than a remembered row id stops it seeding a second copy.
     */
    suspend fun start() {
        if (_step.value != null) return

        // Resume where the user actually was. The tour spans several real screens, so the
        // process can easily die mid-flow; replaying the intro would ask them to redo work
        // they have already been paid for and would break the 5 + 45 = 50 arithmetic.
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

    /**
     * Step one completes on the **tutorial mission specifically**, not on any mission.
     *
     * It used to accept any completion, so that deleting the sample content could not dead-end the
     * flow. But the starter missions pay 4 and the tutorial's pays [TUTORIAL_TASK_POINTS], and the
     * tour's arithmetic has no slack: completing a starter mission instead left the user one point
     * short of [TUTORIAL_REWARD_COST] at step three, which cannot be redeemed, and step three only
     * completes on a *successful* redemption - a permanent dead end. Deletion is handled by
     * repairing the mission instead, which dead-ends nothing.
     *
     * The step also waits for the first-task payout to have committed. The caller runs the
     * achievement check before calling this, so it has normally already landed; the retry is
     * insurance for a slow or failed evaluation, because advancing without the payout puts the user
     * in front of a reward they cannot afford.
     */
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

    /**
     * True once the first-task payout is in the balance. Retries the (idempotent) achievement
     * evaluation between probes rather than just sleeping, so a check that actually failed gets
     * another chance instead of being waited out.
     */
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

    /**
     * Abandon the tour from any step. Same effect as [finish]; named separately because it is
     * offered as an escape rather than reached by completing the loop.
     *
     * The intro had a Skip but the steps did not, so a user who could not complete a step had no way
     * out - the two dead ends fixed above were reachable precisely because of that. Keeping an exit
     * on every step means a strand we have not thought of is still recoverable by the user.
     */
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

    /**
     * Puts the tutorial mission back if it is missing. Keyed off the name, not a remembered row id,
     * so it is safe across process death and cannot seed a second copy.
     */
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

        /**
         * What the first-task achievement pays. Read from [Achievements] rather than restated,
         * because the tour tells the user this number and then asks them to check it against
         * their own balance - a stale literal here would make the app visibly lie.
         */
        val FIRST_TASK_REWARD: Int
            get() = Achievements.getById(FIRST_TASK_ACHIEVEMENT_ID)?.rewardPoints ?: 0

        /**
         * The reward the tutorial steers toward, and its cost. Both come from
         * [StarterContentSeeder] so the seeded ladder stays the single source of truth;
         * [TUTORIAL_TASK_POINTS] + [FIRST_TASK_REWARD] must cover [TUTORIAL_REWARD_COST].
         */
        val TUTORIAL_REWARD_NAME: String get() = StarterContentSeeder.CHEAPEST_REWARD_NAME
        val TUTORIAL_REWARD_COST: Int get() = StarterContentSeeder.CHEAPEST_REWARD_COST

        /** Probe attempts for the first-task payout before giving up and holding the step. */
        private const val PAYOUT_ATTEMPTS = 3
        private const val PAYOUT_RETRY_DELAY_MS = 400L
    }
}
