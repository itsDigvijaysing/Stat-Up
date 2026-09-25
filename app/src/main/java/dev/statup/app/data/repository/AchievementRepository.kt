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
 * Points paid out by an unlocked achievement are tagged with this prefix. They are an EARN with
 * no stat - a payout, not a task - so the classifier backfill filters them out by it. Change the
 * literal here and in [dev.statup.app.data.local.db.dao.TransactionDao] together, or the backfill
 * will start offering to categorise achievement payouts and its "remaining" count will never
 * reach zero.
 */
const val ACHIEVEMENT_REWARD_PREFIX = "Achievement reward: "

/**
 * Achievement progress, unlocking and payout.
 *
 * **Past payout losses are deliberately not repaired.** Builds before the award moved inside the
 * unlock transaction could commit an unlock without its points. A one-time reconciliation would
 * compare `titles WHERE isUnlocked = 1` against transactions carrying [ACHIEVEMENT_REWARD_PREFIX]
 * and top up the difference. It is omitted on purpose: the window needed a purely local Room
 * transaction to throw, so the affected population is effectively nil, and a pass that credits
 * points is itself a double-pay risk if its matching is even slightly wrong. This is a decision,
 * not an oversight.
 */
class AchievementRepository(
    private val database: AppDatabase,
    private val titleDao: TitleDao,
    /** Fires the unlock celebration. Optional so tests can leave it out. */
    private val unlockNotifier: dev.statup.app.rpg.AchievementUnlockNotifier? = null,
    /**
     * Optional points-award hook. Provided lazily as a suspend lambda to avoid a circular
     * Koin dependency (AchievementTracker → AchievementRepository → PointsRepository →
     * AchievementTracker). When non-null, [updateProgress] awards `rewardPoints` on first
     * unlock.
     */
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
     * Seed missing built-in achievements, and re-point the ones the user has not unlocked yet.
     *
     * The re-point matters on UPDATE, not install: rows are only inserted when absent, so before
     * this an existing player kept whatever rewardPoints shipped with the version they installed
     * on, and a rebalance never reached them. Already-unlocked rows are deliberately left alone
     * (see updateRewardPointsIfLocked) - no retroactive top-up, no risk of a second payout.
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
     * Update progress for [achievementId]. If the new progress crosses the target the
     * achievement is unlocked and its `rewardPoints` are awarded via [pointsAwarder].
     *
     * The read-then-unlock-then-award sequence runs inside `database.withTransaction` so two
     * concurrent earns that both cross the threshold (e.g. Todoist sync + a manual action in the same
     * instant) can't both observe `isUnlocked=false` and double-award the reward.
     *
     * **The award is inside the transaction.** It used to sit outside, on the reasoning that
     * `pointsAwarder` opens its own transaction and holding ours across it wasn't worth it. But that
     * left a window where the unlock committed and the payout didn't: the row reads as unlocked, so
     * nothing ever retries it, and every caller wraps this in `runCatching`, so the user silently
     * lost the points. Nested Room transactions are safe, which is exactly what makes this the cheap
     * fix. Note that past losses are NOT repaired - see the class KDoc.
     *
     * The read-back and the celebration stay outside, after commit: firing the popup from inside
     * would celebrate a payout that then rolled back.
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

    /**
     * Unlock [achievementId] directly (e.g. user taps "mark complete"). Awards its
     * `rewardPoints` on first unlock, consistent with [updateProgress]; the in-transaction
     * `isUnlocked` guard prevents a double-award if called again.
     */
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
     * Create a user-defined achievement. Manual-completion only - [AchievementTracker] can only
     * advance the hardcoded built-in ids, so a user id (`custom_…`) would never auto-progress.
     * target = 0 marks it as no-goal, which the UI renders without a progress bar.
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
