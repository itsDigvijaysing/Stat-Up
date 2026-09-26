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
            // /tasks/completed is hard-capped at 200, no cursor; dedupe makes re-reads free.
            // Don't switch to by_completion_date - it keys by TASK id and would re-award history.
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
                        // Resolution order: mapped label wins, then the offline classifier, then
                        // null (unchanged from before) - this can only add stats, never mis-assign.
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

                    // Run once after the loop, not per task - onPointsEarned uses cumulative totals so
                    // this is equivalent, and a thrown check can't turn a successful sync into a false failure.
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
