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
     * Rows of a given [reason] within a time window. Used as the daily tick's idempotency marker.
     *
     * The marker has to live in the same table the tick already writes, so that recording "this day
     * is done" commits atomically with the day's mutation. The old marker was a DataStore key written
     * after the transaction, which cannot join it - so a crash in between re-applied decay.
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
