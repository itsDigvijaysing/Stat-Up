package dev.statup.app.sync

import dev.statup.app.ai.classifier.TaskClassifier
import dev.statup.app.data.local.datastore.UserPreferences
import dev.statup.app.data.repository.PointsRepository
import dev.statup.app.domain.model.TransactionSource
import dev.statup.app.rpg.AchievementTracker
import dev.statup.app.rpg.StatsEngine
import kotlinx.coroutines.flow.first

class TodoistSyncManager(
    private val todoistApi: TodoistApi,
    private val userPreferences: UserPreferences,
    private val pointsRepository: PointsRepository,
    private val achievementTracker: AchievementTracker,
    private val taskClassifier: TaskClassifier
) {

    suspend fun syncCompletedTasks(): SyncResult {
        val token = userPreferences.getTodoistToken()
        if (token.isNullOrBlank()) {
            return SyncResult.NotConnected
        }

        return try {
            // Always request the endpoint's maximum. /tasks/completed is hard-capped at 200
            // and exposes no cursor (measured 2026-08-21: response is {items, projects,
            // sections} with no next_cursor), so there is nothing to gain by asking for less -
            // and asking for only 30 meant a burst of completions between syncs could fall off
            // the end. externalId dedupe makes the re-read free.
            //
            // NOTE: 200 is therefore a ceiling on total importable history via this endpoint.
            // Reaching further back needs /tasks/completed/by_completion_date, which pages in
            // 3-month windows but keys items by TASK id where this one keys by COMPLETION id -
            // switching would re-award everything already imported. See docs/ for the plan.
            val result = todoistApi.getCompletedTasks(token, limit = MAX_COMPLETED_PER_SYNC)

            result.fold(
                onSuccess = { tasks ->
                    // Cache stat mappings once per sync (avoid N round trips for N tasks)
                    val mappingsCache = pointsRepository.loadStatMappings()
                    // Read once per run, not per task. When disabled the classifier is never
                    // consulted and unlabelled tasks arrive with no stat, exactly as before.
                    val autoCategorise = userPreferences.isAutoCategoriseEnabled()
                    // Resolved at most once per sync run, and only if a labelled task actually
                    // needs it (it is a DataStore read, not free).
                    var cachedDefaultStat: dev.statup.app.domain.model.StatType? = null
                    suspend fun defaultStat() =
                        cachedDefaultStat ?: pointsRepository.getDefaultStat().also { cachedDefaultStat = it }
                    var pointsEarned = 0
                    var tasksProcessed = 0

                    tasks.forEach { completedTask ->
                        val externalId = completedTask.stableId
                        if (externalId.isBlank()) return@forEach

                        val points = StatsEngine.calculateTaskPoints(completedTask.priority)
                        val labels = completedTask.labels
                        // Resolution order: a Todoist label the user mapped wins outright;
                        // otherwise the offline classifier reads the task title. An unlabelled
                        // task used to arrive with statType = null - the points landed in the
                        // balance and grew no stat at all. When the model isn't confident the
                        // behaviour is unchanged from before, so this can only add stats,
                        // never mis-assign one that was previously correct.
                        val statType = pointsRepository.routeByLabel(labels, mappingsCache)
                            ?: (if (autoCategorise) taskClassifier.classify(completedTask.content)?.stat else null)
                            ?: if (labels.isNotEmpty()) defaultStat() else null

                        // tryEarnExternalPoints handles dedup atomically via the unique index
                        // on transactions.externalId - returns null if this task was already synced.
                        val tx = pointsRepository.tryEarnExternalPoints(
                            externalId = externalId,
                            points = points,
                            statType = statType,
                            source = TransactionSource.TODOIST,
                            description = "Todoist: ${completedTask.content}"
                        )

                        if (tx != null) {
                            pointsEarned += points
                            tasksProcessed++
                        }
                    }

                    userPreferences.setLastSyncTime(System.currentTimeMillis())

                    // Run achievement checks ONCE after the loop and off the sync's critical
                    // path. onPointsEarned keys off cumulative totals (it writes absolute
                    // progress), so once-after-the-loop is identical to per-task - but a thrown
                    // achievement check can no longer downgrade an already-successful, already
                    // idempotently-awarded sync to a retry + false "sync failed" notification.
                    // It also collapses ~N achievement passes (one per task) into a single pass.
                    if (tasksProcessed > 0) {
                        runCatching { achievementTracker.onPointsEarned(TransactionSource.TODOIST) }
                    }

                    SyncResult.Success(tasksProcessed, pointsEarned)
                },
                onFailure = { error ->
                    if (error is TodoistAuthException) {
                        SyncResult.AuthFailed(error.message ?: "Invalid token")
                    } else {
                        SyncResult.Error(error.message ?: "Unknown error")
                    }
                }
            )
        } catch (e: TodoistAuthException) {
            SyncResult.AuthFailed(e.message ?: "Invalid token")
        } catch (e: Exception) {
            SyncResult.Error(e.message ?: "Sync failed")
        }
    }

    suspend fun getActiveTasks(): Result<List<TodoistTask>> {
        val token = userPreferences.getTodoistToken()
        if (token.isNullOrBlank()) {
            return Result.failure(Exception("Not connected to Todoist"))
        }
        return todoistApi.getTasks(token)
    }
}

/** Hard cap of the /tasks/completed endpoint; requesting more returns no more. */
private const val MAX_COMPLETED_PER_SYNC = 200

sealed class SyncResult {
    data object NotConnected : SyncResult()
    data class Success(val tasksProcessed: Int, val pointsEarned: Int) : SyncResult()
    data class Error(val message: String) : SyncResult()
    /** Token invalid/expired - do not retry until user re-enters token. */
    data class AuthFailed(val message: String) : SyncResult()
}
