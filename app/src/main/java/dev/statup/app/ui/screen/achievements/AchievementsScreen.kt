package dev.statup.app.ui.screen.achievements

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.navigation.NavController
import dev.statup.app.domain.model.Achievement
import androidx.compose.ui.text.input.KeyboardType
import dev.statup.app.ui.components.glass.*
import dev.statup.app.ui.theme.*
import org.koin.androidx.compose.koinViewModel

@Composable
fun AchievementsScreen(
    navController: NavController,
    viewModel: AchievementsViewModel = koinViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Header with progress + add button
        AchievementsHeader(
            unlockedCount = uiState.unlockedCount,
            totalCount = uiState.totalCount,
            completionPercent = uiState.completionPercent,
            onAddClick = { viewModel.showCreateDialog() }
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Achievements list (no category filter)
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.weight(1f)
        ) {
            // A just-earned achievement jumps to the top while its glow lasts, so it isn't
            // buried below a screenful of locked rows.
            val now = System.currentTimeMillis()
            val (fresh, settled) = uiState.achievements.partition { a ->
                a.isUnlocked && a.unlockedAt?.let { now - it < FRESH_UNLOCK_WINDOW_MS } == true
            }
            val (completed, inProgress) = settled.partition { it.isUnlocked }

            items(fresh, key = { it.id }) { achievement ->
                AchievementCard(
                    achievement = achievement,
                    onDelete = { viewModel.deleteAchievement(achievement) },
                    onComplete = { viewModel.completeAchievement(achievement) }
                )
            }

            items(inProgress, key = { it.id }) { achievement ->
                AchievementCard(
                    achievement = achievement,
                    onDelete = { viewModel.deleteAchievement(achievement) },
                    onComplete = { viewModel.completeAchievement(achievement) }
                )
            }

            if (completed.isNotEmpty()) {
                item(key = "completed_header") {
                    SectionHeader(text = "Completed (${completed.size})")
                }
                items(completed, key = { it.id }) { achievement ->
                    AchievementCard(
                        achievement = achievement,
                        onDelete = { viewModel.deleteAchievement(achievement) },
                        onComplete = { viewModel.completeAchievement(achievement) }
                    )
                }
            }

            item {
                Spacer(modifier = Modifier.height(80.dp))
            }
        }
    }

    // Create dialog
    if (uiState.showCreateDialog) {
        CreateAchievementDialog(
            onDismiss = { viewModel.hideCreateDialog() },
            onCreate = { name, desc, emoji, reward ->
                viewModel.createAchievement(name, desc, emoji, reward)
            }
        )
    }
}

/** Ceiling for a user-set achievement reward - these complete on a tap, so keep it a goal. */
private const val MAX_CUSTOM_REWARD = 500

@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text,
        color = TextSecondary,
        fontSize = 13.sp,
        fontWeight = FontWeight.SemiBold,
        fontFamily = Inter,
        modifier = Modifier.padding(top = 8.dp, bottom = 2.dp)
    )
}

@Composable
private fun AchievementsHeader(
    unlockedCount: Int,
    totalCount: Int,
    completionPercent: Float,
    onAddClick: () -> Unit
) {
    val animatedProgress by animateFloatAsState(
        targetValue = completionPercent,
        animationSpec = tween(500),
        label = "progress"
    )

    GlassCard(
        modifier = Modifier.fillMaxWidth(),
        elevated = true
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Achievements",
                    color = TextPrimary,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = Inter
                )
                GlassIconButton(
                    icon = {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Add Achievement",
                            tint = AccentPrimary
                        )
                    },
                    onClick = onAddClick
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Progress bar
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color.White.copy(alpha = 0.1f))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(animatedProgress)
                        .clip(RoundedCornerShape(4.dp))
                        .background(
                            brush = Brush.horizontalGradient(
                                colors = listOf(AccentPrimary, AccentSecondary)
                            )
                        )
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "$unlockedCount / $totalCount Unlocked (${(completionPercent * 100).toInt()}%)",
                color = TextSecondary,
                fontSize = 14.sp,
                fontFamily = Inter
            )
        }
    }
}

