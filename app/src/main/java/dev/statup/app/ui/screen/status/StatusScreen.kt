package dev.statup.app.ui.screen.status

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.navigation.NavController
import dev.statup.app.domain.model.Rank
import dev.statup.app.domain.model.StatType
import dev.statup.app.ui.components.glass.*
import dev.statup.app.ui.components.rpg.DailyQuoteCard
import dev.statup.app.ui.components.StatPickerCaption
import dev.statup.app.ui.components.rememberDefaultStat
import dev.statup.app.ui.components.rememberStatSuggestion
import dev.statup.app.ui.components.rememberHapticTick
import dev.statup.app.ui.components.rpg.RankUpAnimation
import dev.statup.app.ui.components.rpg.StatusWindow
import dev.statup.app.ui.navigation.Routes
import dev.statup.app.ui.theme.*
import org.koin.androidx.compose.koinViewModel

@Composable
fun StatusScreen(
    navController: NavController,
    viewModel: StatusViewModel = koinViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var showMoodDialog by remember { mutableStateOf(false) }
    var showAddPointsDialog by remember { mutableStateOf(false) }
    var showTitlePicker by remember { mutableStateOf(false) }
    var showShieldDialog by remember { mutableStateOf(false) }
    var showRankUpAnimation by remember { mutableStateOf<Rank?>(null) }
    val hapticTick = rememberHapticTick()

    // Listen for rank-up events
    LaunchedEffect(Unit) {
        viewModel.rankUpEvent.collect { newRank ->
            showRankUpAnimation = newRank
            hapticTick()
        }
    }

    // Show rank-up animation
    showRankUpAnimation?.let { rank ->
        RankUpAnimation(
            newRank = rank,
            onDismiss = { showRankUpAnimation = null }
        )
    }

    val hexStyle = when (uiState.hexagonStyle) {
        "glow" -> dev.statup.app.ui.components.rpg.HexagonStyle.GLOW
        else -> dev.statup.app.ui.components.rpg.HexagonStyle.SIMPLE
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Status Window (includes points now)
        StatusWindow(
            playerName = uiState.username,
            stats = uiState.stats,
            availablePoints = uiState.currentBalance,
            hexagonStyle = hexStyle,
            equippedTitle = uiState.equippedTitle,
            onTitleClick = { showTitlePicker = true },
            onHistoryClick = { navController.navigate(Routes.HISTORY) }
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Daily quote - "system message of the day" under the character sheet. Hidden
        // until resolved; source configurable in Settings (offline pack by default).
        uiState.dailyQuote?.let { quote ->
            DailyQuoteCard(quote = quote)
            Spacer(modifier = Modifier.height(16.dp))
        }

        // Quick Actions
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            QuickActionCard(
                emoji = if (uiState.hasCheckedInMoodToday) "✅" else "😊",
                label = "Mood",
                sublabel = if (uiState.hasCheckedInMoodToday) "Done today" else "+2 pts",
                modifier = Modifier.weight(1f),
                enabled = !uiState.hasCheckedInMoodToday,
                onClick = { if (!uiState.hasCheckedInMoodToday) showMoodDialog = true }
            )
            QuickActionCard(
                emoji = "➕",
                label = "Add",
                sublabel = "Points",
                modifier = Modifier.weight(1f),
                onClick = { showAddPointsDialog = true }
            )
            QuickActionCard(
                emoji = "📜",
                label = "History",
                sublabel = "View all",
                modifier = Modifier.weight(1f),
                onClick = { navController.navigate(Routes.HISTORY) }
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Secondary Actions Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            QuickActionCard(
                emoji = "📊",
                label = "Stats",
                sublabel = "Details",
                modifier = Modifier.weight(1f),
                onClick = { navController.navigate(Routes.FULL_STATS) }
            )
            QuickActionCard(
                emoji = "🏆",
                label = "Achieve",
                sublabel = "Titles",
                modifier = Modifier.weight(1f),
                onClick = { navController.navigate(Routes.ACHIEVEMENTS) }
            )
            QuickActionCard(
                emoji = "🛡️",
                label = "Shield",
                sublabel = "x${uiState.stats.streakShields} held",
                modifier = Modifier.weight(1f),
                onClick = { showShieldDialog = true }
            )
        }
    }

    // Mood Check-in Dialog
    if (showMoodDialog) {
        MoodCheckInDialog(
            onDismiss = { showMoodDialog = false },
            onMoodSelected = { mood ->
                viewModel.checkInMood(mood)
                hapticTick()
                showMoodDialog = false
            }
        )
    }

    // Add Points Dialog
    if (showAddPointsDialog) {
        AddPointsDialog(
            onDismiss = { showAddPointsDialog = false },
            onAdd = { points, stat, description ->
                viewModel.addManualPoints(points, stat, description)
                showAddPointsDialog = false
            }
        )
    }

    // Streak Shield Dialog - explain + buy.
    if (showShieldDialog) {
        ShieldDialog(
            shieldsHeld = uiState.stats.streakShields,
            balance = uiState.currentBalance,
            message = uiState.shieldMessage,
            onBuy = { viewModel.buyShield(); hapticTick() },
            onDismiss = {
                viewModel.dismissShieldMessage()
                showShieldDialog = false
            }
        )
    }

    // Title Picker Dialog - equip an unlocked achievement title (or none).
    if (showTitlePicker) {
        TitlePickerDialog(
            titles = uiState.unlockedTitles,
            equippedTitle = uiState.equippedTitle,
            onDismiss = { showTitlePicker = false },
            onSelect = { titleId ->
                viewModel.equipTitle(titleId)
                showTitlePicker = false
            }
        )
    }
}

@Composable
private fun ShieldDialog(
    shieldsHeld: Int,
    balance: Int,
    message: String?,
    onBuy: () -> Unit,
    onDismiss: () -> Unit
) {
    val maxShields = dev.statup.app.domain.model.PlayerStats.MAX_SHIELDS
    val cost = dev.statup.app.domain.model.PlayerStats.SHIELD_COST
    val atMax = shieldsHeld >= maxShields

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties()) {
        GlassCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "🛡️ Streak Freeze Shield",
                    color = TextPrimary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = Inter
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Skip a day without losing anything. One shield absorbs the idle " +
                        "day automatically - no stat decay, streak and work days intact.",
                    color = TextSecondary,
                    fontSize = 13.sp,
                    fontFamily = Inter,
                    lineHeight = 18.sp
                )
                Spacer(modifier = Modifier.height(16.dp))
                // Three fixed columns rather than one dot-separated line: that line wrapped
                // mid-value on narrow screens ("Balance: 632" / "pts"), which read as broken.
                Row(modifier = Modifier.fillMaxWidth()) {
                    listOf(
                        "Held" to "$shieldsHeld / $maxShields",
                        "Cost" to "$cost pts",
                        "Balance" to "$balance pts"
                    ).forEach { (label, value) ->
                        Column(
                            modifier = Modifier.weight(1f),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = label,
                                color = TextTertiary,
                                fontSize = 11.sp,
                                fontFamily = Inter
                            )
                            Text(
                                text = value,
                                color = TextSecondary,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                fontFamily = Inter,
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    }
                }

                message?.let {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = it,
                        color = PointsGold,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        fontFamily = Inter
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    GlassButton(
                        text = "Close",
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f)
                    )
                    GlassButton(
                        text = if (atMax) "Max held" else "Buy",   // cost already shown in the stats row above
                        onClick = onBuy,
                        enabled = !atMax && balance >= cost,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
private fun TitlePickerDialog(
    titles: List<dev.statup.app.data.local.db.entity.TitleEntity>,
    equippedTitle: String?,
    onDismiss: () -> Unit,
    onSelect: (String?) -> Unit
) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties()) {
        GlassCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(8.dp)) {
                Text(
                    text = "Equip a Title",
                    color = TextPrimary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = Inter
                )
                Text(
                    text = if (titles.isEmpty())
                        "Unlock achievements to earn titles"
                    else "Displayed under your name on the status window",
                    color = TextSecondary,
                    fontSize = 12.sp,
                    fontFamily = Inter
                )

                Spacer(modifier = Modifier.height(12.dp))

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 360.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // "No title" entry
                    TitlePickerRow(
                        label = "No title",
                        sublabel = "Keep it clean",
                        selected = equippedTitle == null,
                        onClick = { onSelect(null) }
                    )
                    titles.forEach { title ->
                        TitlePickerRow(
                            label = listOfNotNull(title.emoji, title.name).joinToString(" "),
                            sublabel = title.description,
                            selected = equippedTitle != null &&
                                equippedTitle == listOfNotNull(title.emoji, title.name).joinToString(" "),
                            onClick = { onSelect(title.id) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TitlePickerRow(
    label: String,
    sublabel: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    GlassCard(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = label,
                    color = if (selected) PointsGold else TextPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = Inter
                )
                Text(
                    text = sublabel,
                    color = TextTertiary,
                    fontSize = 11.sp,
                    fontFamily = Inter
                )
            }
            if (selected) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = "Equipped",
                    tint = PointsGold
                )
            }
        }
    }
}

@Composable
private fun QuickActionCard(
    emoji: String,
    label: String,
    sublabel: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    val cardAlpha = if (enabled) 1f else 0.55f
    GlassCard(
        modifier = modifier.alpha(cardAlpha),
        onClick = if (enabled) onClick else null
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = emoji,
                fontSize = 24.sp
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = label,
                color = TextPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                fontFamily = Inter
            )
            Text(
                text = sublabel,
                color = TextTertiary,
                fontSize = 10.sp,
                fontFamily = Inter
            )
        }
    }
}

