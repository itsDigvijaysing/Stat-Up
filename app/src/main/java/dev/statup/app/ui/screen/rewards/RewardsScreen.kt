package dev.statup.app.ui.screen.rewards

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
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
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.navigation.NavController
import dev.statup.app.domain.model.Reward
import dev.statup.app.ui.components.glass.*
import dev.statup.app.ui.components.rememberHapticTick
import dev.statup.app.ui.navigation.Routes
import dev.statup.app.ui.components.HelpDialog
import dev.statup.app.ui.components.HelpIconButton
import dev.statup.app.ui.components.HelpPoint
import dev.statup.app.ui.theme.*
import org.koin.androidx.compose.koinViewModel

@Composable
fun RewardsScreen(
    navController: NavController,
    viewModel: RewardsViewModel = koinViewModel()
) {
    var showHelp by remember { mutableStateOf(false) }
    val uiState by viewModel.uiState.collectAsState()
    // Confirmation gates: redeeming spends points and deleting is permanent - both used to fire
    // on a single tap of buttons that sit close together in a scrolling list.
    var pendingRedeem by remember { mutableStateOf<Reward?>(null) }
    var pendingDelete by remember { mutableStateOf<Reward?>(null) }
    var pendingEdit by remember { mutableStateOf<Reward?>(null) }
    val hapticTick = rememberHapticTick()

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
                    text = "Rewards Shop",
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
                                contentDescription = "Add Reward",
                                tint = AccentPrimary
                            )
                        },
                        onClick = { viewModel.showCreateDialog() }
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Balance Header - tap to view history
            GlassCardWithHighlight(
                modifier = Modifier.fillMaxWidth(),
                elevated = true,
                onClick = { navController.navigate(Routes.HISTORY) }
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Your Points",
                        color = TextSecondary,
                        fontSize = 12.sp,
                        fontFamily = Inter
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "✨ ${uiState.currentBalance}",
                        color = PointsGold,
                        fontSize = 36.sp,
                        fontWeight = FontWeight.Black,
                        fontFamily = Inter
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            if (uiState.isLoading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = AccentPrimary)
                }
            } else if (uiState.rewards.isEmpty()) {
                EmptyRewardsState(onCreateClick = { viewModel.showCreateDialog() })
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(bottom = 100.dp)
                ) {
                    items(uiState.rewards, key = { it.id }) { reward ->
                        RewardCard(
                            reward = reward,
                            canAfford = uiState.currentBalance >= reward.pointsCost,
                            onRedeem = { pendingRedeem = reward },
                            onEdit = { pendingEdit = reward },
                            onDelete = { pendingDelete = reward }
                        )
                    }
                }
            }
        }

        // Create Dialog
        if (showHelp) RewardsHelpDialog(onDismiss = { showHelp = false })

    if (uiState.showCreateDialog) {
            CreateRewardDialog(
                onDismiss = { viewModel.hideCreateDialog() },
                onCreate = { name, desc, cost, emoji, category ->
                    viewModel.createReward(name, desc, cost, emoji, category)
                }
            )
        }

        // Edit Dialog - same form, pre-filled, routes to editReward (preserves id/redemptions).
        pendingEdit?.let { reward ->
            CreateRewardDialog(
                existing = reward,
                onDismiss = { pendingEdit = null },
                onCreate = { name, desc, cost, emoji, category ->
                    viewModel.editReward(reward, name, desc, cost, emoji, category)
                    pendingEdit = null
                }
            )
        }

        // Redeem confirmation - spending points is irreversible.
        pendingRedeem?.let { reward ->
            ConfirmActionDialog(
                title = "Redeem Reward",
                message = "Spend ${reward.pointsCost} points on \"${reward.name}\"?",
                confirmText = "Redeem",
                onConfirm = {
                    viewModel.redeemReward(reward)
                    hapticTick()
                    pendingRedeem = null
                },
                onDismiss = { pendingRedeem = null }
            )
        }

        // Delete confirmation - deletion is permanent.
        pendingDelete?.let { reward ->
            ConfirmActionDialog(
                title = "Delete Reward",
                titleColor = AccentError,
                message = "Delete \"${reward.name}\"? This can't be undone.",
                confirmText = "Delete",
                onConfirm = {
                    viewModel.deleteReward(reward)
                    pendingDelete = null
                },
                onDismiss = { pendingDelete = null }
            )
        }

        // Success snackbar - keyed on the event id (not the reward name) so redeeming the
        // same reward twice in quick succession restarts the auto-dismiss timer.
        uiState.redeemSuccess?.let { success ->
            LaunchedEffect(success.id) {
                kotlinx.coroutines.delay(2000)
                viewModel.clearRedeemSuccess()
            }
            Snackbar(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(16.dp),
                containerColor = AccentSuccess.copy(alpha = 0.9f)
            ) {
                Text("🎉 Redeemed: ${success.rewardName}", color = BackgroundBase)
            }
        }
    }
}

