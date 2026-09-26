package dev.statup.app.data.local.db.dao

import androidx.room.*
import dev.statup.app.data.local.db.entity.MissionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MissionDao {
    @Query("SELECT * FROM missions ORDER BY createdAt DESC")
    fun getAll(): Flow<List<MissionEntity>>

    @Query("SELECT * FROM missions ORDER BY createdAt DESC")
    fun getAllMissions(): Flow<List<MissionEntity>>

    @Query("SELECT * FROM missions WHERE isDaily = 1")
    fun getDailyMissions(): Flow<List<MissionEntity>>

    @Query("SELECT * FROM missions WHERE id = :id")
    suspend fun getById(id: Long): MissionEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(mission: MissionEntity): Long

    @Update
    suspend fun update(mission: MissionEntity)

    /**
     * Completes a mission only if not already complete - the SQL WHERE clause is the double-tap
     * guard; a read-then-write can't provide one since `getById` suspends between the two.
     */
    @Query(
        "UPDATE missions SET isCompletedToday = 1, lastCompletedAt = :completedAt, " +
            "streak = streak + 1 WHERE id = :id AND isCompletedToday = 0"
    )
    suspend fun completeIfNotDone(id: Long, completedAt: Long = System.currentTimeMillis()): Int

    /**
     * Clears daily completions. **`isDaily = 1` is load-bearing** - without it, one-off
     * missions would also reset and could be re-awarded every midnight.
     */
    @Query("UPDATE missions SET isCompletedToday = 0 WHERE isDaily = 1")
    suspend fun resetDailyCompletions()

    @Delete
    suspend fun delete(mission: MissionEntity)

    @Query("DELETE FROM missions WHERE id = :id")
    suspend fun deleteById(id: Long)
}
