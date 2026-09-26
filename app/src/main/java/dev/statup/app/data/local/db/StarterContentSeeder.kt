package dev.statup.app.data.local.db

import androidx.room.withTransaction
import dev.statup.app.data.local.datastore.UserPreferences
import dev.statup.app.data.local.db.dao.MissionDao
import dev.statup.app.data.local.db.dao.RewardDao
import dev.statup.app.data.local.db.entity.MissionEntity
import dev.statup.app.data.local.db.entity.RewardEntity
import dev.statup.app.domain.model.StatType
import kotlinx.coroutines.flow.first

/**
 * Seeds one starter mission per stat and a 50 → 1000 reward ladder so Tasks/Rewards aren't empty
 * after onboarding. Gated on a DataStore flag, not emptiness - deleting samples must be permanent.
 */
object StarterContentSeeder {

    // One per stat, so the hexagon fills evenly. Descriptions omit the stat name - the
    // card already shows a stat chip, so repeating it just lengthened every row.
    private val MISSIONS = listOf(
        Mission("Workout", "Any real physical effort", StatType.STR),
        Mission("Study or Learn Something", "Study or solve something", StatType.INT),
        Mission("Book Reading", "Read or reflect", StatType.WIS),
        Mission("Practice a Skill", "Drill a craft or skill", StatType.DEX),
        Mission("Talk to Someone", "Reach out to someone", StatType.CHA),
        Mission("7+ hrs Sleep & 2+ Meals", "Rest and eat properly", StatType.VIT)
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

    /**
     * The cheapest reward, which the tutorial steers toward and the user can just afford -
     * derived from [REWARDS], not restated, since the tutorial's arithmetic has zero slack.
     */
    private val cheapestReward get() = REWARDS.minBy { it.cost }
    val CHEAPEST_REWARD_NAME: String get() = cheapestReward.name
    val CHEAPEST_REWARD_COST: Int get() = cheapestReward.cost

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

    /**
     * Seeds anything missing, matched **by name** so a crash between committing and writing the
     * flag can't duplicate items - writing the flag first would risk skipping seeding entirely.
     */
    suspend fun seed(database: AppDatabase, missionDao: MissionDao, rewardDao: RewardDao) {
        val now = System.currentTimeMillis()
        database.withTransaction {
            val existingMissions = missionDao.getAllMissions().first().mapTo(mutableSetOf()) { it.name }
            val existingRewards = rewardDao.getAll().first().mapTo(mutableSetOf()) { it.name }
            MISSIONS.filterNot { it.name in existingMissions }.forEach {
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
            REWARDS.filterNot { it.name in existingRewards }.forEach {
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
