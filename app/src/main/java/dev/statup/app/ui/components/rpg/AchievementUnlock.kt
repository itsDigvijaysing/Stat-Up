package dev.statup.app.ui.components.rpg

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.statup.app.domain.model.Achievement
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import dev.statup.app.ui.components.glass.GlassButton
import dev.statup.app.ui.theme.*

/** The celebration overlay shown when an achievement unlocks. */
@Composable
fun AchievementUnlockedDialog(
    achievement: Achievement,
    onDismiss: () -> Unit,
    /**
     * Space to keep clear at the bottom - the guided-tour coach-mark strip is drawn there after
     * this dialog, so without it the strip would cover the dismiss button. Callers outside the tour pass zero.
     */
    bottomReserved: Dp = 0.dp
) {
    val pulse = rememberInfiniteTransition(label = "unlockPulse")
    val haloScale by pulse.animateFloat(
        initialValue = 0.94f,
        targetValue = 1.10f,
        animationSpec = infiniteRepeatable(tween(1300, easing = LinearEasing), RepeatMode.Reverse),
        label = "halo"
    )
    val haloGlow by pulse.animateFloat(
        initialValue = 0.18f,
        targetValue = 0.42f,
        animationSpec = infiniteRepeatable(tween(1300, easing = LinearEasing), RepeatMode.Reverse),
        label = "glow"
    )

    // Pop-in on first frame so the card arrives rather than just appearing.
    var entered by remember { mutableStateOf(false) }
    val cardScale by animateFloatAsState(
        targetValue = if (entered) 1f else 0.85f,
        animationSpec = tween(320),
        label = "enter"
    )
    LaunchedEffect(achievement.id) { entered = true }

    Box(
        modifier = Modifier
            .fillMaxSize()
            // In-tree overlay, not a Dialog - a Dialog is its own window, so Haze couldn't sample
            // the app behind it. Blurred and lightly tinted so the app reads as out of focus, not blacked out.
            .background(BackgroundBase.copy(alpha = 0.22f))
            // Swallows taps so the screen underneath cannot be operated through the overlay.
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = {}
            )
            .padding(28.dp)
            // Shrinks the centring box rather than the card, so the card stays centred in whatever
            // space the coach-mark leaves instead of being pushed off the top.
            .padding(bottom = bottomReserved),
        contentAlignment = Alignment.Center
    ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .fillMaxWidth()
                    .scale(cardScale)
                    .clip(RoundedCornerShape(24.dp))
                    // Opaque base first, then the gold wash - a gradient starting translucent let the
                    // screen behind show through the top of the card, and a celebration needs to read as solid.
                    .background(BackgroundSurface)
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                PointsGold.copy(alpha = 0.10f),
                                Color.Transparent,
                                Color.Transparent
                            )
                        )
                    )
                    .border(1.5.dp, PointsGold.copy(alpha = 0.45f), RoundedCornerShape(24.dp))
                    .padding(horizontal = 24.dp, vertical = 30.dp)
            ) {
                Text(
                    text = "ACHIEVEMENT UNLOCKED",
                    color = PointsGold,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = Inter,
                    letterSpacing = 2.sp
                )

                Spacer(modifier = Modifier.height(22.dp))

                Box(contentAlignment = Alignment.Center) {
                    Box(
                        modifier = Modifier
                            .size(112.dp)
                            .scale(haloScale)
                            .clip(CircleShape)
                            .background(PointsGold.copy(alpha = haloGlow))
                    )
                    Text(text = achievement.emoji, fontSize = 54.sp)
                }

                Spacer(modifier = Modifier.height(20.dp))

                Text(
                    text = achievement.name,
                    color = TextPrimary,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = Inter,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = achievement.description,
                    color = TextSecondary,
                    fontSize = 14.sp,
                    fontFamily = Inter,
                    textAlign = TextAlign.Center
                )

                val reward = achievement.displayRewardPoints
                if (reward > 0) {
                    Spacer(modifier = Modifier.height(18.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(PointsGold.copy(alpha = 0.16f))
                            .border(1.dp, PointsGold.copy(alpha = 0.4f), RoundedCornerShape(20.dp))
                            .padding(horizontal = 18.dp, vertical = 9.dp)
                    ) {
                        Text(
                            text = "+$reward pts",
                            color = PointsGold,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = Inter
                        )
                    }
                }

                Spacer(modifier = Modifier.height(26.dp))

                GlassButton(
                    text = "Nice!",
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
