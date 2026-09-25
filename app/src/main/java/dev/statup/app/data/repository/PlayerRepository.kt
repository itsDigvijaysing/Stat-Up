package dev.statup.app.data.repository

import dev.statup.app.data.local.datastore.UserPreferences
import dev.statup.app.data.local.db.dao.PlayerStatsDao
import dev.statup.app.data.local.db.entity.PlayerStatsEntity
import dev.statup.app.domain.model.PlayerStats
import dev.statup.app.domain.model.Rank
import dev.statup.app.rpg.DecayStatsStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class PlayerRepository(
    private val playerStatsDao: PlayerStatsDao,
    private val userPreferences: UserPreferences
) : PlayerStateProvider, DecayStatsStore {
    override val username: Flow<String> = userPreferences.username

    val playerStats: Flow<PlayerStats?> = playerStatsDao.getStats().map { entity ->
        entity?.toDomain()
    }

    override suspend fun getStatsOnce(): PlayerStats? {
        return playerStatsDao.getStatsOnce()?.toDomain()
    }

    suspend fun initializeStats() {
        val existing = playerStatsDao.getStatsOnce()
        if (existing == null) {
            playerStatsDao.insert(
                PlayerStatsEntity(
                    id = 1,
                    lastActivityAt = null,
                    updatedAt = System.currentTimeMillis()
                )
            )
        }
    }

    override suspend fun updateStats(stats: PlayerStats) {
        playerStatsDao.update(stats.toEntity())
    }

    override suspend fun updateStreak(streak: Int) {
        playerStatsDao.updateStreak(streak)
    }

    override suspend fun updateRank(rank: Rank) {
        playerStatsDao.updateRank(rank.name)
    }

    override suspend fun updateWorkDays(workDays: Int) {
        playerStatsDao.updateWorkDays(workDays)
    }

    suspend fun addTotalPoints(points: Int) {
        playerStatsDao.addPoints(points)
    }

    suspend fun setUsername(name: String) {
        userPreferences.setUsername(name)
    }

    private fun PlayerStatsEntity.toDomain(): PlayerStats = PlayerStats(
        strStat = strStat,
        intStat = intStat,
        wisStat = wisStat,
        dexStat = dexStat,
        chaStat = chaStat,
        vitStat = vitStat,
        strPointsAcc = strPointsAcc,
        intPointsAcc = intPointsAcc,
        wisPointsAcc = wisPointsAcc,
        dexPointsAcc = dexPointsAcc,
        chaPointsAcc = chaPointsAcc,
        vitPointsAcc = vitPointsAcc,
        totalPointsEarned = totalPointsEarned,
        rank = Rank.fromString(rank),
        streak = streak,
        longestStreak = longestStreak,
        rankUpStreakCounter = rankUpStreakCounter,
        rankDownBreakCounter = rankDownBreakCounter,
        streakShields = streakShields,
        lastActivityAt = lastActivityAt,
        updatedAt = updatedAt
    )

    private fun PlayerStats.toEntity(): PlayerStatsEntity = PlayerStatsEntity(
        id = 1,
        strStat = strStat,
        intStat = intStat,
        wisStat = wisStat,
        dexStat = dexStat,
        chaStat = chaStat,
        vitStat = vitStat,
        strPointsAcc = strPointsAcc,
        intPointsAcc = intPointsAcc,
        wisPointsAcc = wisPointsAcc,
        dexPointsAcc = dexPointsAcc,
        chaPointsAcc = chaPointsAcc,
        vitPointsAcc = vitPointsAcc,
        totalPointsEarned = totalPointsEarned,
        rank = rank.name,
        streak = streak,
        longestStreak = longestStreak,
        rankUpStreakCounter = rankUpStreakCounter,
        rankDownBreakCounter = rankDownBreakCounter,
        streakShields = streakShields,
        lastActivityAt = lastActivityAt,
        updatedAt = updatedAt
    )
}
