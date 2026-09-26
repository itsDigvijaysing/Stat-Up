package dev.statup.app.ui.components.rpg

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.EventAvailable
import androidx.compose.material.icons.outlined.Insights
import androidx.compose.material.icons.outlined.LocalFireDepartment
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import dev.statup.app.domain.model.PlayerStats
import dev.statup.app.domain.model.Rank
import dev.statup.app.ui.components.glass.GlassButton
import dev.statup.app.ui.components.glass.GlassCard
import dev.statup.app.ui.theme.*

@Composable
fun StatusWindow(
    playerName: String,
    stats: PlayerStats,
    availablePoints: Int = 0,
    hexagonStyle: HexagonStyle = HexagonStyle.SIMPLE,
    modifier: Modifier = Modifier,
    equippedTitle: String? = null,
    onTitleClick: () -> Unit = {},
    onHistoryClick: () -> Unit = {}
) {
    val shape = RoundedCornerShape(24.dp)
    var showStatBars by remember { mutableStateOf(false) }
    var showRankInfo by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        GlassHighlight,
                        GlassFill,
                        GlassFill
                    )
                )
            )
            .border(GlassTokens.BorderWidth, GlassBorder, shape)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Header
        StatusHeader()

        Spacer(modifier = Modifier.height(20.dp))

        // Rank Badge - clickable for rank info
        Box(
            modifier = Modifier.clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { showRankInfo = true }
        ) {
            RankBadge(rank = stats.rank, showTitle = false)
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Player Name
        Text(
            text = playerName,
            color = TextPrimary,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = Inter
        )

        // Equipped achievement title - tap to change. Shows a subtle hint when none is
        // equipped so the feature is discoverable without cluttering the sheet.
        Text(
            text = equippedTitle?.let { "« $it »" } ?: "+ set title",
            color = if (equippedTitle != null) PointsGold else TextTertiary,
            fontSize = if (equippedTitle != null) 13.sp else 11.sp,
            fontWeight = if (equippedTitle != null) FontWeight.SemiBold else FontWeight.Normal,
            fontFamily = Inter,
            modifier = Modifier
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onTitleClick
                )
                .padding(top = 2.dp)
        )

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = "✨ $availablePoints pts",
            color = PointsGold,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            fontFamily = Inter
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Hexagon Chart - clickable to toggle stat bars
        Box(
            modifier = Modifier.clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { showStatBars = !showStatBars }
        ) {
            HexagonRadarChart(
                stats = stats,
                size = 200.dp,
                showLabels = true,
                style = hexagonStyle
            )
        }

        // Collapsible Stat Bars
        AnimatedVisibility(
            visible = showStatBars,
            enter = expandVertically(),
            exit = shrinkVertically()
        ) {
            Column {
                Spacer(modifier = Modifier.height(16.dp))
                StatBarsColumn(stats = stats, showFullNames = true)
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Both rank requirements plus the streak, in one row rather than a separate box
        // below - three comparable values at a glance instead of repeating them.
        StatusFooter(
            stats = stats,
            onHistoryClick = onHistoryClick
        )
    }

    // Rank Info Dialog
    if (showRankInfo) {
        RankInfoDialog(
            stats = stats,
            onDismiss = { showRankInfo = false }
        )
    }
}

@Composable
private fun StatusHeader() {
    val headerShape = RoundedCornerShape(12.dp)

    Box(
        modifier = Modifier
            .clip(headerShape)
            .background(AccentPrimary.copy(alpha = 0.15f))
            .border(1.dp, AccentPrimary.copy(alpha = 0.3f), headerShape)
            .padding(horizontal = 24.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "S T A T U S",
            color = AccentPrimary,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = Inter,
            letterSpacing = 4.sp
        )
    }
}

@Composable
private fun StatusFooter(
    stats: PlayerStats,
    onHistoryClick: () -> Unit = {}
) {
    val next = stats.rank.nextRank()

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.Top
    ) {
        // Avg stat and Work Days are the two promotion gates; shown as `have / need` and
        // colored so "what's blocking me" reads without a second box.
        FooterItem(
            icon = Icons.Outlined.Insights,
            label = "Avg Stat",
            modifier = Modifier.weight(1f),
            value = next?.let { "${stats.averageStat().toInt()} / ${it.statsRequired}" }
                ?: "${stats.averageStat().toInt()}",
            met = next == null || stats.averageStat() >= next.statsRequired
        )
        FooterItem(
            icon = Icons.Outlined.LocalFireDepartment,
            label = "Streak",
            modifier = Modifier.weight(1f),
            value = "${stats.streak} days"
        )
        FooterItem(
            icon = Icons.Outlined.EventAvailable,
            label = "Work Days",
            modifier = Modifier.weight(1f),
            value = next?.let { "${stats.workDays} / ${it.daysRequired}" } ?: "${stats.workDays}",
            met = next == null || stats.workDays >= next.daysRequired,
            onClick = onHistoryClick
        )
    }
}

