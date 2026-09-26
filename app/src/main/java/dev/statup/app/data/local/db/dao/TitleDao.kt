package dev.statup.app.data.local.db.dao

import androidx.room.*
import dev.statup.app.data.local.db.entity.TitleEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TitleDao {
    // isUnlocked ASC: in-progress first, completed sink to the bottom of the list.
    @Query("SELECT * FROM titles ORDER BY isUnlocked ASC, target ASC")
    fun getAll(): Flow<List<TitleEntity>>

    @Query("SELECT * FROM titles WHERE isUnlocked = 1")
    fun getUnlocked(): Flow<List<TitleEntity>>

    @Query("SELECT * FROM titles WHERE id = :id")
    suspend fun getById(id: String): TitleEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(title: TitleEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(titles: List<TitleEntity>)

    @Update
    suspend fun update(title: TitleEntity)

    // MAX(...) keeps progress monotonic: some trackers report a value that can drop after stat
    // decay (e.g. "balanced stats" minStat), which would otherwise visibly regress the bar.
    @Query("UPDATE titles SET progress = MAX(progress, :progress) WHERE id = :id")
    suspend fun updateProgress(id: String, progress: Int)

    /**
     * Guarded on isUnlocked = 0 so an already-claimed row keeps the value actually paid out -
     * re-pointing it would advertise a reward the player never received.
     */
    @Query("UPDATE titles SET rewardPoints = :points WHERE id = :id AND isUnlocked = 0")
    suspend fun updateRewardPointsIfLocked(id: String, points: Int)

    @Query("UPDATE titles SET isUnlocked = 1, unlockedAt = :unlockedAt WHERE id = :id")
    suspend fun unlock(id: String, unlockedAt: Long = System.currentTimeMillis())

    @Query("DELETE FROM titles WHERE id = :id")
    suspend fun deleteById(id: String)
}
