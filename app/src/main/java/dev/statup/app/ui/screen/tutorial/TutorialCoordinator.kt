package dev.statup.app.ui.screen.tutorial

import dev.statup.app.data.local.datastore.UserPreferences
import dev.statup.app.data.local.db.dao.MissionDao
import dev.statup.app.data.local.db.entity.MissionEntity
import dev.statup.app.domain.model.PlayerStats
import dev.statup.app.domain.model.StatType
import dev.statup.app.ui.navigation.Routes
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
    SEE_ACHIEVEMENT(Routes.ACHIEVEMENTS),
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
 * Every step also completes on the *generic* action (any mission, any redemption), so deleting
 * the sample content cannot dead-end the flow.
 */
class TutorialCoordinator(
    private val userPreferences: UserPreferences,
    private val missionDao: MissionDao
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
        val saved = userPreferences.getTutorialStep()
            ?.let { name -> TutorialStep.entries.firstOrNull { it.name == name } }
        if (saved != null) {
            _step.value = saved
            return
        }

        if (existingTutorialMission() == null) {
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
        moveTo(TutorialStep.INTRO)
    }

    suspend fun beginTour() {
        if (_step.value == TutorialStep.INTRO) moveTo(TutorialStep.COMPLETE_TASK)
    }

    /** Any mission completion advances step one - the banner points at the right one. */
    suspend fun onTaskCompleted() {
        if (_step.value == TutorialStep.COMPLETE_TASK) moveTo(TutorialStep.SEE_ACHIEVEMENT)
    }

    /** The achievement step is a "look at this" beat, so the user acknowledges it. */
    suspend fun onAchievementAcknowledged() {
        if (_step.value == TutorialStep.SEE_ACHIEVEMENT) moveTo(TutorialStep.REDEEM_REWARD)
    }

    suspend fun onRewardRedeemed() {
        if (_step.value == TutorialStep.REDEEM_REWARD) finish()
    }

    /** Clears the sample mission and closes the gate for good. */
    suspend fun finish() {
        existingTutorialMission()?.let { missionDao.delete(it) }
        _step.value = null
        userPreferences.setTutorialStep(null)
        userPreferences.setTutorialComplete(true)
    }

    private suspend fun moveTo(step: TutorialStep) {
        _step.value = step
        userPreferences.setTutorialStep(step.name)
    }

    private suspend fun existingTutorialMission(): MissionEntity? =
        missionDao.getAllMissions().first().firstOrNull { it.name == TUTORIAL_TASK_NAME }

    companion object {
        const val TUTORIAL_TASK_NAME = "Drink a glass of water"
        const val TUTORIAL_TASK_POINTS = PlayerStats.POINTS_PER_STAT
        /** The reward the tutorial steers toward; its cost matches 5 + 45. */
        const val TUTORIAL_REWARD_NAME = "Favorite Meal"
    }
}
