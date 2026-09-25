package dev.statup.app.data.local.db

import androidx.room.withTransaction
import dev.statup.app.data.local.datastore.UserPreferences
import dev.statup.app.data.local.db.dao.MissionDao
import dev.statup.app.data.local.db.dao.RewardDao
import dev.statup.app.data.local.db.entity.MissionEntity
import dev.statup.app.data.local.db.entity.RewardEntity
import dev.statup.app.domain.model.StatType

/**
 * Seeds one starter mission per stat and a 50 → 1000 reward ladder, so the Tasks and Rewards
 * tabs open with something in them instead of the empty screens that left testers with nothing
 * to do after onboarding. The ladder teaches the economy by itself.
 *
 * Gated on a DataStore flag rather than "is the table empty", because deleting the samples has
 * to be permanent — an emptiness check would resurrect them on the next launch.
 */
object StarterContentSeeder {

    // One per stat, so the hexagon fills evenly and every stat has a worked example.
    private val MISSIONS = listOf(
        Mission("Workout", "Physical effort builds Strength", StatType.STR),
        Mission("Study or Learn Something", "Learning and problem-solving build Intelligence", StatType.INT),
        Mission("Book Reading", "Reflection and reading build Wisdom", StatType.WIS),
        Mission("Practice a Skill", "Repetition and craft build Dexterity", StatType.DEX),
        Mission("Talk to Someone", "Connecting with people builds Charisma", StatType.CHA),
        Mission("7+ hrs Sleep & 2+ Meals", "Rest and nutrition build Vitality", StatType.VIT)
    )

    private val REWARDS = listOf(
        Reward("Favorite Meal", null, 50, "🍦"),
        Reward("Movie Time", null, 80, "🎬"),
        Reward("Special Outing", null, 120, "💆"),
        Reward("3hrs Hobby Session", null, 150, "🎮"),
        Reward("Diamond Reward", "500rs Purchase Power", 250, "🛍️"),
        Reward("Mythic Reward", "1500rs Purchase Power", 500, "🎁"),
        Reward("Legendary Reward", "Trip w 5000rs Budget", 1000, "✈️")
    )

    private const val MISSION_POINTS = 4

    /** Seeds once per install. Safe to call on every app start. */
    suspend fun seedIfNeeded(
        database: AppDatabase,
        missionDao: MissionDao,
        rewardDao: RewardDao,
        userPreferences: UserPreferences
    ) {
        if (userPreferences.isStarterContentSeeded()) return
        seed(database, missionDao, rewardDao)
        userPreferences.setStarterContentSeeded(true)
    }

    /** Unconditional seed — used after `clearAllTables()` during a full reset. */
    suspend fun seed(database: AppDatabase, missionDao: MissionDao, rewardDao: RewardDao) {
        val now = System.currentTimeMillis()
        database.withTransaction {
            MISSIONS.forEach {
                missionDao.insert(
                    MissionEntity(
                        name = it.name,
                        description = it.description,
                        pointsReward = MISSION_POINTS,
                        statType = it.stat.name,
                        isDaily = true,
                        isCompletedToday = false,
                        createdAt = now
                    )
                )
            }
            REWARDS.forEach {
                rewardDao.insert(
                    RewardEntity(
                        name = it.name,
                        description = it.description,
                        pointsCost = it.cost,
                        // RewardEntity.category has no default; the create dialog passes the
                        // same literal, so seeded and user-made rewards stay consistent.
                        category = "General",
                        emoji = it.emoji,
                        isActive = true,
                        createdAt = now
                    )
                )
            }
        }
    }

    private data class Mission(val name: String, val description: String, val stat: StatType)
    private data class Reward(val name: String, val description: String?, val cost: Int, val emoji: String)
}
