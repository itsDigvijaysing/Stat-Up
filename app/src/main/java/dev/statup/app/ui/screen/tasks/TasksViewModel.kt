package dev.statup.app.ui.screen.tasks

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.statup.app.data.local.db.entity.MissionEntity
import dev.statup.app.data.local.datastore.UserPreferences
import dev.statup.app.data.repository.MissionRepository
import dev.statup.app.data.repository.PlayerRepository
import dev.statup.app.data.repository.PointsRepository
import dev.statup.app.rpg.AchievementTracker
import dev.statup.app.domain.model.StatType
import dev.statup.app.domain.model.TransactionSource
import dev.statup.app.domain.model.TransactionType
import dev.statup.app.sync.SyncResult
import dev.statup.app.sync.TodoistSyncManager
import dev.statup.app.sync.TodoistTask
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class TasksUiState(
    val missions: List<MissionEntity> = emptyList(),
    val showCreateDialog: Boolean = false,
    val isLoading: Boolean = true,
    val todoistConnected: Boolean = false,
    val todoistSyncing: Boolean = false,
    val todoistLastSync: String? = null,
    val todoistSyncLog: List<String> = emptyList(),
    val todoistTasks: List<TodoistTask> = emptyList(),
    val todoistTasksLoading: Boolean = false,
    val todoistTasksError: String? = null,
    val showTodoistTasks: Boolean = false,
    val isRefreshing: Boolean = false
)

