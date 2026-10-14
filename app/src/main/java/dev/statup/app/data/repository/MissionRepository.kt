package dev.statup.app.data.repository

import dev.statup.app.data.local.db.dao.MissionDao
import dev.statup.app.data.local.db.entity.MissionEntity
import dev.statup.app.domain.model.StatType
import dev.statup.app.rpg.MissionStreak
import dev.statup.app.rpg.Transactor
import kotlinx.coroutines.flow.Flow
import java.time.Clock
import java.time.LocalDate

/**
 * Whether a mission has already been paid for (implemented by `PointsRepository`); only consulted
 * for one-off missions, to catch rows an old nightly reset had un-completed after paying out.
 */
interface MissionAwardProbe {
    suspend fun hasBeenAwarded(missionId: Long): Boolean
    suspend fun awardTimes(missionId: Long): List<Long>
}

/** Gates the one-time rebuild of mission streaks that were stored as lifetime completion counts. */
interface MissionStreakStore {
    suspend fun isMissionStreakRebuilt(): Boolean
    suspend fun markMissionStreakRebuilt()
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
    private val streakStore: MissionStreakStore,
    /**
     * Awards the mission's points; a lambda rather than an injected `PointsRepository` to avoid
     * closing a Koin cycle (PointsRepository -> ... -> MissionRepository) - same as `AchievementRepository`.
     */
    private val pointsAwarder: suspend (MissionEntity) -> Unit,
    private val clock: Clock = Clock.systemDefaultZone()
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
        val before = missionDao.getById(missionId) ?: return@transaction null
        val streak = MissionStreak.afterCompletion(
            previous = before.streak,
            lastCompletedDay = before.lastCompletedAt?.let { MissionStreak.dayOf(it, clock.zone) },
            today = LocalDate.now(clock)
        )
        if (missionDao.completeIfNotDone(missionId, clock.millis(), streak) == 0) return@transaction null
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
        val today = LocalDate.now(clock).toString()
        if (dayStore.getLastMissionResetDay() == today) return
        missionDao.resetDailyCompletions()
        dayStore.setLastMissionResetDay(today)
    }

    /** Builds before this fix stored lifetime completion counts; derive the real run from award history. */
    suspend fun rebuildStreaksIfNeeded() {
        if (streakStore.isMissionStreakRebuilt()) return
        val today = LocalDate.now(clock)
        transactor.transaction {
            for (mission in missionDao.getAllOnce()) {
                val days = awardProbe.awardTimes(mission.id).map { MissionStreak.dayOf(it, clock.zone) }
                missionDao.setStreak(mission.id, MissionStreak.fromCompletionDays(days, today))
            }
        }
        streakStore.markMissionStreakRebuilt()
    }
}
