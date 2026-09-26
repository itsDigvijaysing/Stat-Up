package dev.statup.app.data.local.db.dao

import androidx.room.*
import dev.statup.app.data.local.db.entity.DecayLogEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface DecayLogDao {
    @Query("SELECT * FROM decay_log ORDER BY createdAt DESC")
    fun getAll(): Flow<List<DecayLogEntity>>

    @Query("SELECT * FROM decay_log ORDER BY createdAt DESC LIMIT :limit")
    fun getRecent(limit: Int): Flow<List<DecayLogEntity>>

    /**
     * Idempotency marker for the daily tick - lives in the same table the tick writes so
     * "day done" commits atomically with the mutation (unlike the old DataStore-key marker).
     */
    @Query(
        "SELECT COUNT(*) FROM decay_log WHERE reason = :reason " +
            "AND createdAt >= :start AND createdAt < :end"
    )
    suspend fun countByReasonInRange(reason: String, start: Long, end: Long): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(log: DecayLogEntity): Long

    @Query("DELETE FROM decay_log")
    suspend fun deleteAll()
}
