package dev.statup.app.data.repository

import dev.statup.app.data.local.db.dao.MissionDao
import dev.statup.app.data.local.db.entity.MissionEntity
import dev.statup.app.domain.model.StatType
import dev.statup.app.rpg.Transactor
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

/**
 * Whether a mission has already been paid for (implemented by `PointsRepository`); only consulted
 * for one-off missions, to catch rows an old nightly reset had un-completed after paying out.
 */
interface MissionAwardProbe {
    suspend fun hasBeenAwarded(missionId: Long): Boolean
}

/**
 * Local-day marker for the once-per-day reset (implemented by `UserPreferences`) - a narrow port so
 * this repository is unit-testable without a `Context`, the same shape as `DecayDayStore`.
 */
interface MissionResetDayStore {
    suspend fun getLastMissionResetDay(): String?
    suspend fun setLastMissionResetDay(day: String)
}

/**
 * Owns mission CRUD, the completion guard, and the daily reset. Extracted from TasksViewModel so
 * the reset can run from background work (DecayWorker), not just when the Tasks screen is open.
 */
class MissionRepository(
    private val missionDao: MissionDao,
    private val dayStore: MissionResetDayStore,
    private val transactor: Transactor,
    private val awardProbe: MissionAwardProbe,
    /**
     * Awards the mission's points; a lambda rather than an injected `PointsRepository` to avoid
     * closing a Koin cycle (PointsRepository -> ... -> MissionRepository) - same as `AchievementRepository`.
     */
    private val pointsAwarder: suspend (MissionEntity) -> Unit
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
     * Completes a mission and awards points atomically - the single transaction ensures a double-tap
     * can't double-award, and an award failure rolls back the completion so it can be retried.
     */
    suspend fun completeMission(missionId: Long): MissionEntity? = transactor.transaction {
        if (missionDao.completeIfNotDone(missionId) == 0) return@transaction null
        val completed = missionDao.getById(missionId) ?: return@transaction null
        // Returning early (not throwing) keeps the completion write but skips the award - marking it
        // complete is what stops a one-off mission reappearing every night; a throw would undo that too.
        if (!completed.isDaily && awardProbe.hasBeenAwarded(missionId)) return@transaction null
        pointsAwarder(completed)
        completed
    }

    suspend fun deleteMission(mission: MissionEntity) {
        missionDao.delete(mission)
    }

    /**
     * Resets daily-mission completions at most once per local day - safe to call from both the
     * midnight DecayWorker and Tasks-screen resume, gated on lastMissionResetDay.
     */
    suspend fun resetDailyIfNeeded() {
        val today = LocalDate.now().toString()
        if (dayStore.getLastMissionResetDay() == today) return
        missionDao.resetDailyCompletions()
        dayStore.setLastMissionResetDay(today)
    }
}