@Composable
private fun EmptyRewardsState(onCreateClick: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        GlassCard(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(text = "🎁", fontSize = 56.sp)
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "No Rewards Yet",
                    color = TextPrimary,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = Inter
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Create rewards to motivate yourself!\nEarn points by completing tasks.",
                    color = TextSecondary,
                    fontSize = 14.sp,
                    fontFamily = Inter,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(24.dp))
                GlassButton(
                    text = "+ Create Reward",
                    onClick = onCreateClick,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Composable
private fun RewardCard(
    reward: Reward,
    canAfford: Boolean,
    onRedeem: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Emoji
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                PointsGold.copy(alpha = 0.2f),
                                PointsGold.copy(alpha = 0.05f)
                            )
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(text = reward.emoji ?: "🎁", fontSize = 28.sp)
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = reward.name,
                    color = TextPrimary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = Inter
                )
                reward.description?.let {
                    Text(
                        text = it,
                        color = TextSecondary,
                        fontSize = 12.sp,
                        fontFamily = Inter,
                        maxLines = 1
                    )
                }
                Text(
                    text = "${reward.pointsCost} pts",
                    color = if (canAfford) PointsGold else AccentError,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = Inter
                )
            }

            Column(horizontalAlignment = Alignment.End) {
                GlassButtonSmall(
                    text = "Redeem",
                    onClick = onRedeem,
                    enabled = canAfford,
                    primary = canAfford
                )
                Spacer(modifier = Modifier.height(4.dp))
                // 48dp touch targets (Core App Quality Touch_Target_Size / Material a11y
                // minimum). The glyphs stay small so the card's visual weight is unchanged -
                // only the tappable area grows. Do NOT shrink these back with Modifier.size().
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = onEdit,
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "Edit",
                            tint = TextTertiary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Delete",
                            tint = TextTertiary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ConfirmActionDialog(
    title: String,
    message: String,
    confirmText: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    titleColor: Color = TextPrimary
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
                        .padding(16.dp)
                ) {
                    Text(
                        text = title,
                        color = titleColor,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = Inter
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = message,
                        color = TextSecondary,
                        fontSize = 14.sp,
                        fontFamily = Inter
                    )
                    Spacer(modifier = Modifier.height(24.dp))
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
                            text = confirmText,
                            onClick = onConfirm,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CreateRewardDialog(
    onDismiss: () -> Unit,
    onCreate: (name: String, desc: String?, cost: Int, emoji: String, category: String?) -> Unit,
    existing: Reward? = null
) {
    var name by remember { mutableStateOf(existing?.name ?: "") }
    var description by remember { mutableStateOf(existing?.description ?: "") }
    var cost by remember { mutableStateOf("10") }
    // Put the existing cost in the custom field (it overrides the presets), so editing shows the
    // current value regardless of whether it was originally a preset.
    var customCost by remember { mutableStateOf(existing?.pointsCost?.toString() ?: "") }
    var selectedEmoji by remember { mutableStateOf(existing?.emoji ?: "🎁") }

    val emojis = listOf("🎁", "🍕", "🎮", "☕", "🎬", "🛍️", "🍦", "📱", "🎧", "✈️", "💆", "🍫")
    val presetCosts = listOf("5", "10", "20", "50", "100", "200", "500")

    // Effective cost: custom field overrides preset when filled with a positive int.
    val effectiveCost = customCost.toIntOrNull()?.takeIf { it > 0 } ?: cost.toIntOrNull() ?: 10

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
                        .padding(8.dp)
                ) {
                    Text(
                        text = if (existing != null) "Edit Reward" else "Create Reward",
                        color = TextPrimary,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = Inter
                    )

                    Spacer(modifier = Modifier.height(20.dp))

                    // Name
                    GlassTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = "Reward Name",
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Description
                    GlassTextField(
                        value = description,
                        onValueChange = { description = it },
                        label = "Description (optional)",
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Emoji selector - 2 rows of 6
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
                                    if (idx < emojis.size) {
                                        val emoji = emojis[idx]
                                        GlassIconButton(
                                            icon = { Text(emoji, fontSize = 20.sp) },
                                            onClick = { selectedEmoji = emoji },
                                            size = 40.dp,
                                            selected = selectedEmoji == emoji
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Cost selector - presets + custom input
                    Text(
                        text = "Cost (points):",
                        color = TextSecondary,
                        fontSize = 14.sp,
                        fontFamily = Inter
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        // Preset chips, 4 per row
                        presetCosts.chunked(4).forEach { row ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                row.forEach { c ->
                                    GlassButtonSmall(
                                        text = c,
                                        onClick = {
                                            cost = c
                                            customCost = "" // clear custom override when picking a preset
                                        },
                                        primary = customCost.isBlank() && cost == c,
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                                // Pad short rows so chips don't stretch
                                repeat(4 - row.size) { Spacer(modifier = Modifier.weight(1f)) }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Custom cost input - overrides presets when filled
                    GlassTextField(
                        value = customCost,
                        onValueChange = { input ->
                            // Only digits, max 6 chars (up to 999,999 pts)
                            customCost = input.filter { it.isDigit() }.take(6)
                        },
                        label = "Or custom amount",
                        modifier = Modifier.fillMaxWidth(),
                        keyboardType = androidx.compose.ui.text.input.KeyboardType.Number
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
                            text = if (existing != null) "Save" else "Create",
                            onClick = {
                                if (name.isNotBlank() && effectiveCost > 0) {
                                    onCreate(
                                        name,
                                        description.takeIf { it.isNotBlank() },
                                        effectiveCost,
                                        selectedEmoji,
                                        null
                                    )
                                }
                            },
                            enabled = name.isNotBlank() && effectiveCost > 0,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
    }
}

private val REWARDS_HELP = listOf(
    HelpPoint("Add a reward", "Tap + and set its price in points.", "\"One episode\" for 20 pts"),
    HelpPoint("Earn points anywhere", "Missions, Todoist, mood and achievements all pay in."),
    HelpPoint("Redeem to spend", "Your balance drops by the price.")
)

@Composable
private fun RewardsHelpDialog(onDismiss: () -> Unit) {
    HelpDialog(
        title = "Rewards Shop",
        intro = "Spend the points you've earned on treats you set for yourself. This is the payoff half of the loop.",
        points = REWARDS_HELP,
        onDismiss = onDismiss
    )
}