@Composable
private fun AchievementCard(
    achievement: Achievement,
    onDelete: () -> Unit = {},
    onComplete: () -> Unit = {}
) {
    val alpha = if (achievement.isUnlocked) 1f else 0.6f
    val animatedProgress by animateFloatAsState(
        targetValue = achievement.progressPercent,
        animationSpec = tween(500),
        label = "achievementProgress"
    )

    val rewardPoints = achievement.displayRewardPoints

    // A just-earned achievement glows for a minute so the unlock is actually noticed -
    // the list is long and an unlock was otherwise indistinguishable from an old one.
    val isFresh = achievement.isUnlocked && achievement.unlockedAt?.let {
        System.currentTimeMillis() - it < FRESH_UNLOCK_WINDOW_MS
    } == true
    val freshPulse = rememberInfiniteTransition(label = "freshUnlock")
    val glow by freshPulse.animateFloat(
        initialValue = 0.20f,
        targetValue = 0.75f,
        animationSpec = infiniteRepeatable(tween(900, easing = LinearEasing), RepeatMode.Reverse),
        label = "freshGlow"
    )

    GlassCard(
        modifier = Modifier
            .fillMaxWidth()
            .alpha(alpha)
            .then(
                if (isFresh) {
                    Modifier.border(
                        width = 2.dp,
                        color = PointsGold.copy(alpha = glow),
                        shape = RoundedCornerShape(GlassTokens.CardRadius)
                    )
                } else Modifier
            )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Emoji badge
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(
                        if (achievement.isUnlocked) {
                            achievement.category.color.copy(alpha = 0.2f)
                        } else {
                            Color.White.copy(alpha = 0.05f)
                        }
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (achievement.isUnlocked) achievement.emoji else "🔒",
                    fontSize = 24.sp
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Content
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = achievement.name,
                        color = if (achievement.isUnlocked) TextPrimary else TextSecondary,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        fontFamily = Inter,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (achievement.isUnlocked) {
                        Text(
                            text = "✓",
                            color = AccentSuccess,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                if (achievement.description.isNotBlank()) {
                    Text(
                        text = achievement.description,
                        color = TextTertiary,
                        fontSize = 12.sp,
                        fontFamily = Inter,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                // Reward points badge
                Text(
                    text = "🏆 +$rewardPoints pts",
                    color = PointsGold,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    fontFamily = Inter
                )

                if (!achievement.isUnlocked) {
                    Spacer(modifier = Modifier.height(6.dp))

                    if (achievement.isNoGoal) {
                        // Manual completion button for no-goal achievements
                        GlassButtonSmall(
                            text = "Mark Complete",
                            onClick = onComplete,
                            primary = true
                        )
                    } else {
                        // Progress bar
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(4.dp)
                                    .clip(RoundedCornerShape(2.dp))
                                    .background(Color.White.copy(alpha = 0.1f))
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxHeight()
                                        .fillMaxWidth(animatedProgress)
                                        .clip(RoundedCornerShape(2.dp))
                                        .background(achievement.category.color)
                                )
                            }

                            Text(
                                text = "${achievement.progress}/${achievement.target}",
                                color = TextTertiary,
                                fontSize = 10.sp,
                                fontFamily = Inter
                            )
                        }
                    }
                }
            }

            // Delete button for all achievements. 48dp is the accessibility minimum touch
            // target (Core App Quality Touch_Target_Size); the 18dp glyph keeps it visually light.
            IconButton(
                onClick = onDelete,
                modifier = Modifier.size(48.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Delete",
                    tint = TextTertiary,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
private fun CreateAchievementDialog(
    onDismiss: () -> Unit,
    onCreate: (name: String, description: String, emoji: String, rewardPoints: Int) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var emoji by remember { mutableStateOf("🏆") }
    var rewardPoints by remember { mutableStateOf("25") }
    var customPoints by remember { mutableStateOf("") }

    val emojiOptions = listOf("🏆", "⭐", "🎯", "💪", "🧠", "🔥", "💎", "👑", "🚀", "🎖️", "⚡", "🌟")

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
                        text = "Create Achievement",
                        color = TextPrimary,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = Inter
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Emoji picker - 2 rows of 6
                    Text(
                        text = "Icon:",
                        color = TextSecondary,
                        fontSize = 14.sp,
                        fontFamily = Inter
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        for (row in 0 until 2) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                for (col in 0 until 6) {
                                    val idx = row * 6 + col
                                    if (idx < emojiOptions.size) {
                                        val e = emojiOptions[idx]
                                        GlassIconButton(
                                            icon = { Text(text = e, fontSize = 18.sp) },
                                            onClick = { emoji = e },
                                            size = 38.dp,
                                            selected = emoji == e
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Name
                    GlassTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = "Achievement Name",
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Description (optional)
                    GlassTextField(
                        value = description,
                        onValueChange = { description = it },
                        label = "Description (optional)",
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Manual-completion only: AchievementTracker can't auto-advance a custom id,
                    // so a category/target would render a progress bar that never moves.
                    Text(
                        text = "Tick it off yourself whenever you've earned it.",
                        color = TextTertiary,
                        fontSize = 12.sp,
                        fontFamily = Inter
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Reward points selector
                    Text(
                        text = "Points:",
                        color = TextSecondary,
                        fontSize = 14.sp,
                        fontFamily = Inter
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    val pointsOptions = listOf("10", "25", "50", "100", "250")
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        pointsOptions.forEach { p ->
                            GlassButtonSmall(
                                text = p,
                                onClick = { rewardPoints = p; customPoints = "" },
                                primary = customPoints.isBlank() && rewardPoints == p,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Overrides the preset when non-empty. Capped at MAX_CUSTOM_REWARD so a
                    // manual-completion achievement stays a goal rather than a points button.
                    GlassTextField(
                        value = customPoints,
                        onValueChange = { v -> customPoints = v.filter { it.isDigit() }.take(3) },
                        label = "Custom points (max $MAX_CUSTOM_REWARD)",
                        keyboardType = KeyboardType.Number,
                        modifier = Modifier.fillMaxWidth()
                    )

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
                                    val points = (customPoints.toIntOrNull()?.takeIf { it > 0 }
                                        ?: rewardPoints.toIntOrNull() ?: 25)
                                        .coerceIn(1, MAX_CUSTOM_REWARD)
                                    onCreate(name, description, emoji, points)
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

/** How long a newly unlocked achievement keeps its glow. */
private const val FRESH_UNLOCK_WINDOW_MS = 60_000L
