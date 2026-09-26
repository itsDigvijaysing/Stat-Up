package dev.statup.app.ui.screen.rewards

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.statup.app.data.repository.PointsRepository
import dev.statup.app.data.repository.RewardRepository
import dev.statup.app.domain.model.Reward
import dev.statup.app.rpg.AchievementTracker
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class RewardsViewModel(
    private val rewardRepository: RewardRepository,
    private val pointsRepository: PointsRepository,
    private val achievementTracker: AchievementTracker,
    private val tutorialCoordinator: dev.statup.app.ui.screen.tutorial.TutorialCoordinator
) : ViewModel() {

    private val _uiState = MutableStateFlow(RewardsUiState())
    val uiState: StateFlow<RewardsUiState> = _uiState.asStateFlow()

    // Monotonic id so the screen's LaunchedEffect re-fires even when the SAME reward is
    // redeemed twice inside the snackbar's window (the name alone would be an unchanged key).
    private var redeemEventId = 0L

    init {
        viewModelScope.launch {
            rewardRepository.activeRewards.collect { rewards ->
                _uiState.update { it.copy(rewards = rewards, isLoading = false) }
            }
        }

        viewModelScope.launch {
            pointsRepository.balanceFlow.collect { balance ->
                _uiState.update { it.copy(currentBalance = balance) }
            }
        }
    }

    fun showCreateDialog() {
        _uiState.update { it.copy(showCreateDialog = true) }
    }

    fun hideCreateDialog() {
        _uiState.update { it.copy(showCreateDialog = false) }
    }

    fun createReward(name: String, description: String?, cost: Int, emoji: String, category: String?) {
        viewModelScope.launch {
            val reward = Reward(
                name = name,
                description = description,
                pointsCost = cost,
                emoji = emoji,
                category = category ?: "General"
            )
            rewardRepository.createReward(reward)
            hideCreateDialog()
        }
    }

    fun editReward(original: Reward, name: String, description: String?, cost: Int, emoji: String, category: String?) {
        viewModelScope.launch {
            // Preserve id/createdAt/timesRedeemed; only the user-editable fields change.
            rewardRepository.updateReward(
                original.copy(
                    name = name,
                    description = description,
                    pointsCost = cost,
                    emoji = emoji,
                    category = category ?: original.category
                )
            )
        }
    }

    fun redeemReward(reward: Reward) {
        viewModelScope.launch {
            val result = rewardRepository.redeemReward(reward)
            result.onSuccess {
                achievementTracker.onRewardRedeemed()
                // Closes the guided first run once the loop has been completed for real.
                tutorialCoordinator.onRewardRedeemed()
                _uiState.update {
                    it.copy(redeemSuccess = RedeemSuccess(++redeemEventId, reward.name))
                }
            }.onFailure { error ->
                // Name the shortfall rather than restating both numbers - this is also the message
                // the guided tour's last step surfaces, so it must say what to do, not just that it failed.
                val message = when (error) {
                    is dev.statup.app.data.repository.InsufficientPointsException ->
                        "You need ${error.required - error.available} more points for this."
                    else -> error.message
                }
                _uiState.update { it.copy(error = message) }
            }
        }
    }

    fun deleteReward(reward: Reward) {
        viewModelScope.launch {
            rewardRepository.deleteReward(reward.id)
        }
    }

    fun clearRedeemSuccess() {
        _uiState.update { it.copy(redeemSuccess = null) }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }
}

data class RewardsUiState(
    val rewards: List<Reward> = emptyList(),
    val currentBalance: Int = 0,
    val isLoading: Boolean = true,
    val error: String? = null,
    val redeemSuccess: RedeemSuccess? = null,
    val showCreateDialog: Boolean = false
)

/**
 * One redemption event. [id] is monotonically increasing so back-to-back redemptions of the
 * same reward still produce distinct LaunchedEffect keys (restarting the snackbar timer).
 */
data class RedeemSuccess(val id: Long, val rewardName: String)
