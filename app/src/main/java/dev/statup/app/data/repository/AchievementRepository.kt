package dev.statup.app.data.repository

import androidx.room.withTransaction
import dev.statup.app.data.local.db.AppDatabase
import dev.statup.app.data.local.db.dao.TitleDao
import dev.statup.app.data.local.db.entity.TitleEntity
import dev.statup.app.domain.model.Achievement
import dev.statup.app.domain.model.AchievementCategory
import dev.statup.app.domain.model.Achievements
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Tags achievement payouts so the classifier backfill excludes them. Keep the literal in sync
 * with [dev.statup.app.data.local.db.dao.TransactionDao].
 */
const val ACHIEVEMENT_REWARD_PREFIX = "Achievement reward: "

/**
 * Achievement progress, unlocking and payout. Past payout losses from a pre-transaction bug are
 * deliberately not repaired - the affected population is effectively nil and reconciling risks double-paying.
 */
class AchievementRepository(
    private val database: AppDatabase,
    private val titleDao: TitleDao,
    /** Fires the unlock celebration. Optional so tests can leave it out. */
    private val unlockNotifier: dev.statup.app.rpg.AchievementUnlockNotifier? = null,
    /** Optional points-award hook; lazy suspend lambda avoids a circular Koin dependency chain. */
    private val pointsAwarder: suspend (id: String, points: Int) -> Unit = { _, _ -> }
) {
    val achievements: Flow<List<Achievement>> = titleDao.getAll().map { entities ->
        entities.map { entity ->
            val template = Achievements.getById(entity.id)
            entity.toAchievement(template)
        }
    }

    val unlockedAchievements: Flow<List<Achievement>> = titleDao.getUnlocked().map { entities ->
        entities.map { entity ->
            val template = Achievements.getById(entity.id)
            entity.toAchievement(template)
        }
    }

    /**
     * Seeds missing built-in achievements and re-points locked ones on update (existing installs
     * otherwise keep whatever rewardPoints shipped when they installed). Unlocked rows are untouched.
     */
    suspend fun initializeAchievements() {
        Achievements.ALL.forEach { achievement ->
            val existing = titleDao.getById(achievement.id)
            if (existing != null) {
                if (existing.rewardPoints != achievement.rewardPoints) {
                    titleDao.updateRewardPointsIfLocked(achievement.id, achievement.rewardPoints)
                }
            } else {
                titleDao.insert(
                    TitleEntity(
                        id = achievement.id,
                        name = achievement.name,
                        description = achievement.description,
                        emoji = achievement.emoji,
                        isUnlocked = false,
                        unlockedAt = null,
                        progress = 0,
                        target = achievement.target,
                        rewardPoints = achievement.rewardPoints
                    )
                )
            }
        }
    }

    /**
     * Runs unlock + award inside one transaction so concurrent earns can't double-award; the award
     * must stay inside it, since moving it outside once left a silent lose-the-payout window.
     */
    suspend fun updateProgress(achievementId: String, progress: Int) {
        val unlockedNow: Int = database.withTransaction {
            val achievement = titleDao.getById(achievementId) ?: return@withTransaction 0
            if (achievement.isUnlocked) return@withTransaction 0

            titleDao.updateProgress(achievementId, progress)

            if (progress >= achievement.target) {
                titleDao.unlock(achievementId)
                val reward = if (achievement.rewardPoints > 0) achievement.rewardPoints
                    else Achievements.getById(achievementId)?.displayRewardPoints ?: 0
                if (reward > 0) pointsAwarder(achievementId, reward)
                reward
            } else 0
        }
        if (unlockedNow > 0) {
            // Read back AFTER the commit so the celebration shows the unlocked row, not the
            // pre-unlock snapshot.
            titleDao.getById(achievementId)?.let {
                unlockNotifier?.notify(it.toAchievement(Achievements.getById(achievementId)))
            }
        }
    }

    suspend fun checkAndUnlock(achievementId: String): Boolean {
        val achievement = titleDao.getById(achievementId) ?: return false
        if (achievement.isUnlocked) return false

        if (achievement.progress >= achievement.target) {
            titleDao.unlock(achievementId)
            return true
        }
        return false
    }

    /** Manual unlock path (e.g. "mark complete"); same in-transaction double-award guard as [updateProgress]. */
    suspend fun unlockDirectly(achievementId: String) {
        val awardPoints: Int = database.withTransaction {
            val achievement = titleDao.getById(achievementId) ?: return@withTransaction 0
            if (achievement.isUnlocked) return@withTransaction 0
            titleDao.unlock(achievementId)
            if (achievement.rewardPoints > 0) achievement.rewardPoints
                else Achievements.getById(achievementId)?.displayRewardPoints ?: 0
        }
        if (awardPoints > 0) {
            pointsAwarder(achievementId, awardPoints)
        }
    }

    /**
     * Manual-completion only - [AchievementTracker] only advances hardcoded built-in ids, so a
     * `custom_…` id never auto-progresses. `target = 0` marks it no-goal (UI hides the progress bar).
     */
    suspend fun createCustomAchievement(
        name: String,
        description: String,
        emoji: String,
        rewardPoints: Int = 0
    ) {
        titleDao.insert(
            TitleEntity(
                id = "custom_${System.currentTimeMillis()}",
                name = name,
                description = description,
                emoji = emoji,
                isUnlocked = false,
                unlockedAt = null,
                progress = 0,
                target = 0,
                rewardPoints = rewardPoints
            )
        )
    }

    suspend fun deleteAchievement(achievementId: String) {
        titleDao.deleteById(achievementId)
    }

    suspend fun getAchievement(id: String): Achievement? {
        val entity = titleDao.getById(id) ?: return null
        val template = Achievements.getById(entity.id)
        return entity.toAchievement(template)
    }

    private fun TitleEntity.toAchievement(template: Achievement?): Achievement = Achievement(
        id = id,
        name = name,
        description = description,
        emoji = emoji ?: template?.emoji ?: "🏆",
        category = template?.category ?: AchievementCategory.SPECIAL,
        target = target,
        progress = progress,
        isUnlocked = isUnlocked,
        unlockedAt = unlockedAt,
        rewardPoints = rewardPoints
    )
}