class TasksViewModel(
    private val missionRepository: MissionRepository,
    private val pointsRepository: PointsRepository,
    private val playerRepository: PlayerRepository,
    private val achievementTracker: AchievementTracker,
    private val todoistSyncManager: TodoistSyncManager,
    private val userPreferences: UserPreferences,
    private val tutorialCoordinator: dev.statup.app.ui.screen.tutorial.TutorialCoordinator
) : ViewModel() {

    private val _uiState = MutableStateFlow(TasksUiState())
    val uiState: StateFlow<TasksUiState> = _uiState.asStateFlow()

    init {
        loadMissions()
        loadTodoistStatus()
    }

    private fun loadTodoistStatus() {
        viewModelScope.launch {
            // Hydrates the encrypted-secret cache first - this ViewModel can otherwise win the
            // race against StatUpApp.loadSecretsIfNeeded() on cold start.
            userPreferences.getTodoistToken()
            // Collects rather than reads once: this ViewModel survives tab switches, so a
            // one-shot read missed a token added in Settings until app restart.
            var wasConnected = false
            combine(
                userPreferences.todoistToken,
                userPreferences.lastSyncTime
            ) { token, lastSync ->
                !token.isNullOrBlank() to lastSync
            }.collect { (isConnected, lastSync) ->
                val lastSyncStr = if (lastSync > 0) {
                    SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault()).format(Date(lastSync))
                } else null
                _uiState.update {
                    it.copy(
                        todoistConnected = isConnected,
                        todoistLastSync = lastSyncStr
                    )
                }
                // Fetch active tasks when the connection first appears (or re-appears).
                if (isConnected && !wasConnected) {
                    loadTodoistTasks()
                }
                wasConnected = isConnected
            }
        }
    }

    fun loadTodoistTasks() {
        viewModelScope.launch { loadTodoistTasksSuspend() }
    }

    private suspend fun loadTodoistTasksSuspend() {
        _uiState.update { it.copy(todoistTasksLoading = true, todoistTasksError = null) }
        val result = todoistSyncManager.getActiveTasks()
        result.fold(
            onSuccess = { tasks ->
                val sortedTasks = tasks.sortedWith(
                    compareByDescending<TodoistTask> { it.priority }
                        .thenBy { it.due?.date ?: "9999-99-99" }
                )
                _uiState.update {
                    it.copy(
                        todoistTasks = sortedTasks,
                        todoistTasksLoading = false,
                        todoistTasksError = null
                    )
                }
            },
            onFailure = { error ->
                _uiState.update {
                    it.copy(
                        todoistTasksLoading = false,
                        todoistTasksError = error.message ?: "Failed to load tasks"
                    )
                }
            }
        )
    }

    fun toggleTodoistTasks() {
        _uiState.update { it.copy(showTodoistTasks = !it.showTodoistTasks) }
    }

    /** Pull-to-refresh handler: re-fetches Todoist + resets daily missions. Awaits completion. */
    fun refreshAll() {
        viewModelScope.launch {
            _uiState.update { it.copy(isRefreshing = true) }
            resetDailyMissionsSuspend()
            if (_uiState.value.todoistConnected) {
                loadTodoistTasksSuspend()
            }
            _uiState.update { it.copy(isRefreshing = false) }
        }
    }

    fun syncTodoist() {
        viewModelScope.launch {
            _uiState.update { it.copy(todoistSyncing = true) }
            val result = todoistSyncManager.syncCompletedTasks()
            val log = _uiState.value.todoistSyncLog.toMutableList()
            val timestamp = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())

            when (result) {
                is SyncResult.Success -> {
                    log.add(0, "[$timestamp] Synced ${result.tasksProcessed} tasks (+${result.pointsEarned} pts)")
                }
                is SyncResult.NotConnected -> {
                    log.add(0, "[$timestamp] Not connected - add token in Settings")
                }
                is SyncResult.AuthFailed -> {
                    log.add(0, "[$timestamp] Token rejected - reconnect in Settings")
                }
                is SyncResult.Error -> {
                    log.add(0, "[$timestamp] Error: ${result.message}")
                }
            }

            // todoistLastSync updates reactively via the lastSyncTime flow in
            // loadTodoistStatus() - no manual re-read needed here.
            _uiState.update {
                it.copy(
                    todoistSyncing = false,
                    todoistSyncLog = log.take(10)
                )
            }
            
            // Reload active tasks after sync
            loadTodoistTasks()
        }
    }

    private fun loadMissions() {
        viewModelScope.launch {
            missionRepository.missions.collect { missions ->
                _uiState.update {
                    it.copy(
                        missions = missions,
                        isLoading = false
                    )
                }
            }
        }
    }

    fun showCreateDialog() {
        _uiState.update { it.copy(showCreateDialog = true) }
    }

    fun hideCreateDialog() {
        _uiState.update { it.copy(showCreateDialog = false) }
    }

    fun createMission(
        name: String,
        description: String?,
        points: Int,
        statType: StatType,
        isDaily: Boolean
    ) {
        viewModelScope.launch {
            missionRepository.createMission(name, description, points, statType, isDaily)
            hideCreateDialog()
        }
    }

    fun completeMission(mission: MissionEntity) {
        viewModelScope.launch {
            // Completes + awards in one transaction (a double tap can't pay twice); returns null
            // when nothing was awarded (already complete, or an already-paid one-off).
            val completed = missionRepository.completeMission(mission.id) ?: return@launch

            // Best-effort: an achievement-check failure must not crash mission completion
            // (the mission points were already awarded atomically above).
            runCatching { achievementTracker.onPointsEarned(TransactionSource.MISSION) }

            // Must run after the achievement check: the tutorial step is gated on the first-task
            // payout having landed, and quotes that exact balance to the user.
            tutorialCoordinator.onTaskCompleted(completed)
        }
    }

    fun deleteMission(mission: MissionEntity) {
        viewModelScope.launch {
            missionRepository.deleteMission(mission)
        }
    }

    fun resetDailyMissions() {
        viewModelScope.launch { resetDailyMissionsSuspend() }
    }

    private suspend fun resetDailyMissionsSuspend() {
        // Same once-per-local-day guarded reset the midnight DecayWorker calls, so a tab-open
        // trigger and background work can't double-reset or skip a day.
        missionRepository.resetDailyIfNeeded()
    }
}