@Composable
private fun FooterItem(
    icon: ImageVector,
    label: String,
    modifier: Modifier = Modifier,
    value: String,
    /** null = not a rank gate (no target to meet), so it stays neutral. */
    met: Boolean? = null,
    onClick: (() -> Unit)? = null
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier.then(
            if (onClick != null) {
            Modifier.clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
        } else Modifier
        )
    ) {
        val tint = when (met) {
            true -> AccentSuccess
            false -> AccentWarning
            null -> AccentPrimary
        }
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(22.dp)
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = label,
            color = TextTertiary,
            fontSize = 10.sp,
            fontFamily = Inter,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = value,
            color = when (met) {
                true -> AccentSuccess
                false -> AccentWarning
                null -> TextPrimary
            },
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = Inter,
            // A long value ("1234 / 240") must shrink rather than wrap or shove its
            // neighbours: the three columns are fixed thirds.
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Visible,
            textAlign = TextAlign.Center,
            style = LocalTextStyle.current.copy(
                lineHeight = 14.sp,
                platformStyle = null
            ),
            modifier = Modifier.fillMaxWidth()
        )
    }
}

/**
 * Shows both promotion requirements and marks which is blocking - the old five-star display
 * off a single counter couldn't express "days are there but stats aren't".
 */
@Composable
fun RankProgressBlock(
    stats: PlayerStats,
    modifier: Modifier = Modifier
) {
    val next = stats.rank.nextRank()
    val shape = RoundedCornerShape(12.dp)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(AccentPrimary.copy(alpha = 0.07f))
            .padding(horizontal = 14.dp, vertical = 12.dp)
    ) {
        if (next == null) {
            Text(
                text = "Rank ${stats.rank.name} - top of the ladder",
                color = PointsGold,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = Inter
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "${stats.workDays} work days banked",
                color = TextSecondary,
                fontSize = 12.sp,
                fontFamily = Inter
            )
            return@Column
        }

        Text(
            text = "Rank ${stats.rank.name} → ${next.name}",
            color = TextPrimary,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = Inter
        )
        Spacer(modifier = Modifier.height(8.dp))

        RequirementRow(
            label = "Work days",
            have = stats.workDays,
            need = next.daysRequired
        )
        Spacer(modifier = Modifier.height(4.dp))
        RequirementRow(
            label = "Avg stat",
            have = stats.averageStat().toInt(),
            need = next.statsRequired
        )
    }
}

@Composable
private fun RequirementRow(label: String, have: Int, need: Int) {
    val met = have >= need
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            color = TextSecondary,
            fontSize = 12.sp,
            fontFamily = Inter,
            modifier = Modifier.width(78.dp)
        )
        Text(
            text = "$have / $need",
            color = if (met) AccentSuccess else TextPrimary,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            fontFamily = Inter,
            modifier = Modifier.width(64.dp)
        )
        Text(
            text = if (met) "✓" else "${need - have} more",
            color = if (met) AccentSuccess else AccentWarning,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            fontFamily = Inter
        )
    }
}

@Composable
private fun RankInfoDialog(
    stats: PlayerStats,
    onDismiss: () -> Unit
) {
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
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp)
                ) {
                    Text(
                        text = "Rank System",
                        color = TextPrimary,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = Inter
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = "Every day you earn any points is a work day. Miss a day and you " +
                            "lose one work day plus one point from your highest stat.",
                        color = TextSecondary,
                        fontSize = 13.sp,
                        fontFamily = Inter,
                        lineHeight = 18.sp
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "Work days never reset when you rank up - they keep adding up, so " +
                            "the longer you go the safer your rank gets.",
                        color = PointsGold,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        fontFamily = Inter,
                        lineHeight = 18.sp
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Column headers for the requirements table.
                    Row(modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp)) {
                        Spacer(modifier = Modifier.width(20.dp))
                        Text(
                            text = "Rank",
                            color = TextTertiary,
                            fontSize = 10.sp,
                            fontFamily = Inter,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = "Days",
                            color = TextTertiary,
                            fontSize = 10.sp,
                            fontFamily = Inter,
                            textAlign = TextAlign.End,
                            modifier = Modifier.width(44.dp)
                        )
                        Text(
                            text = "Avg stat",
                            color = TextTertiary,
                            fontSize = 10.sp,
                            fontFamily = Inter,
                            textAlign = TextAlign.End,
                            modifier = Modifier.width(56.dp)
                        )
                    }

                    Rank.entries.reversed().forEach { rank ->
                        val isCurrent = rank == stats.rank
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = if (isCurrent) "▸" else " ",
                                color = rank.color,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.width(20.dp)
                            )
                            Text(
                                text = "${rank.name}  ${rank.title}",
                                color = if (isCurrent) rank.color else TextSecondary,
                                fontSize = 13.sp,
                                fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                                fontFamily = Inter,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                text = if (rank == Rank.E) "-" else "${rank.daysRequired}",
                                color = if (isCurrent) TextPrimary else TextTertiary,
                                fontSize = 13.sp,
                                fontFamily = Inter,
                                textAlign = TextAlign.End,
                                modifier = Modifier.width(44.dp)
                            )
                            Text(
                                text = if (rank == Rank.E) "-" else "${rank.statsRequired}",
                                color = if (isCurrent) TextPrimary else TextTertiary,
                                fontSize = 13.sp,
                                fontFamily = Inter,
                                textAlign = TextAlign.End,
                                modifier = Modifier.width(56.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    RankProgressBlock(stats = stats)

                    Spacer(modifier = Modifier.height(20.dp))

                    GlassButton(
                        text = "Got it",
                        onClick = onDismiss,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}
