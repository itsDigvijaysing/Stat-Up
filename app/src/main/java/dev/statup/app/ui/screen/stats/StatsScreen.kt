package dev.statup.app.ui.screen.stats

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import dev.statup.app.domain.model.PlayerStats
import dev.statup.app.domain.model.StatType
import dev.statup.app.domain.model.TransactionSource
import dev.statup.app.ui.components.glass.GlassCard
import dev.statup.app.ui.components.HelpDialog
import dev.statup.app.ui.components.HelpIconButton
import dev.statup.app.ui.components.HelpPoint
import dev.statup.app.ui.theme.*
import org.koin.androidx.compose.koinViewModel
import java.time.format.TextStyle
import java.util.Locale

@Composable
fun StatsScreen(
    navController: NavController,
    viewModel: StatsViewModel = koinViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var showHelp by remember { mutableStateOf(false) }

    if (showHelp) {
        HelpDialog(
            title = "Your Stats",
            intro = "The detail view: where your points went and how close each stat is to its next point.",
            points = STATS_HELP,
            onDismiss = { showHelp = false }
        )
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            StatsHeader(onHelp = { showHelp = true })
        }

        // Overall summary
        item {
            OverallSummary(stats = uiState.stats, totalTransactions = uiState.totalTransactions)
        }

        // Stat selector
        item {
            StatSelector(
                stats = uiState.stats,
                selectedStat = uiState.selectedStat,
                onStatSelected = { viewModel.selectStat(it) }
            )
        }

        // Weekly chart
        item {
            WeeklyChart(weeklyData = uiState.weeklyData)
        }

        // Stat breakdown
        item {
            StatBreakdownSection(
                statBreakdown = uiState.statBreakdown,
                selectedStat = uiState.selectedStat
            )
        }

        // Stat accumulators (progress toward next stat point)
        item {
            StatAccumulatorSection(stats = uiState.stats, selectedStat = uiState.selectedStat)
        }

        // Top sources
        item {
            TopSourcesSection(topSources = uiState.topSources)
        }

        item {
            Spacer(modifier = Modifier.height(80.dp))
        }
    }
}

@Composable
private fun StatsHeader(onHelp: () -> Unit) {
    GlassCard(
        modifier = Modifier.fillMaxWidth(),
        elevated = true
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "📊",
                fontSize = 32.sp
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Detailed Stats",
                    color = TextPrimary,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = Inter
                )
                Text(
                    text = "Track your progress over time",
                    color = TextSecondary,
                    fontSize = 12.sp,
                    fontFamily = Inter
                )
            }
            HelpIconButton(onClick = onHelp)
        }
    }
}

