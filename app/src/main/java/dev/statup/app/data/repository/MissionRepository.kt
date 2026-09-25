package dev.statup.app.data.repository

import dev.statup.app.data.local.db.dao.MissionDao
import dev.statup.app.data.local.db.entity.MissionEntity
import dev.statup.app.domain.model.StatType
import dev.statup.app.rpg.Transactor
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

/**
 * Whether a mission has already been paid for. Implemented by `PointsRepository`.
 *
 * Only consulted for one-off missions, to catch rows the pre-fix nightly reset had already
 * un-completed after paying out - see [MissionRepository.completeMission].
 */
interface MissionAwardProbe {
    suspend fun hasBeenAwarded(missionId: Long): Boolean
}

/**
 * Local-day marker for the once-per-day daily reset. Implemented by `UserPreferences`; a narrow port
 * rather than the concrete class so this repository is unit-testable without a `Context`, the same
 * shape as `DecayDayStore`.
 */
interface MissionResetDayStore {
    suspend fun getLastMissionResetDay(): String?
    suspend fun setLastMissionResetDay(day: String)
}

/**
 * Owns mission CRUD, the completion guard, and the daily-completion reset. Extracted from
 * TasksViewModel so the reset can run from background work (DecayWorker) - not just when the Tasks
 * screen happens to be open - and so the completion guard lives in one place.
 */
class MissionRepository(
    private val missionDao: MissionDao,
    private val dayStore: MissionResetDayStore,
    private val transactor: Transactor,
    private val awardProbe: MissionAwardProbe,
    /**
     * Awards the mission's points. A lambda rather than an injected `PointsRepository` because that
     * would close a Koin cycle (PointsRepository -> ... -> MissionRepository); the same approach is
     * used for `AchievementRepository`'s pointsAwarder.
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
     * Completes a mission and awards its points atomically. Returns the completed mission, or null
     * if nothing was awarded.
     *
     * Everything runs in **one transaction**, which matters in both directions: a second tap cannot
     * win the conditional UPDATE, so points can never be awarded twice; and if the award throws, the
     * completion rolls back with it, so the mission can be tapped again rather than being marked
     * done for free. The previous version did the read, the write and the award as three separate
     * steps, so it could do either.
     *
     * The extra probe applies to **one-off missions only**. Builds before the `isDaily = 1` fix to
     * `resetDailyCompletions` un-completed one-offs every midnight after paying them, so such a row
     * can look incomplete while already having been awarded. A daily mission is deliberately exempt:
     * it is supposed to pay again every day, so probing it would break the core loop.
     */
    suspend fun completeMission(missionId: Long): MissionEntity? = transactor.transaction {
        if (missionDao.completeIfNotDone(missionId) == 0) return@transaction null
        val completed = missionDao.getById(missionId) ?: return@transaction null
        // Deliberately keeps the completion and skips only the award: leaving such a row marked
        // complete is what stops it reappearing every night. Returning early (not throwing) is what
        // preserves that write - a throw would roll it back.
        if (!completed.isDaily && awardProbe.hasBeenAwarded(missionId)) return@transaction null
        pointsAwarder(completed)
        completed
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
        if (dayStore.getLastMissionResetDay() == today) return
        missionDao.resetDailyCompletions()
        dayStore.setLastMissionResetDay(today)
    }
}
