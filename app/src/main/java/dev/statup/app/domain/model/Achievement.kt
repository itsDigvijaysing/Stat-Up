package dev.statup.app.domain.model

import androidx.compose.ui.graphics.Color
import dev.statup.app.ui.theme.*

data class Achievement(
    val id: String,
    val name: String,
    val description: String,
    val emoji: String,
    val category: AchievementCategory,
    val target: Int,
    val progress: Int = 0,
    val isUnlocked: Boolean = false,
    val unlockedAt: Long? = null,
    val rewardPoints: Int = 0
) {
    val isNoGoal: Boolean get() = target == 0
    val progressPercent: Float get() = if (target <= 0) 0f else (progress.toFloat() / target).coerceIn(0f, 1f)
    val displayRewardPoints: Int get() = if (rewardPoints > 0) rewardPoints else when {
        isNoGoal -> 25
        target >= 100 -> 150
        target >= 50 -> 80
        target >= 25 -> 50
        target >= 10 -> 25
        else -> 25
    }
}

enum class AchievementCategory(val displayName: String, val color: Color, val emoji: String) {
    STREAK("Streak Master", AccentWarning, "🔥"),
    POINTS("Point Collector", PointsGold, "✨"),
    STATS("Stat Builder", AccentPrimary, "📊"),
    TASKS("Task Crusher", AccentSuccess, "✅"),
    RANK("Rank Climber", StatCHA, "⭐"),
    SPECIAL("Special", AccentSecondary, "🏆")
}

object Achievements {
    // IDs here must stay in sync with the ones AchievementTracker checks - when the tracker
    // calls `updateProgress(id, …)` for an id that isn't seeded, the call is a silent no-op.
    val ALL = listOf(
        // Streak
        Achievement("streak_3", "Three in a Row", "Maintain a 3-day streak", "🔥", AchievementCategory.STREAK, 3, rewardPoints = 25),
        Achievement("streak_7", "Week Warrior", "Maintain a 7-day streak", "🔥", AchievementCategory.STREAK, 7, rewardPoints = 60),
        Achievement("streak_14", "Two Weeks Strong", "Maintain a 14-day streak", "🔥", AchievementCategory.STREAK, 14, rewardPoints = 120),
        Achievement("streak_30", "Monthly Master", "Maintain a 30-day streak", "🔥", AchievementCategory.STREAK, 30, rewardPoints = 250),
        Achievement("streak_100", "Centurion of Days", "Maintain a 100-day streak", "🔥", AchievementCategory.STREAK, 100, rewardPoints = 750),

        // Points
        Achievement("points_100", "First Steps", "Earn 100 total points", "✨", AchievementCategory.POINTS, 100, rewardPoints = 25),
        Achievement("points_500", "Half a Thousand", "Earn 500 total points", "✨", AchievementCategory.POINTS, 500, rewardPoints = 100),
        Achievement("points_1000", "Thousand Club", "Earn 1,000 total points", "💰", AchievementCategory.POINTS, 1000, rewardPoints = 200),
        Achievement("points_5000", "Point Collector", "Earn 5,000 total points", "💎", AchievementCategory.POINTS, 5000, rewardPoints = 800),

        // Stats
        Achievement("stat_20", "Apprentice Stat", "Raise any stat to 20", "📊", AchievementCategory.STATS, 20, rewardPoints = 40),
        Achievement("stat_50", "Half Century", "Raise any stat to 50", "📊", AchievementCategory.STATS, 50, rewardPoints = 100),
        Achievement("stat_max", "Maxed Out", "Raise any stat to 100", "🌟", AchievementCategory.STATS, 100, rewardPoints = 300),
        Achievement("balanced", "Balanced", "Raise every stat to 25", "⚖️", AchievementCategory.STATS, 25, rewardPoints = 120),

        // Tasks
        // Pays the 45 that, with the tutorial's 5-point task, exactly affords the 50-point
        // "Favorite Meal" the guided first run ends on.
        Achievement("first_task", "First Step", "Complete your first task", "👣", AchievementCategory.TASKS, 1, rewardPoints = 45),
        Achievement("tasks_10", "Task Beginner", "Complete 10 tasks", "✅", AchievementCategory.TASKS, 10, rewardPoints = 25),
        Achievement("tasks_50", "Task Veteran", "Complete 50 tasks", "✅", AchievementCategory.TASKS, 50, rewardPoints = 80),
        Achievement("tasks_100", "Task Centurion", "Complete 100 tasks", "🎯", AchievementCategory.TASKS, 100, rewardPoints = 150),
        Achievement("tasks_500", "Task Crusher", "Complete 500 tasks", "🎯", AchievementCategory.TASKS, 500, rewardPoints = 500),

        // Rank
        Achievement("rank_d", "D-Rank Hunter", "Reach D Rank", "⭐", AchievementCategory.RANK, 1, rewardPoints = 40),
        Achievement("rank_c", "C-Rank Hunter", "Reach C Rank", "⭐", AchievementCategory.RANK, 2, rewardPoints = 75),
        Achievement("rank_b", "B-Rank Hunter", "Reach B Rank", "⭐", AchievementCategory.RANK, 3, rewardPoints = 150),
        Achievement("rank_a", "A-Rank Hunter", "Reach A Rank", "⭐", AchievementCategory.RANK, 4, rewardPoints = 300),
        Achievement("rank_s", "S-Rank Hunter", "Reach S Rank", "👑", AchievementCategory.RANK, 5, rewardPoints = 600),
        Achievement("rank_ex", "Beyond the Ladder", "Reach EX Rank", "🔱", AchievementCategory.RANK, 6, rewardPoints = 1200),

        // Special
        Achievement("first_reward", "Treat Yourself", "Redeem your first reward", "🎁", AchievementCategory.SPECIAL, 1, rewardPoints = 20),
        Achievement("todoist_connect", "Connected", "Connect to Todoist", "📋", AchievementCategory.SPECIAL, 1, rewardPoints = 25),
        Achievement("mood_7", "Reflective", "Check in your mood for 7 days", "🧘", AchievementCategory.SPECIAL, 7, rewardPoints = 60)
    )

    fun getById(id: String): Achievement? = ALL.find { it.id == id }
}