@Composable
private fun MoodCheckInDialog(
    onDismiss: () -> Unit,
    onMoodSelected: (String) -> Unit
) {
    val moods = listOf(
        "😊" to "Happy",
        "😌" to "Calm",
        "😤" to "Motivated",
        "😢" to "Sad",
        "😴" to "Tired",
        "😎" to "Focused"
    )

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
                modifier = Modifier.fillMaxWidth(),
                elevated = true
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Text(
                        text = "How are you feeling?",
                        color = TextPrimary,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = Inter
                    )
                    Text(
                        text = "Earn +2 WIS points",
                        color = AccentPrimary,
                        fontSize = 14.sp,
                        fontFamily = Inter
                    )

                    Spacer(modifier = Modifier.height(20.dp))

                    // Plain Rows, not a LazyVerticalGrid pinned to 160.dp - that height clipped
                    // labels at larger font scales; Rows wrap to content at any size.
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        moods.chunked(3).forEach { row ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                row.forEach { (emoji, label) ->
                                    Column(
                                        modifier = Modifier.weight(1f),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        GlassIconButton(
                                            icon = { Text(text = emoji, fontSize = 28.sp) },
                                            onClick = { onMoodSelected(label) },
                                            size = 56.dp
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = label,
                                            color = TextSecondary,
                                            fontSize = 11.sp,
                                            fontFamily = Inter,
                                            textAlign = TextAlign.Center
                                        )
                                    }
                                }
                                // Keeps a short final row column-aligned with the row above.
                                repeat(3 - row.size) { Spacer(modifier = Modifier.weight(1f)) }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    GlassButton(
                        text = "Cancel",
                        onClick = onDismiss,
                        primary = false,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}

@Composable
private fun AddPointsDialog(
    onDismiss: () -> Unit,
    onAdd: (points: Int, stat: StatType, description: String) -> Unit
) {
    var points by remember { mutableStateOf("4") }
    var description by remember { mutableStateOf("") }

    // Same rule as the mission dialog: default stat, then the offline guess, then whatever the
    // user picks - their pick always wins.
    val defaultStat = rememberDefaultStat()
    var pickedStat by remember { mutableStateOf<StatType?>(null) }
    val suggestion = rememberStatSuggestion(description)
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
                modifier = Modifier.fillMaxWidth(),
                elevated = true
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Text(
                        text = "Add Points",
                        color = TextPrimary,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = Inter
                    )
                    Text(
                        text = "Manually add reward points",
                        color = TextSecondary,
                        fontSize = 14.sp,
                        fontFamily = Inter
                    )

                    Spacer(modifier = Modifier.height(20.dp))

                    // Points selector - 4 in row
                    Text(
                        text = "Points Amount:",
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

                    // Description
                    GlassTextField(
                        value = description,
                        onValueChange = { description = it },
                        label = "Description (optional)",
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(20.dp))

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
                            text = "Add +${points}",
                            onClick = {
                                onAdd(
                                    points.toIntOrNull() ?: 4,
                                    selectedStat,
                                    description.ifBlank { "Manual entry" }
                                )
                            },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
    }
}