@Composable
private fun StatSelector(
    stats: PlayerStats,
    selectedStat: StatType?,
    onStatSelected: (StatType?) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        // "All" button full width
        StatChip(
            label = "All Stats",
            value = null,
            color = AccentPrimary,
            isSelected = selectedStat == null,
            onClick = { onStatSelected(null) },
            modifier = Modifier.fillMaxWidth()
        )
        
        // 3x2 grid for stat types
        val statTypes = StatType.entries.toList()
        for (row in 0 until 2) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                for (col in 0 until 3) {
                    val index = row * 3 + col
                    if (index < statTypes.size) {
                        val statType = statTypes[index]
                        val statValue = stats.getStat(statType)
                        StatChip(
                            label = statType.name,
                            value = statValue,
                            color = statType.color,
                            isSelected = selectedStat == statType,
                            onClick = { onStatSelected(statType) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StatChip(
    label: String,
    value: Int?,
    color: Color,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val backgroundColor = if (isSelected) color.copy(alpha = 0.2f) else GlassFill

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(backgroundColor)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = 12.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Text(
                text = label,
                color = if (isSelected) color else TextSecondary,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                fontFamily = Inter
            )
            if (value != null) {
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "$value",
                    color = if (isSelected) color else TextTertiary,
                    fontSize = 12.sp,
                    fontFamily = Inter
                )
            }
        }
    }
}

@Composable
private fun WeeklyChart(weeklyData: List<DayData>) {
    val maxPoints = weeklyData.maxOfOrNull { it.points } ?: 1

    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Last 7 Days",
                color = TextPrimary,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                fontFamily = Inter
            )

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(140.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.Bottom
            ) {
                weeklyData.forEach { dayData ->
                    val heightFraction = if (maxPoints > 0) {
                        dayData.points.toFloat() / maxPoints
                    } else 0f

                    val animatedHeight by animateFloatAsState(
                        targetValue = heightFraction,
                        animationSpec = tween(500),
                        label = "barHeight"
                    )

                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.weight(1f)
                    ) {
                        // Points label
                        if (dayData.points > 0) {
                            Text(
                                text = "${dayData.points}",
                                color = AccentPrimary,
                                fontSize = 9.sp,
                                fontFamily = Inter
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                        }

                        // Bar
                        Box(
                            modifier = Modifier
                                .width(20.dp)
                                .height((90.dp * animatedHeight).coerceAtLeast(4.dp))
                                .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                                .background(
                                    brush = Brush.verticalGradient(
                                        colors = listOf(AccentPrimary, AccentSecondary)
                                    )
                                )
                        )

                        Spacer(modifier = Modifier.height(6.dp))

                        // Day label — single letter to avoid overlap
                        Text(
                            text = dayData.date.dayOfWeek.getDisplayName(TextStyle.NARROW, Locale.getDefault()),
                            color = TextTertiary,
                            fontSize = 11.sp,
                            fontFamily = Inter,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StatBreakdownSection(
    statBreakdown: Map<StatType, StatBreakdown>,
    selectedStat: StatType?
) {
    val filteredBreakdown = if (selectedStat != null) {
        statBreakdown.filterKeys { it == selectedStat }
    } else {
        statBreakdown
    }

    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Points by Stat",
                color = TextPrimary,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                fontFamily = Inter
            )

            Spacer(modifier = Modifier.height(12.dp))

            filteredBreakdown.forEach { (statType, breakdown) ->
                StatBreakdownRow(statType = statType, breakdown = breakdown)
                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }
}

@Composable
private fun StatBreakdownRow(
    statType: StatType,
    breakdown: StatBreakdown
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Stat indicator
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(statType.color)
        )

        Spacer(modifier = Modifier.width(8.dp))

        Text(
            text = statType.displayName,
            color = TextPrimary,
            fontSize = 14.sp,
            fontFamily = Inter,
            modifier = Modifier.weight(1f)
        )

        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = "${breakdown.totalPoints} pts",
                color = statType.color,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                fontFamily = Inter
            )
            Text(
                text = "${breakdown.transactionCount} transactions",
                color = TextTertiary,
                fontSize = 10.sp,
                fontFamily = Inter
            )
        }
    }
}

@Composable
private fun TopSourcesSection(topSources: List<SourceData>) {
    if (topSources.isEmpty()) return

    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Top Point Sources",
                color = TextPrimary,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                fontFamily = Inter
            )

            Spacer(modifier = Modifier.height(12.dp))

            topSources.forEachIndexed { index, sourceData ->
                SourceRow(rank = index + 1, sourceData = sourceData)
                if (index < topSources.lastIndex) {
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
        }
    }
}

@Composable
private fun OverallSummary(stats: PlayerStats, totalTransactions: Int) {
    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            SummaryItem(
                label = "Total Stats",
                value = "${stats.totalStats()}",
                color = AccentPrimary
            )
            SummaryItem(
                label = "Avg Stat",
                value = "${stats.averageStat().toInt()}",
                color = AccentSecondary
            )
            SummaryItem(
                label = "Total Earned",
                value = "${stats.totalPointsEarned}",
                color = PointsGold
            )
            SummaryItem(
                label = "Activities",
                value = "$totalTransactions",
                color = AccentSuccess
            )
        }
    }
}

