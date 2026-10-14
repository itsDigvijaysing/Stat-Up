package dev.statup.app.ui.screen.history

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import dev.statup.app.data.repository.achievementIdFromReward
import dev.statup.app.domain.model.Achievements
import dev.statup.app.domain.model.Transaction
import dev.statup.app.domain.model.TransactionSource
import dev.statup.app.domain.model.TransactionType
import dev.statup.app.domain.model.StatType
import dev.statup.app.ui.components.glass.*
import dev.statup.app.ui.theme.*
import org.koin.androidx.compose.koinViewModel
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun HistoryScreen(
    navController: NavController,
    viewModel: HistoryViewModel = koinViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Header with calendar toggle
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "History",
                color = TextPrimary,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = Inter,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = { viewModel.toggleCalendar() }) {
                Icon(
                    imageVector = Icons.Default.CalendarMonth,
                    contentDescription = "Toggle Calendar",
                    tint = if (uiState.showCalendar) AccentPrimary else TextSecondary
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Streak Calendar Section (collapsible)
        AnimatedVisibility(
            visible = uiState.showCalendar,
            enter = expandVertically(),
            exit = shrinkVertically()
        ) {
            Column {
                StreakCalendar(
                    calendarDays = uiState.calendarDays,
                    currentStreak = uiState.currentStreak,
                    longestStreak = uiState.longestStreak,
                    month = uiState.calendarMonth,
                    year = uiState.calendarYear,
                    onPreviousMonth = { viewModel.previousMonth() },
                    onNextMonth = { viewModel.nextMonth() }
                )
                Spacer(modifier = Modifier.height(16.dp))
            }
        }

        // Stats summary
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            GlassCard(modifier = Modifier.weight(1f)) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "+${uiState.totalEarned}",
                        color = AccentSuccess,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = Inter
                    )
                    Text(
                        text = "Earned",
                        color = TextSecondary,
                        fontSize = 12.sp,
                        fontFamily = Inter
                    )
                }
            }
            GlassCard(modifier = Modifier.weight(1f)) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "-${uiState.totalSpent}",
                        color = AccentError,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = Inter
                    )
                    Text(
                        text = "Spent",
                        color = TextSecondary,
                        fontSize = 12.sp,
                        fontFamily = Inter
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Filter chips
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(HistoryFilter.entries) { filter ->
                FilterChip(
                    filter = filter,
                    isSelected = uiState.selectedFilter == filter,
                    onClick = { viewModel.setFilter(filter) }
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Transaction list
        if (uiState.isLoading) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = AccentPrimary)
            }
        } else if (uiState.filteredTransactions.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                GlassCard {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(24.dp)
                    ) {
                        Text(text = "📭", fontSize = 48.sp)
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "No transactions",
                            color = TextSecondary,
                            fontSize = 16.sp,
                            fontFamily = Inter
                        )
                    }
                }
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(bottom = 100.dp)
            ) {
                items(uiState.visibleTransactions, key = { it.id }) { transaction ->
                    TransactionCard(transaction = transaction)
                }

                if (uiState.hasMore) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            GlassButtonSmall(
                                text = "Load More (${uiState.filteredTransactions.size - uiState.visibleTransactions.size} remaining)",
                                onClick = { viewModel.loadMore() }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StreakCalendar(
    calendarDays: List<DayStatus?>,
    currentStreak: Int,
    longestStreak: Int,
    month: Int,
    year: Int,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit
) {
    val now = Calendar.getInstance()
    val isCurrentMonth = year == now.get(Calendar.YEAR) && month == now.get(Calendar.MONTH)
    val monthNames = listOf("January", "February", "March", "April", "May", "June",
        "July", "August", "September", "October", "November", "December")

    GlassCard(
        modifier = Modifier.fillMaxWidth(),
        elevated = true
    ) {
        Column(
            modifier = Modifier.padding(12.dp)
        ) {
            // Streak stats row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "🔥 $currentStreak",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (currentStreak > 0) AccentWarning else TextSecondary
                    )
                    Text(
                        text = "Current Streak",
                        fontSize = 11.sp,
                        color = TextTertiary,
                        fontFamily = Inter
                    )
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "🏆 $longestStreak",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = AccentPrimary
                    )
                    Text(
                        text = "Longest Streak",
                        fontSize = 11.sp,
                        color = TextTertiary,
                        fontFamily = Inter
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Month navigation header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onPreviousMonth) {
                    Icon(
                        imageVector = Icons.Default.ChevronLeft,
                        contentDescription = "Previous month",
                        tint = TextSecondary
                    )
                }
                Text(
                    text = "${monthNames[month]} $year",
                    color = TextPrimary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = Inter
                )
                IconButton(
                    onClick = onNextMonth,
                    enabled = !isCurrentMonth
                ) {
                    Icon(
                        imageVector = Icons.Default.ChevronRight,
                        contentDescription = "Next month",
                        tint = if (isCurrentMonth) TextTertiary.copy(alpha = 0.3f) else TextSecondary
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Day of week labels
            Row(modifier = Modifier.fillMaxWidth()) {
                listOf("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat").forEach { day ->
                    Box(
                        modifier = Modifier.weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = day,
                            color = TextTertiary,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Medium,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Calendar grid
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                calendarDays.chunked(7).forEach { week ->
                    Row(modifier = Modifier.fillMaxWidth()) {
                        week.forEach { day ->
                            Box(
                                modifier = Modifier.weight(1f),
                                contentAlignment = Alignment.Center
                            ) {
                                if (day != null) {
                                    CalendarDay(dayStatus = day)
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Legend
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(AccentSuccess)
                )
                Text(text = " Done", color = TextTertiary, fontSize = 10.sp)
                Spacer(modifier = Modifier.width(12.dp))
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(AccentError.copy(alpha = 0.7f))
                )
                Text(text = " Missed", color = TextTertiary, fontSize = 10.sp)
                Spacer(modifier = Modifier.width(12.dp))
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .border(1.dp, AccentPrimary, CircleShape)
                )
                Text(text = " Today", color = TextTertiary, fontSize = 10.sp)
            }
        }
    }
}

@Composable
private fun CalendarDay(
    dayStatus: DayStatus,
    modifier: Modifier = Modifier
) {
    val backgroundColor = when {
        dayStatus.isFuture -> Color.Transparent
        dayStatus.isToday && dayStatus.hasActivity -> AccentSuccess
        dayStatus.isToday -> Color.Transparent
        dayStatus.hasActivity -> AccentSuccess
        else -> AccentError.copy(alpha = 0.7f) // Missed day
    }

    val textColor = when {
        dayStatus.isFuture -> TextTertiary.copy(alpha = 0.4f)
        dayStatus.isToday -> AccentPrimary
        dayStatus.hasActivity -> BackgroundBase
        else -> TextPrimary // Missed day text
    }

    Box(
        modifier = modifier
            .size(34.dp)
            .clip(CircleShape)
            .background(backgroundColor)
            .then(
                if (dayStatus.isToday) {
                    Modifier.border(2.dp, AccentPrimary, CircleShape)
                } else Modifier
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = dayStatus.dayOfMonth.toString(),
            color = textColor,
            fontSize = 12.sp,
            fontWeight = if (dayStatus.isToday) FontWeight.Bold else FontWeight.Normal
        )
    }
}

@Composable
private fun FilterChip(
    filter: HistoryFilter,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    GlassButtonSmall(
        text = filter.label,
        onClick = onClick,
        primary = isSelected
    )
}

@Composable
private fun TransactionCard(transaction: Transaction) {
    val isEarn = transaction.type == TransactionType.EARN
    val pointsColor = if (isEarn) AccentSuccess else AccentError
    val pointsPrefix = if (isEarn) "+" else "-"

    // Payout rows are stored as "Achievement reward: <id>" (the double-pay guard matches on that
    // text), so the readable name is resolved here at display time.
    val achievementId = achievementIdFromReward(transaction.description)
    val achievement = achievementId?.let { Achievements.getById(it) }

    val emoji = when {
        achievementId != null -> achievement?.emoji ?: "🏆"
        else -> when (transaction.source) {
            TransactionSource.TODOIST -> "✅"
            TransactionSource.MANUAL -> "✏️"
            TransactionSource.MISSION -> "🎯"
            TransactionSource.MOOD -> "😊"
            TransactionSource.REWARD -> "🎁"
        }
    }
    val title = when {
        achievementId != null -> achievement?.name ?: "Achievement unlocked"
        else -> transaction.description ?: transaction.source.name.lowercase()
            .replaceFirstChar { it.uppercase() }
    }

    val statColor = if (achievementId != null) PointsGold else transaction.statType?.let { stat ->
        when (stat) {
            StatType.STR -> StatStrength
            StatType.INT -> StatIntelligence
            StatType.WIS -> StatWisdom
            StatType.DEX -> StatDexterity
            StatType.CHA -> StatCharisma
            StatType.VIT -> StatVitality
        }
    }

    val dateFormat = SimpleDateFormat("MMM d, h:mm a", Locale.getDefault())
    val formattedDate = dateFormat.format(Date(transaction.createdAt))

    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Source emoji
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                (statColor ?: AccentPrimary).copy(alpha = 0.2f),
                                (statColor ?: AccentPrimary).copy(alpha = 0.05f)
                            )
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(text = emoji, fontSize = 22.sp)
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Details
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    color = TextPrimary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    fontFamily = Inter,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = formattedDate,
                        color = TextTertiary,
                        fontSize = 12.sp,
                        fontFamily = Inter
                    )
                    if (achievementId != null) {
                        Text(
                            text = " • Achievement",
                            color = PointsGold,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                    transaction.statType?.let { stat ->
                        Text(
                            text = " • ${stat.name}",
                            color = statColor ?: TextTertiary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            // Points
            Text(
                text = "$pointsPrefix${transaction.points}",
                color = pointsColor,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = Inter
            )
        }
    }
}
