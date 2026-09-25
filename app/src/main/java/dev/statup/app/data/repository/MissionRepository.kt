package dev.statup.app.data.repository

import dev.statup.app.data.local.datastore.UserPreferences
import dev.statup.app.data.local.db.dao.MissionDao
import dev.statup.app.data.local.db.entity.MissionEntity
import dev.statup.app.domain.model.StatType
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

/**
 * Owns mission CRUD, the double-tap completion guard, and the daily-completion reset. Extracted
 * from TasksViewModel so the reset can run from background work (DecayWorker) - not just when the
 * Tasks screen happens to be open - and so the completion guard lives in one place.
 */
class MissionRepository(
    private val missionDao: MissionDao,
    private val userPreferences: UserPreferences
) {
    val missions: Flow<List<MissionEntity>> = missionDao.getAllMissions()

    suspend fun createMission(
        name: String,
        description: String?,
        points: Int,
        statType: StatType,
        isDaily: Boolean
    ) {
        missionDao.insert(
            MissionEntity(
                name = name,
                description = description,
                pointsReward = points,
                statType = statType.name,
                isDaily = isDaily,
                isCompletedToday = false,
                createdAt = System.currentTimeMillis()
            )
        )
    }

    /**
     * Marks the mission complete with a double-tap race guard: re-reads the live row, and for a
     * daily mission already completed today returns null (the caller must NOT award points).
     * Marking complete happens BEFORE the caller's points award so a racing second tap sees
     * `isCompletedToday = true`. On success returns the updated mission to award points for.
     */
    suspend fun completeMission(missionId: Long): MissionEntity? {
        val current = missionDao.getById(missionId) ?: return null
        if (current.isDaily && current.isCompletedToday) return null
        val updated = current.copy(
            isCompletedToday = true,
            lastCompletedAt = System.currentTimeMillis(),
            streak = current.streak + 1
        )
        missionDao.update(updated)
        return updated
    }

    suspend fun deleteMission(mission: MissionEntity) {
        missionDao.delete(mission)
    }

    /**
     * Resets daily-mission completions at most once per local day. Safe to call from the midnight
     * DecayWorker and on Tasks-screen resume - gated on lastMissionResetDay so completed daily
     * missions clear even on days the user never opens the Tasks tab.
     */
    suspend fun resetDailyIfNeeded() {
        val today = LocalDate.now().toString()
        if (userPreferences.getLastMissionResetDay() == today) return
        missionDao.resetDailyCompletions()
        userPreferences.setLastMissionResetDay(today)
    }
}