@Composable
private fun SummaryItem(label: String, value: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            color = color,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = Inter
        )
        Text(
            text = label,
            color = TextTertiary,
            fontSize = 10.sp,
            fontFamily = Inter
        )
    }
}

@Composable
private fun StatAccumulatorSection(stats: PlayerStats, selectedStat: StatType?) {
    val statsToShow = if (selectedStat != null) listOf(selectedStat) else StatType.entries.toList()

    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Progress to Next Stat Point",
                color = TextPrimary,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                fontFamily = Inter
            )
            Text(
                text = "Every ${PlayerStats.POINTS_PER_STAT} points earned = +1 stat point",
                color = TextTertiary,
                fontSize = 11.sp,
                fontFamily = Inter
            )

            Spacer(modifier = Modifier.height(12.dp))

            statsToShow.forEach { statType ->
                val accumulator = stats.getStatAccumulator(statType)
                val currentStat = stats.getStat(statType)
                // Coerced: a maxed stat freezes its accumulator, which can sit at or above
                // POINTS_PER_STAT and would otherwise drive the bar past full width.
                val progress = (accumulator.toFloat() / PlayerStats.POINTS_PER_STAT).coerceIn(0f, 1f)

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = statType.name,
                        color = statType.color,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        fontFamily = Inter,
                        modifier = Modifier.width(36.dp)
                    )

                    Text(
                        text = "$currentStat",
                        color = TextSecondary,
                        fontSize = 12.sp,
                        fontFamily = Inter,
                        modifier = Modifier.width(28.dp)
                    )

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(Color.White.copy(alpha = 0.08f))
                    ) {
                        val animatedWidth by animateFloatAsState(
                            targetValue = progress,
                            animationSpec = tween(400),
                            label = "acc_${statType.name}"
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .fillMaxWidth(animatedWidth)
                                .clip(RoundedCornerShape(3.dp))
                                .background(statType.color)
                        )
                    }

                    Text(
                        text = "$accumulator/${PlayerStats.POINTS_PER_STAT}",
                        color = TextTertiary,
                        fontSize = 11.sp,
                        fontFamily = Inter,
                        modifier = Modifier.width(36.dp),
                        textAlign = TextAlign.End
                    )
                }
            }
        }
    }
}

@Composable
private fun SourceRow(
    rank: Int,
    sourceData: SourceData
) {
    val emoji = when (sourceData.source) {
        TransactionSource.MANUAL -> "✏️"
        TransactionSource.MISSION -> "🎯"
        TransactionSource.MOOD -> "😊"
        TransactionSource.TODOIST -> "📋"
        TransactionSource.REWARD -> "🎁"
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "#$rank",
            color = when (rank) {
                1 -> PointsGold
                2 -> TextSecondary
                3 -> AccentWarning
                else -> TextTertiary
            },
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = Inter,
            modifier = Modifier.width(28.dp)
        )

        Text(text = emoji, fontSize = 16.sp)

        Spacer(modifier = Modifier.width(8.dp))

        Text(
            text = sourceData.source.name.lowercase().replaceFirstChar { it.uppercase() },
            color = TextPrimary,
            fontSize = 14.sp,
            fontFamily = Inter,
            modifier = Modifier.weight(1f)
        )

        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = "${sourceData.totalPoints} pts",
                color = AccentPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                fontFamily = Inter
            )
            Text(
                text = "${sourceData.count}x",
                color = TextTertiary,
                fontSize = 10.sp,
                fontFamily = Inter
            )
        }
    }
}

private val STATS_HELP = listOf(
    HelpPoint(
        "Progress bars",
        "Each bar shows how many points you've banked toward that stat's next point.",
        "${dev.statup.app.domain.model.PlayerStats.POINTS_PER_STAT} points fills one bar"
    ),
    HelpPoint(
        "Average stat gates your rank",
        "Ranking up needs a minimum average across all six, so a single maxed stat won't carry you."
    ),
    HelpPoint(
        "Top sources",
        "Where your points actually come from — useful for spotting a stat you never train."
    )
)
