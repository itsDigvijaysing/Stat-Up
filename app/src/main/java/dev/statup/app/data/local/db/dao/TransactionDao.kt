package dev.statup.app.data.local.db.dao

import androidx.room.*
import dev.statup.app.data.local.db.entity.TransactionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TransactionDao {
    @Query("SELECT * FROM transactions ORDER BY createdAt DESC")
    fun getAll(): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transactions ORDER BY createdAt DESC LIMIT :limit")
    fun getRecent(limit: Int): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transactions WHERE type = :type ORDER BY createdAt DESC")
    fun getByType(type: String): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transactions WHERE type = :type ORDER BY createdAt DESC LIMIT :limit")
    fun getRecentByType(type: String, limit: Int): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transactions WHERE statType = :statType ORDER BY createdAt DESC")
    fun getByStatType(statType: String): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transactions WHERE createdAt >= :startTime AND createdAt < :endTime ORDER BY createdAt DESC")
    fun getByDateRange(startTime: Long, endTime: Long): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transactions WHERE externalId = :externalId LIMIT 1")
    suspend fun getByExternalId(externalId: String): TransactionEntity?

    /** Every local date with at least one earn, ascending - the real history the one-time Work
     * Day recompute replays, since the pre-v4 column reset on every promotion and is unusable. */
    @Query(
        "SELECT DISTINCT date(createdAt / 1000, 'unixepoch', 'localtime') AS day " +
            "FROM transactions WHERE type = 'EARN' ORDER BY day ASC"
    )
    suspend fun getActiveEarnDays(): List<String>

    /** Lifetime points ever earned in one stat - the input to the one-time stat recompute. */
    @Query("SELECT IFNULL(SUM(points), 0) FROM transactions WHERE type = 'EARN' AND statType = :statType")
    suspend fun getLifetimePointsForStat(statType: String): Int

    /** Completed earns with no stat assigned - the classifier backfill's work list. Excludes
     * achievement payouts (stat-less by design) or "remaining" could never reach zero. */
    @Query(
        "SELECT * FROM transactions WHERE type = 'EARN' AND statType IS NULL " +
            "AND (description IS NULL OR description NOT LIKE 'Achievement reward: %') " +
            "ORDER BY createdAt DESC"
    )
    suspend fun getUncategorisedEarns(): List<TransactionEntity>

    @Query("UPDATE transactions SET statType = :statType WHERE id = :id AND statType IS NULL")
    suspend fun assignStatTypeIfMissing(id: Long, statType: String): Int

    @Query(
        "SELECT COUNT(*) FROM transactions WHERE type = 'EARN' AND statType IS NULL " +
            "AND (description IS NULL OR description NOT LIKE 'Achievement reward: %')"
    )
    fun countUncategorisedEarns(): Flow<Int>

    /** Whether a specific achievement payout has committed, matched by its description. Backs
     * the tutorial's advance check, which must not fire while a payout is still in flight. */
    @Query("SELECT COUNT(*) FROM transactions WHERE type = 'EARN' AND description = :description")
    suspend fun countByDescription(description: String): Int

    /** How many times a mission has been paid - stops a one-off mission being re-awarded after
     * the pre-fix nightly reset un-completed it. */
    @Query("SELECT COUNT(*) FROM transactions WHERE source = 'MISSION' AND relatedId = :missionId")
    suspend fun countMissionAwards(missionId: String): Int

    @Query("SELECT SUM(points) FROM transactions WHERE type = 'EARN'")
    suspend fun getTotalEarned(): Int?

    @Query("SELECT SUM(points) FROM transactions WHERE type = 'REDEEM'")
    suspend fun getTotalRedeemed(): Int?

    @Query("SELECT IFNULL(SUM(CASE WHEN type = 'EARN' THEN points WHEN type = 'REDEEM' THEN -points ELSE 0 END), 0) FROM transactions")
    fun getBalance(): Flow<Int>

    @Query("SELECT SUM(points) FROM transactions WHERE type = 'EARN' AND createdAt >= :startTime AND createdAt < :endTime")
    suspend fun getEarnedInRange(startTime: Long, endTime: Long): Int?

    @Query("SELECT IFNULL(SUM(points), 0) FROM transactions WHERE type = 'EARN' AND createdAt >= :startTime AND createdAt < :endTime")
    fun observeEarnedInRange(startTime: Long, endTime: Long): Flow<Int>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(transaction: TransactionEntity): Long

    /** Returns -1 on a unique-constraint conflict instead of replacing, so concurrent Todoist
     * sync runs can't double-award the same completed task. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIgnore(transaction: TransactionEntity): Long

    @Delete
    suspend fun delete(transaction: TransactionEntity)

    @Query("SELECT COUNT(*) FROM transactions WHERE type = 'EARN' AND (source = 'TODOIST' OR source = 'MISSION')")
    suspend fun getTaskTransactionCount(): Int

    @Query("SELECT COUNT(*) FROM transactions WHERE source = :source AND createdAt >= :startTime AND createdAt < :endTime")
    fun countBySourceInRange(source: String, startTime: Long, endTime: Long): Flow<Int>

    /** Debug-only: removes the fabricated demo history by its description marker. */
    @Query("DELETE FROM transactions WHERE description LIKE :prefix")
    suspend fun deleteByDescriptionPrefix(prefix: String)

    @Query("DELETE FROM transactions")
    suspend fun deleteAll()
}
