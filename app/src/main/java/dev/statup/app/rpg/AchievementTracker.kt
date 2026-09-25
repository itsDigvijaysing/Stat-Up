package dev.statup.app.rpg

import dev.statup.app.data.repository.AchievementRepository
import dev.statup.app.data.repository.PlayerRepository
import dev.statup.app.data.repository.PointsRepository
import dev.statup.app.domain.model.PlayerStats
import dev.statup.app.domain.model.Rank
import dev.statup.app.domain.model.TransactionSource

class AchievementTracker(
    private val achievementRepository: AchievementRepository,
    private val playerRepository: PlayerRepository,
    private val pointsRepository: PointsRepository
) {
    /**
     * Called after points are earned. Checks points-based and task-based achievements.
     */
    suspend fun onPointsEarned(source: TransactionSource) {
        val totalEarned = pointsRepository.getTotalEarned()
        checkPointsAchievements(totalEarned)

        if (source == TransactionSource.TODOIST || source == TransactionSource.MISSION) {
            checkTaskAchievements()
        }
    }

    /**
     * Called after a mood check-in. Tracks consecutive mood days.
     * We approximate by counting total mood transactions (simple approach).
     */
    suspend fun onMoodCheckedIn() {
        val achievement = achievementRepository.getAchievement("mood_7") ?: return
        if (achievement.isUnlocked) return
        achievementRepository.updateProgress("mood_7", achievement.progress + 1)
    }

    /**
     * Called after a reward is redeemed.
     */
    suspend fun onRewardRedeemed() {
        achievementRepository.updateProgress("first_reward", 1)
    }

    /**
     * Called when Todoist is connected for the first time.
     */
    suspend fun onTodoistConnected() {
        achievementRepository.updateProgress("todoist_connect", 1)
    }

    /**
     * Called after streak/rank updates (e.g., from DecayEngine).
     */
    suspend fun onStreakUpdated() {
        val stats = playerRepository.getStatsOnce() ?: return
        checkStreakAchievements(stats.streak)
        checkRankAchievements(stats.rank)
        checkStatAchievements(stats)
    }

    /**
     * Full check — call after any significant game event to catch everything.
     */
    suspend fun checkAll() {
        val stats = playerRepository.getStatsOnce() ?: return
        val totalEarned = pointsRepository.getTotalEarned()

        checkPointsAchievements(totalEarned)
        checkStreakAchievements(stats.streak)
        checkRankAchievements(stats.rank)
        checkStatAchievements(stats)
        checkTaskAchievements()
    }

    private suspend fun checkPointsAchievements(totalEarned: Int) {
        val pointsAchievements = listOf(
            "points_100" to 100,
            "points_500" to 500,
            "points_1000" to 1000,
            "points_5000" to 5000
        )
        for ((id, _) in pointsAchievements) {
            achievementRepository.updateProgress(id, totalEarned)
        }
    }

    private suspend fun checkStreakAchievements(streak: Int) {
        val streakAchievements = listOf(
            "streak_3" to 3,
            "streak_7" to 7,
            "streak_14" to 14,
            "streak_30" to 30,
            "streak_100" to 100
        )
        for ((id, _) in streakAchievements) {
            achievementRepository.updateProgress(id, streak)
        }
    }

    private suspend fun checkRankAchievements(rank: Rank) {
        val rankOrder = rank.order
        val rankAchievements = listOf(
            "rank_d" to 1,
            "rank_c" to 2,
            "rank_b" to 3,
            "rank_a" to 4,
            "rank_s" to 5,
            "rank_ex" to 6
        )
        for ((id, requiredOrder) in rankAchievements) {
            if (rankOrder >= requiredOrder) {
                achievementRepository.updateProgress(id, requiredOrder)
            }
        }
    }

    private suspend fun checkStatAchievements(stats: PlayerStats) {
        val maxStat = maxOf(
            stats.strStat, stats.intStat, stats.wisStat,
            stats.dexStat, stats.chaStat, stats.vitStat
        )
        achievementRepository.updateProgress("stat_20", maxStat)
        achievementRepository.updateProgress("stat_50", maxStat)
        achievementRepository.updateProgress("stat_max", maxStat)

        // Balanced: all stats >= 25
        val minStat = minOf(
            stats.strStat, stats.intStat, stats.wisStat,
            stats.dexStat, stats.chaStat, stats.vitStat
        )
        achievementRepository.updateProgress("balanced", minStat)
    }

    private suspend fun checkTaskAchievements() {
        val taskCount = pointsRepository.getTaskTransactionCount()
        val taskAchievements = listOf(
            "tasks_10" to 10,
            "tasks_50" to 50,
            "tasks_100" to 100,
            "tasks_500" to 500
        )
        for ((id, _) in taskAchievements) {
            achievementRepository.updateProgress(id, taskCount)
        }
    }
}
