package dev.statup.app.ui.screen.tasks

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.*
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.navigation.NavController
import dev.statup.app.data.local.db.entity.MissionEntity
import dev.statup.app.domain.model.PlayerStats
import dev.statup.app.domain.model.StatType
import dev.statup.app.sync.TodoistTask
import dev.statup.app.ui.components.glass.*
import dev.statup.app.ui.components.StatPickerCaption
import dev.statup.app.ui.components.rememberDefaultStat
import dev.statup.app.ui.components.rememberStatSuggestion
import dev.statup.app.ui.components.rememberHapticTick
import dev.statup.app.ui.components.HelpDialog
import dev.statup.app.ui.components.HelpIconButton
import dev.statup.app.ui.components.HelpPoint
import dev.statup.app.ui.theme.*
import org.koin.androidx.compose.koinViewModel

@Composable
fun TasksScreen(
    navController: NavController,
    viewModel: TasksViewModel = koinViewModel()
) {
    var showHelp by remember { mutableStateOf(false) }
    val uiState by viewModel.uiState.collectAsState()
    val lifecycleOwner = LocalLifecycleOwner.current
    val hapticTick = rememberHapticTick()

    // Re-run on every tab re-entry (not just initial composition).
    // - resetDailyMissions: catches midnight rollover while app stays open
    // - loadTodoistTasks: gives user fresh active-task list every time they open the tab
    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            viewModel.resetDailyMissions()
            if (uiState.todoistConnected) {
                viewModel.loadTodoistTasks()
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Tasks & Missions",
                    color = TextPrimary,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = Inter
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    HelpIconButton(onClick = { showHelp = true })
                    GlassIconButton(
                        icon = {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = "Add Task",
                                tint = AccentPrimary
                            )
                        },
                        onClick = { viewModel.showCreateDialog() }
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (uiState.isLoading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = AccentPrimary)
                }
            } else {
                TasksList(
                    uiState = uiState,
                    onCompleteMission = { viewModel.completeMission(it); hapticTick() },
                    onDeleteMission = { viewModel.deleteMission(it) },
                    onCreateMission = { viewModel.showCreateDialog() },
                    onToggleTodoist = { viewModel.toggleTodoistTasks() },
                    onRefreshTodoist = { viewModel.loadTodoistTasks() },
                    onSyncTodoist = { viewModel.syncTodoist() },
                    onPullRefresh = { viewModel.refreshAll() }
                )
            }
        }

        if (showHelp) TasksHelpDialog(onDismiss = { showHelp = false })

        // Create Dialog
        if (uiState.showCreateDialog) {
            CreateMissionDialog(
                onDismiss = { viewModel.hideCreateDialog() },
                onCreate = { name, desc, points, stat, isDaily ->
                    viewModel.createMission(name, desc, points, stat, isDaily)
                }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TasksList(
    uiState: TasksUiState,
    onCompleteMission: (MissionEntity) -> Unit,
    onDeleteMission: (MissionEntity) -> Unit,
    onCreateMission: () -> Unit,
    onToggleTodoist: () -> Unit,
    onRefreshTodoist: () -> Unit,
    onSyncTodoist: () -> Unit,
    onPullRefresh: () -> Unit
) {
    val activeMissions = uiState.missions.filter { !it.isCompletedToday }
    val completedMissions = uiState.missions.filter { it.isCompletedToday }

    PullToRefreshBox(
        isRefreshing = uiState.isRefreshing,
        onRefresh = onPullRefresh,
        state = rememberPullToRefreshState(),
        modifier = Modifier.fillMaxSize()
    ) {
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(bottom = 100.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            // 1. Active missions first — the primary surface
            if (activeMissions.isNotEmpty()) {
                item {
                    Text(
                        text = "Active (${activeMissions.size})",
                        color = TextSecondary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        fontFamily = Inter
                    )
                }
                items(activeMissions, key = { it.id }) { mission ->
                    MissionCard(
                        mission = mission,
                        onComplete = { onCompleteMission(mission) },
                        onDelete = { onDeleteMission(mission) }
                    )
                }
            }

            // 2. Completed today
            if (completedMissions.isNotEmpty()) {
                item {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Completed Today (${completedMissions.size})",
                        color = TextTertiary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        fontFamily = Inter
                    )
                }
                items(completedMissions, key = { it.id }) { mission ->
                    MissionCard(
                        mission = mission,
                        onComplete = { },
                        onDelete = { onDeleteMission(mission) },
                        isCompleted = true
                    )
                }
            }

            // 3. Empty state if both lists empty AND no Todoist
            if (uiState.missions.isEmpty() && !uiState.todoistConnected) {
                item {
                    EmptyTasksState(onCreateClick = onCreateMission)
                }
            }

            // 4. Todoist section — at the bottom, collapsed by default
            if (uiState.todoistConnected) {
                item {
                    Spacer(modifier = Modifier.height(16.dp))
                    TodoistSection(
                        tasks = uiState.todoistTasks,
                        isLoading = uiState.todoistTasksLoading,
                        error = uiState.todoistTasksError,
                        isExpanded = uiState.showTodoistTasks,
                        onToggle = onToggleTodoist,
                        onRefresh = onRefreshTodoist,
                        lastSync = uiState.todoistLastSync,
                        isSyncing = uiState.todoistSyncing,
                        syncLog = uiState.todoistSyncLog,
                        onSync = onSyncTodoist
                    )
                }
            }
        }
    }
}

@Composable
private fun TodoistSection(
    tasks: List<TodoistTask>,
    isLoading: Boolean,
    error: String?,
    isExpanded: Boolean,
    onToggle: () -> Unit,
    onRefresh: () -> Unit,
    lastSync: String?,
    isSyncing: Boolean,
    syncLog: List<String>,
    onSync: () -> Unit
) {
    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Header — tap to expand/collapse
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { onToggle() }
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(text = "📋", fontSize = 20.sp)
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = "Todoist",
                            color = TextPrimary,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold,
                            fontFamily = Inter
                        )
                        Text(
                            text = "${tasks.size} tasks • ${lastSync ?: "Never synced"}",
                            color = TextTertiary,
                            fontSize = 11.sp,
                            fontFamily = Inter
                        )
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (isSyncing || isLoading) {
                        CircularProgressIndicator(
                            color = AccentPrimary,
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        GlassButtonSmall(
                            text = "Sync",
                            onClick = onSync
                        )
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(
                        imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                        contentDescription = if (isExpanded) "Collapse" else "Expand",
                        tint = TextSecondary
                    )
                }
            }

            // Error
            if (error != null) {
                Text(
                    text = error,
                    color = AccentError,
                    fontSize = 12.sp,
                    fontFamily = Inter,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }

            // Expanded content
            AnimatedVisibility(
                visible = isExpanded,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Sync log (compact)
                    if (syncLog.isNotEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color.White.copy(alpha = 0.04f))
                                .padding(8.dp)
                        ) {
                            Column {
                                syncLog.take(3).forEach { entry ->
                                    Text(
                                        text = entry,
                                        color = TextTertiary,
                                        fontSize = 10.sp,
                                        fontFamily = Inter
                                    )
                                }
                            }
                        }
                    }

                    // Active tasks
                    if (tasks.isNotEmpty()) {
                        tasks.take(10).forEach { task ->
                            TodoistTaskCard(task = task)
                        }
                        if (tasks.size > 10) {
                            Text(
                                text = "+${tasks.size - 10} more tasks",
                                color = TextTertiary,
                                fontSize = 12.sp,
                                fontFamily = Inter,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyTasksState(onCreateClick: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        GlassCard(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(text = "📋", fontSize = 56.sp)
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "No Tasks Yet",
                    color = TextPrimary,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = Inter
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Create custom missions to earn points\nor connect Todoist in Settings",
                    color = TextSecondary,
                    fontSize = 14.sp,
                    fontFamily = Inter,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(24.dp))
                GlassButton(
                    text = "+ Create Mission",
                    onClick = onCreateClick,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Composable
private fun MissionCard(
    mission: MissionEntity,
    onComplete: () -> Unit,
    onDelete: () -> Unit,
    isCompleted: Boolean = false
) {
    val statType = try {
        StatType.valueOf(mission.statType)
    } catch (e: Exception) {
        StatType.STR
    }

    val statColor = when (statType) {
        StatType.STR -> StatStrength
        StatType.INT -> StatIntelligence
        StatType.WIS -> StatWisdom
        StatType.DEX -> StatDexterity
        StatType.CHA -> StatCharisma
        StatType.VIT -> StatVitality
    }

    GlassCard(
        modifier = Modifier.fillMaxWidth(),
        onClick = if (!isCompleted) onComplete else null
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Stat indicator
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                statColor.copy(alpha = 0.3f),
                                statColor.copy(alpha = 0.1f)
                            )
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (isCompleted) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = "Completed",
                        tint = AccentSuccess,
                        modifier = Modifier.size(24.dp)
                    )
                } else {
                    Text(
                        text = statType.name,
                        color = statColor,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.width(16.dp))

            // Mission info
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = mission.name,
                    color = if (isCompleted) TextTertiary else TextPrimary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = Inter,
                    textDecoration = if (isCompleted) TextDecoration.LineThrough else null
                )
                if (!mission.description.isNullOrBlank()) {
                    Text(
                        text = mission.description,
                        color = TextTertiary,
                        fontSize = 13.sp,
                        fontFamily = Inter
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "+${mission.pointsReward} pts",
                        color = AccentPrimary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        fontFamily = Inter
                    )
                    if (mission.isDaily) {
                        Text(
                            text = " • Daily",
                            color = TextTertiary,
                            fontSize = 12.sp,
                            fontFamily = Inter
                        )
                    }
                    if (mission.streak > 0) {
                        Text(
                            text = " • 🔥 ${mission.streak}",
                            color = AccentWarning,
                            fontSize = 12.sp
                        )
                    }
                }
            }

            // Delete button
            IconButton(onClick = onDelete) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Delete",
                    tint = TextTertiary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
private fun CreateMissionDialog(
    onDismiss: () -> Unit,
    onCreate: (name: String, desc: String?, points: Int, stat: StatType, isDaily: Boolean) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var points by remember { mutableStateOf("4") }
    var isDaily by remember { mutableStateOf(true) }

    // The picker used to open hardcoded on STR regardless of the task. It now starts on the
    // user's default stat and moves to the classifier's guess until they pick one themselves —
    // after which their choice is never overridden.
    val defaultStat = rememberDefaultStat()
    var pickedStat by remember { mutableStateOf<StatType?>(null) }
    val suggestion = rememberStatSuggestion(name)
    val selectedStat = pickedStat ?: suggestion?.stat ?: defaultStat

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(BackgroundBase.copy(alpha = 0.92f))
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            GlassCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .wrapContentHeight(),
                elevated = true
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp)
                ) {
                    Text(
                        text = "Create Mission",
                        color = TextPrimary,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = Inter
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Name field
                    GlassTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = "Mission Name",
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Description field
                    GlassTextField(
                        value = description,
                        onValueChange = { description = it },
                        label = "Description (optional)",
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Points selector - 4 in row
                    Text(
                        text = "Points:",
                        color = TextSecondary,
                        fontSize = 14.sp,
                        fontFamily = Inter
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf("1", "2", "3", "4").forEach { p ->
                            GlassButtonSmall(
                                text = "+$p",
                                onClick = { points = p },
                                primary = points == p,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Stat selector - 3x2 Grid
                    Text(
                        text = "Target Stat:",
                        color = TextSecondary,
                        fontSize = 14.sp,
                        fontFamily = Inter
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(3),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(100.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        userScrollEnabled = false
                    ) {
                        items(StatType.entries.toList()) { stat ->
                            val isSelected = selectedStat == stat
                            GlassButtonSmall(
                                text = stat.name,
                                onClick = { pickedStat = stat },
                                primary = isSelected,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    StatPickerCaption(
                        stat = selectedStat,
                        isSuggested = pickedStat == null && suggestion != null
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Daily toggle
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Repeats Daily",
                            color = TextSecondary,
                            fontSize = 14.sp,
                            fontFamily = Inter,
                            modifier = Modifier.weight(1f)
                        )
                        Switch(
                            checked = isDaily,
                            onCheckedChange = { isDaily = it },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = AccentPrimary,
                                checkedTrackColor = AccentPrimary.copy(alpha = 0.3f),
                                uncheckedThumbColor = TextTertiary,
                                uncheckedTrackColor = GlassFill
                            )
                        )
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    // Buttons
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        GlassButton(
                            text = "Cancel",
                            onClick = onDismiss,
                            primary = false,
                            modifier = Modifier.weight(1f)
                        )
                        GlassButton(
                            text = "Create",
                            onClick = {
                                if (name.isNotBlank()) {
                                    onCreate(
                                        name,
                                        description.takeIf { it.isNotBlank() },
                                        points.toIntOrNull() ?: 4,
                                        selectedStat,
                                        isDaily
                                    )
                                }
                            },
                            enabled = name.isNotBlank(),
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TodoistTaskCard(task: TodoistTask) {
    val priorityColor = when (task.priority) {
        4 -> AccentError      // P1 (urgent/red)
        3 -> AccentWarning     // P2 (orange)
        2 -> AccentPrimary     // P3 (blue)
        else -> TextTertiary   // P4/none
    }
    
    val priorityLabel = when (task.priority) {
        4 -> "P1"
        3 -> "P2"
        2 -> "P3"
        else -> "P4"
    }
    
    val pointsForTask = when (task.priority) {
        4 -> 4
        3 -> 3
        2 -> 2
        else -> 1
    }
    
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(Color.White.copy(alpha = 0.04f))
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Priority badge
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(4.dp))
                .background(priorityColor.copy(alpha = 0.2f))
                .padding(horizontal = 6.dp, vertical = 2.dp)
        ) {
            Text(
                text = priorityLabel,
                color = priorityColor,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = Inter
            )
        }
        
        Spacer(modifier = Modifier.width(10.dp))
        
        // Task content
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = task.content,
                color = TextPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                fontFamily = Inter,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            
            // Due date if present
            task.due?.let { due ->
                val dueText = due.string ?: due.date ?: ""
                if (dueText.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "📅 $dueText",
                        color = TextTertiary,
                        fontSize = 11.sp,
                        fontFamily = Inter
                    )
                }
            }
            
            // Labels if present
            if (task.labels.isNotEmpty()) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "🏷️ ${task.labels.joinToString(", ")}",
                    color = TextTertiary,
                    fontSize = 10.sp,
                    fontFamily = Inter,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        
        // Points indicator
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(4.dp))
                .background(AccentPrimary.copy(alpha = 0.15f))
                .padding(horizontal = 6.dp, vertical = 2.dp)
        ) {
            Text(
                text = "+$pointsForTask",
                color = AccentPrimary,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                fontFamily = Inter
            )
        }
    }
}

private val TASKS_HELP = listOf(
    HelpPoint("Add a mission", "Tap +, give it points, pick the stat it trains.", "\"Gym 30 min\" \u2192 4 pts to STR"),
    HelpPoint("Finish it to earn", "Points grow that stat. ${PlayerStats.POINTS_PER_STAT} points = +1 stat."),
    HelpPoint("Dailies reset nightly", "Tick them off each day to keep earning.")
)

@Composable
private fun TasksHelpDialog(onDismiss: () -> Unit) {
    HelpDialog(
        title = "Tasks & Missions",
        intro = "Missions are the things you want to do. Finishing them earns points, and points grow your stats.",
        points = TASKS_HELP,
        onDismiss = onDismiss
    )
}
