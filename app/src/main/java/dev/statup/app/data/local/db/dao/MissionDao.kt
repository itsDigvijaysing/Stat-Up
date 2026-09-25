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
     * Completes a mission only if it is not already complete, returning the number of rows changed.
     *
     * The condition is the double-tap guard: a read-then-write cannot provide one, because
     * `getById` suspends and a second tap interleaves at that suspension point, so both callers see
     * `isCompletedToday = false` and both award points. Let SQLite decide the winner instead.
     */
    @Query(
        "UPDATE missions SET isCompletedToday = 1, lastCompletedAt = :completedAt, " +
            "streak = streak + 1 WHERE id = :id AND isCompletedToday = 0"
    )
    suspend fun completeIfNotDone(id: Long, completedAt: Long = System.currentTimeMillis()): Int

    /**
     * Clears the daily completions. **`isDaily = 1` is load-bearing.**
     *
     * Without it this also un-completed one-off missions - the ones created with the "Repeats Daily"
     * switch off - so a declared one-off came back every midnight and could be re-awarded
     * indefinitely. That needed no race and no crash; it was simply wrong every night.
     */
    @Query("UPDATE missions SET isCompletedToday = 0 WHERE isDaily = 1")
    suspend fun resetDailyCompletions()

    @Delete
    suspend fun delete(mission: MissionEntity)

    @Query("DELETE FROM missions WHERE id = :id")
    suspend fun deleteById(id: Long)
}
