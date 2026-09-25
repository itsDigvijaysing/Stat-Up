package dev.statup.app.ui.screen.tutorial

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.statup.app.data.local.datastore.UserPreferences
import dev.statup.app.data.repository.PlayerRepository
import dev.statup.app.data.repository.PointsRepository
import dev.statup.app.domain.model.Achievements
import dev.statup.app.domain.model.PlayerStats
import dev.statup.app.domain.model.Rank
import dev.statup.app.domain.model.StatType
import dev.statup.app.domain.model.TransactionSource
import dev.statup.app.domain.model.TransactionType
import dev.statup.app.rpg.AchievementTracker
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Drives the one-off guided tutorial that runs after onboarding, before the main shell unlocks.
 *
 * The actions are **real**: the sample task writes a real EARN, the stat really moves, the
 * redemption writes a real REDEEM and really unlocks "Treat Yourself". A simulated walkthrough
 * would leave the user's first genuine task completion as the anticlimax this exists to
 * prevent — and the achievement payout means they finish with points banked rather than at zero.
 *
 * The sample task is worth exactly [TUTORIAL_POINTS] because that is
 * [PlayerStats.POINTS_PER_STAT]: a 4-point task would leave the bar at 4/5 with the stat
 * unmoved, demonstrating nothing.
 */
class TutorialViewModel(
    private val pointsRepository: PointsRepository,
    private val playerRepository: PlayerRepository,
    private val achievementTracker: AchievementTracker,
    private val userPreferences: UserPreferences
) : ViewModel() {

    private val _uiState = MutableStateFlow(TutorialUiState())
    val uiState: StateFlow<TutorialUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            playerRepository.playerStats.collect { stats ->
                _uiState.update {
                    it.copy(
                        currentStr = stats?.strStat ?: PlayerStats.BASE_STAT,
                        workDays = stats?.workDays ?: 0
                    )
                }
            }
        }
    }

    fun next() = _uiState.update { it.copy(step = it.step + 1) }

    /** Step 1: complete the sample task. Real earn, real stat gain. */
    fun completeSampleTask() {
        if (_uiState.value.taskDone || _uiState.value.isBusy) return
        _uiState.update { it.copy(isBusy = true, strBefore = it.currentStr) }
        viewModelScope.launch {
            pointsRepository.addPoints(
                points = TUTORIAL_POINTS,
                type = TransactionType.EARN,
                source = TransactionSource.MANUAL,
                description = SAMPLE_TASK,
                statType = StatType.STR
            )
            runCatching { achievementTracker.onPointsEarned(TransactionSource.MANUAL) }
            _uiState.update { it.copy(taskDone = true, isBusy = false, step = it.step + 1) }
        }
    }

    /** Step 3: spend the points just earned. Real redemption, real achievement unlock. */
    fun redeemSampleReward() {
        if (_uiState.value.rewardDone || _uiState.value.isBusy) return
        _uiState.update { it.copy(isBusy = true) }
        viewModelScope.launch {
            pointsRepository.redeemPoints(
                points = TUTORIAL_POINTS,
                description = SAMPLE_REWARD
            )
            // Awards the achievement's own reward points, so the user leaves the tutorial with
            // a balance rather than back at zero.
            runCatching { achievementTracker.onRewardRedeemed() }
            val unlocked = Achievements.getById(FIRST_REWARD_ID)
            _uiState.update {
                it.copy(
                    rewardDone = true,
                    isBusy = false,
                    step = it.step + 1,
                    achievementName = unlocked?.let { a -> "${a.emoji} ${a.name}" },
                    achievementPoints = unlocked?.displayRewardPoints ?: 0
                )
            }
        }
    }

    fun finish() {
        viewModelScope.launch { userPreferences.setTutorialComplete(true) }
    }

    companion object {
        const val TUTORIAL_POINTS = PlayerStats.POINTS_PER_STAT
        const val SAMPLE_TASK = "Drink a glass of water"
        const val SAMPLE_REWARD = "Tutorial: sample reward"
        private const val FIRST_REWARD_ID = "first_reward"

        /** Work days still needed for the first promotion — read live, never hardcoded. */
        val DAYS_TO_FIRST_RANK: Int get() = Rank.D.daysRequired
    }
}

data class TutorialUiState(
    val step: Int = 0,
    val currentStr: Int = PlayerStats.BASE_STAT,
    val strBefore: Int = PlayerStats.BASE_STAT,
    val workDays: Int = 0,
    val taskDone: Boolean = false,
    val rewardDone: Boolean = false,
    val achievementName: String? = null,
    val achievementPoints: Int = 0,
    val isBusy: Boolean = false
)
