package dev.statup.app.data.repository

import androidx.room.withTransaction
import dev.statup.app.data.local.db.AppDatabase
import dev.statup.app.data.local.db.dao.RewardDao
import dev.statup.app.data.local.db.entity.RewardEntity
import dev.statup.app.domain.model.Reward
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class RewardRepository(
    private val database: AppDatabase,
    private val rewardDao: RewardDao,
    private val pointsRepository: PointsRepository
) {
    val activeRewards: Flow<List<Reward>> = rewardDao.getAllActive().map { list ->
        list.map { it.toDomain() }
    }

    val allRewards: Flow<List<Reward>> = rewardDao.getAll().map { list ->
        list.map { it.toDomain() }
    }

    suspend fun getById(id: Long): Reward? = rewardDao.getById(id)?.toDomain()

    suspend fun createReward(reward: Reward): Long {
        return rewardDao.insert(reward.toEntity())
    }

    suspend fun updateReward(reward: Reward) {
        rewardDao.update(reward.toEntity())
    }

    suspend fun deleteReward(id: Long) {
        rewardDao.deleteById(id)
    }

    /**
     * Redeems atomically, re-reading the live balance from SQL (not the possibly-stale ViewModel
     * snapshot) inside one transaction, so concurrent redemptions can't overspend the balance.
     */
    suspend fun redeemReward(reward: Reward): Result<Unit> = database.withTransaction {
        val liveBalance = pointsRepository.getCurrentBalance()
        if (liveBalance < reward.pointsCost) {
            return@withTransaction Result.failure(
                InsufficientPointsException(reward.pointsCost, liveBalance)
            )
        }
        pointsRepository.redeemPoints(
            points = reward.pointsCost,
            description = "Redeemed: ${reward.name}",
            relatedId = reward.id.toString()
        )
        rewardDao.incrementRedeemed(reward.id)
        Result.success(Unit)
    }

    private fun RewardEntity.toDomain(): Reward = Reward(
        id = id,
        name = name,
        description = description,
        pointsCost = pointsCost,
        category = category,
        emoji = emoji,
        isActive = isActive,
        createdAt = createdAt,
        timesRedeemed = timesRedeemed
    )

    private fun Reward.toEntity(): RewardEntity = RewardEntity(
        id = id,
        name = name,
        description = description,
        pointsCost = pointsCost,
        category = category,
        emoji = emoji,
        isActive = isActive,
        createdAt = createdAt,
        timesRedeemed = timesRedeemed
    )
}

class InsufficientPointsException(val required: Int, val available: Int) :
    Exception("Insufficient points: need $required, have $available")
