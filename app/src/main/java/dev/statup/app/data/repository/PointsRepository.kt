package dev.statup.app.data.repository

import androidx.room.withTransaction
import dev.statup.app.data.local.db.AppDatabase
import dev.statup.app.data.local.db.dao.PlayerStatsDao
import dev.statup.app.data.local.db.dao.StatMappingDao
import dev.statup.app.data.local.db.dao.TransactionDao
import dev.statup.app.data.local.db.entity.StatMappingEntity
import dev.statup.app.data.local.db.entity.TransactionEntity
import dev.statup.app.data.local.datastore.UserPreferences
import dev.statup.app.domain.model.*
import dev.statup.app.widget.StatsWidgetUpdater
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

class PointsRepository(
    private val database: AppDatabase,
    private val transactionDao: TransactionDao,
    private val playerStatsDao: PlayerStatsDao,
    private val statMappingDao: StatMappingDao,
    private val userPreferences: UserPreferences,
    private val widgetUpdater: StatsWidgetUpdater? = null
) : dev.statup.app.rpg.LifetimeStatPointsSource,
    dev.statup.app.ai.classifier.UncategorisedEarnStore {
    val transactions: Flow<List<Transaction>> = transactionDao.getAll().map { list ->
        list.map { it.toDomain() }
    }

    val balanceFlow: Flow<Int> = transactionDao.getBalance()

    fun getRecentTransactions(limit: Int): Flow<List<Transaction>> =
        transactionDao.getRecent(limit).map { list -> list.map { it.toDomain() } }

    suspend fun getTotalEarned(): Int = transactionDao.getTotalEarned() ?: 0
    suspend fun getTotalRedeemed(): Int = transactionDao.getTotalRedeemed() ?: 0
    suspend fun getCurrentBalance(): Int = getTotalEarned() - getTotalRedeemed()
    suspend fun getTaskTransactionCount(): Int = transactionDao.getTaskTransactionCount()

    override suspend fun lifetimePoints(stat: StatType): Int =
        transactionDao.getLifetimePointsForStat(stat.name)

    override suspend fun activeEarnDays(): List<String> = transactionDao.getActiveEarnDays()

    override suspend fun uncategorisedEarns(): List<dev.statup.app.ai.classifier.UncategorisedEarn> =
        transactionDao.getUncategorisedEarns().map {
            dev.statup.app.ai.classifier.UncategorisedEarn(it.id, it.description, it.points)
        }

    override suspend fun assignStat(id: Long, stat: StatType): Boolean =
        transactionDao.assignStatTypeIfMissing(id, stat.name) > 0

    /** Live count of earns still missing a stat — drives the Settings row's subtitle. */
    val uncategorisedCount: Flow<Int> = transactionDao.countUncategorisedEarns()

    /**
     * Insert the transaction, increment totals + stat accumulator. All DB writes happen
     * inside a single Room transaction so concurrent earns can't tear a stat update
     * apart from its accumulator/total counterpart.
     */
    suspend fun addPoints(
        points: Int,
        type: TransactionType,
        source: TransactionSource,
        description: String? = null,
        statType: StatType? = null,
        relatedId: String? = null,
        externalId: String? = null
    ): Transaction = database.withTransaction {
        val transaction = TransactionEntity(
            type = type.name,
            source = source.name,
            description = description,
            points = points,
            statType = statType?.name,
            relatedId = relatedId,
            externalId = externalId,
            createdAt = System.currentTimeMillis()
        )
        val id = transactionDao.insert(transaction)

        if (type == TransactionType.EARN) {
            playerStatsDao.addPoints(points)
            if (statType != null) {
                updateStatAccumulator(statType, points)
            }
        }

        widgetUpdater?.refresh()
        transaction.copy(id = id).toDomain()
    }

    suspend fun earnPoints(
        points: Int,
        statType: StatType? = null,
        source: TransactionSource,
        description: String? = null,
        relatedId: String? = null,
        externalId: String? = null
    ): Transaction {
        return addPoints(points, TransactionType.EARN, source, description, statType, relatedId, externalId)
    }

    /**
     * Idempotent earn keyed by [externalId] — used by Todoist sync. Returns the new
     * transaction on first call, or null if a transaction with the same externalId
     * already exists (race winner / prior sync run). The unique index on
     * `transactions.externalId` plus `OnConflictStrategy.IGNORE` makes the check race-safe
     * even when two sync runs overlap.
     */
    suspend fun tryEarnExternalPoints(
        externalId: String,
        points: Int,
        statType: StatType? = null,
        source: TransactionSource,
        description: String? = null,
        relatedId: String? = externalId
    ): Transaction? = database.withTransaction {
        val transaction = TransactionEntity(
            type = TransactionType.EARN.name,
            source = source.name,
            description = description,
            points = points,
            statType = statType?.name,
            relatedId = relatedId,
            externalId = externalId,
            createdAt = System.currentTimeMillis()
        )
        val id = transactionDao.insertIgnore(transaction)
        if (id == -1L) return@withTransaction null

        playerStatsDao.addPoints(points)
        if (statType != null) {
            updateStatAccumulator(statType, points)
        }
        widgetUpdater?.refresh()
        transaction.copy(id = id).toDomain()
    }

    suspend fun redeemPoints(
        points: Int,
        description: String? = null,
        relatedId: String? = null
    ): Transaction = database.withTransaction {
        val transaction = TransactionEntity(
            type = TransactionType.REDEEM.name,
            source = TransactionSource.REWARD.name,
            description = description,
            points = points,
            statType = null,
            relatedId = relatedId,
            createdAt = System.currentTimeMillis()
        )
        val id = transactionDao.insert(transaction)
        widgetUpdater?.refresh()
        transaction.copy(id = id).toDomain()
    }

    /**
     * Buy one Streak Freeze Shield for [PlayerStats.SHIELD_COST] points. Balance check,
     * REDEEM insert, and shield increment run in a single Room transaction (balance is
     * re-read live inside it — same pattern as RewardRepository.redeemReward), so a
     * concurrent redemption can't drive the balance negative. Returns the new shield
     * count, or fails with [InsufficientPointsException] / max-shields.
     */
    suspend fun buyStreakShield(): Result<Int> = runCatching {
        database.withTransaction {
            val stats = playerStatsDao.getStatsOnce()
                ?: error("Player stats not initialized")
            if (stats.streakShields >= PlayerStats.MAX_SHIELDS) {
                error("You already hold the maximum of ${PlayerStats.MAX_SHIELDS} shields.")
            }
            val balance = (transactionDao.getTotalEarned() ?: 0) -
                (transactionDao.getTotalRedeemed() ?: 0)
            if (balance < PlayerStats.SHIELD_COST) {
                throw InsufficientPointsException(
                    required = PlayerStats.SHIELD_COST,
                    available = balance
                )
            }
            transactionDao.insert(
                TransactionEntity(
                    type = TransactionType.REDEEM.name,
                    source = TransactionSource.REWARD.name,
                    description = "🛡️ Streak Shield",
                    points = PlayerStats.SHIELD_COST,
                    statType = null,
                    relatedId = null,
                    createdAt = System.currentTimeMillis()
                )
            )
            playerStatsDao.update(
                stats.copy(
                    streakShields = stats.streakShields + 1,
                    updatedAt = System.currentTimeMillis()
                )
            )
            stats.streakShields + 1
        }.also { widgetUpdater?.refresh() }
    }

    private suspend fun updateStatAccumulator(statType: StatType, points: Int) {
        val stats = playerStatsDao.getStatsOnce() ?: return
        val currentAcc = when (statType) {
            StatType.STR -> stats.strPointsAcc
            StatType.INT -> stats.intPointsAcc
            StatType.WIS -> stats.wisPointsAcc
            StatType.DEX -> stats.dexPointsAcc
            StatType.CHA -> stats.chaPointsAcc
            StatType.VIT -> stats.vitPointsAcc
        }

        val currentStat = when (statType) {
            StatType.STR -> stats.strStat
            StatType.INT -> stats.intStat
            StatType.WIS -> stats.wisStat
            StatType.DEX -> stats.dexStat
            StatType.CHA -> stats.chaStat
            StatType.VIT -> stats.vitStat
        }

        val progress = dev.statup.app.rpg.StatsEngine.applyPoints(currentStat, currentAcc, points)
        val newStat = progress.stat
        val remainingAcc = progress.accumulator

        val updatedStats = when (statType) {
            StatType.STR -> stats.copy(strStat = newStat, strPointsAcc = remainingAcc)
            StatType.INT -> stats.copy(intStat = newStat, intPointsAcc = remainingAcc)
            StatType.WIS -> stats.copy(wisStat = newStat, wisPointsAcc = remainingAcc)
            StatType.DEX -> stats.copy(dexStat = newStat, dexPointsAcc = remainingAcc)
            StatType.CHA -> stats.copy(chaStat = newStat, chaPointsAcc = remainingAcc)
            StatType.VIT -> stats.copy(vitStat = newStat, vitPointsAcc = remainingAcc)
        }

        playerStatsDao.update(updatedStats.copy(
            lastActivityAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        ))
    }

    suspend fun routeToStat(labels: List<String>): StatType {
        return routeToStatCached(labels, statMappingDao.getAllOnce())
    }

    /** Snapshot of stat mappings, suitable for reuse across a sync run. */
    suspend fun loadStatMappings(): List<StatMappingEntity> = statMappingDao.getAllOnce()

    /** Routing variant that reuses a pre-loaded mappings list to avoid N DB round trips. */
    suspend fun routeToStatCached(labels: List<String>, mappings: List<StatMappingEntity>): StatType =
        routeByLabel(labels, mappings) ?: getDefaultStat()

    /**
     * Label routing with no fallback: returns null when no label maps to a stat, so the caller
     * can try the offline classifier before settling for the default.
     */
    fun routeByLabel(labels: List<String>, mappings: List<StatMappingEntity>): StatType? {
        for (label in labels) {
            val mapping = mappings.find { it.sourceName.equals(label, ignoreCase = true) }
            if (mapping != null) return StatType.fromString(mapping.statType)
        }
        return null
    }

    /** Internal so the create-mission / add-points dialogs can seed their picker with it. */
    internal suspend fun getDefaultStat(): StatType {
        val defaultStatName = userPreferences.defaultStat.first()
        return StatType.fromString(defaultStatName) ?: StatType.INT
    }

    private fun TransactionEntity.toDomain(): Transaction = Transaction(
        id = id,
        type = TransactionType.valueOf(type),
        source = TransactionSource.valueOf(source),
        description = description,
        points = points,
        statType = statType?.let { StatType.fromString(it) },
        relatedId = relatedId,
        createdAt = createdAt
    )
}
