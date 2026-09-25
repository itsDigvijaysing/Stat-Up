package dev.statup.app.ui.screen.tutorial

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import dev.statup.app.domain.model.PlayerStats
import dev.statup.app.ui.components.glass.GlassButton
import dev.statup.app.ui.navigation.Routes
import dev.statup.app.ui.theme.*

/**
 * Announces the tour before it starts, so the user knows what is happening rather than
 * wondering why the app is talking to them. Pulsing mark plus an explicit opt-in.
 */
@Composable
fun TutorialIntroDialog(onStart: () -> Unit, onSkip: () -> Unit) {
    val pulse = rememberInfiniteTransition(label = "introPulse")
    val scale by pulse.animateFloat(
        initialValue = 0.92f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(tween(1100, easing = LinearEasing), RepeatMode.Reverse),
        label = "introScale"
    )
    val glow by pulse.animateFloat(
        initialValue = 0.15f,
        targetValue = 0.45f,
        animationSpec = infiniteRepeatable(tween(1100, easing = LinearEasing), RepeatMode.Reverse),
        label = "introGlow"
    )

    Dialog(
        onDismissRequest = { },
        properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnClickOutside = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(BackgroundBase.copy(alpha = 0.93f))
                .padding(28.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(contentAlignment = Alignment.Center) {
                    Box(
                        modifier = Modifier
                            .size(120.dp)
                            .scale(scale)
                            .clip(CircleShape)
                            .background(AccentPrimary.copy(alpha = glow))
                    )
                    Text(text = "👋", fontSize = 52.sp)
                }

                Spacer(modifier = Modifier.height(28.dp))
                Text(
                    text = "Quick tour",
                    color = TextPrimary,
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = Inter
                )
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "Three steps, about thirty seconds. You'll finish a real task, " +
                        "unlock a real achievement and spend what you earn - none of it is " +
                        "pretend, it all stays on your account.",
                    color = TextSecondary,
                    fontSize = 14.sp,
                    fontFamily = Inter,
                    textAlign = TextAlign.Center,
                    lineHeight = 20.sp
                )
                Spacer(modifier = Modifier.height(28.dp))
                GlassButton(
                    text = "Start the tour",
                    onClick = onStart,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(14.dp))
                Text(
                    text = "Skip - I'll explore on my own",
                    color = TextTertiary,
                    fontSize = 13.sp,
                    fontFamily = Inter,
                    modifier = Modifier
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = onSkip
                        )
                        .padding(8.dp)
                )
            }
        }
    }
}

/**
 * The coach-mark strip for the guided first run. Kept slim and pinned above the bottom bar,
 * and it fades out on a cycle so it never permanently covers the thing it is pointing at.
 *
 * It does not navigate. When the step lives on another tab it names that tab and the bottom
 * bar pulses it, so the user learns the layout by moving through it themselves.
 *
 * The achievement step has no target tab at all - the unlock popup comes to the user - so this
 * strip only narrates it. **The strip is display-only: it has no button and no tap handler**, so it
 * cannot complete a step. If the popup never fires (the achievement was already unlocked on an
 * earlier run) the step is advanced by `AppNavigation`, which waits for the payout transaction to
 * land. Do not delete that wait on the assumption this strip is a fallback - it is not.
 */
@Composable
fun TutorialOverlay(
    step: TutorialStep,
    isOnTargetTab: Boolean,
    /** Where the user actually is, so the hint can drop a hop they have already made. */
    currentRoute: String,
    /** False on hidden detail screens, where there is no tab to point at. */
    hasBottomBar: Boolean,
    /**
     * Abandons the tour. Present on every step, not just the intro: without an exit here, a step the
     * user cannot complete has no way out, which is exactly how the earlier dead ends were reachable.
     */
    onSkip: () -> Unit,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(16.dp)
    val pulse = rememberInfiniteTransition(label = "markPulse")
    val borderAlpha by pulse.animateFloat(
        initialValue = 0.25f,
        targetValue = 0.85f,
        animationSpec = infiniteRepeatable(tween(950, easing = LinearEasing), RepeatMode.Reverse),
        label = "markBorder"
    )


    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clip(shape)
            .background(BackgroundSurface.copy(alpha = 0.97f))
            .border(1.5.dp, AccentPrimary.copy(alpha = borderAlpha), shape)
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        // INTRO is ordinal 0 and is never rendered here (AppNavigation filters it out), so the
        // three real steps read 1..3. That only holds while INTRO stays first in the enum -
        // inserting a step before it silently renumbers every label.
        Text(
            text = "STEP ${step.ordinal} OF 3",
            color = AccentPrimary,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = Inter,
            letterSpacing = 1.5.sp
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = if (isOnTargetTab || step.targetRoute == null) step.title else "Open ${step.tabName}",
            color = TextPrimary,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = Inter
        )
        Spacer(modifier = Modifier.height(3.dp))
        Text(
            text = when {
                isOnTargetTab || step.targetRoute == null -> step.body
                !hasBottomBar -> "Go back first, then ${step.navHintFrom(currentRoute).replaceFirstChar { it.lowercase() }}"
                else -> step.navHintFrom(currentRoute)
            },
            color = TextSecondary,
            fontSize = 13.sp,
            fontFamily = Inter,
            lineHeight = 18.sp
        )

        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Skip tour",
            color = TextTertiary,
            fontSize = 12.sp,
            fontFamily = Inter,
            modifier = Modifier
                .align(Alignment.End)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onSkip
                )
                // Keeps the tap target at 48dp without making the label itself look bulky.
                .padding(horizontal = 12.dp, vertical = 14.dp)
        )
    }
}

private val TutorialStep.tabName: String
    get() = when (this) {
        TutorialStep.INTRO -> ""
        TutorialStep.COMPLETE_TASK -> "Tasks"
        TutorialStep.SEE_ACHIEVEMENT -> ""
        TutorialStep.REDEEM_REWARD -> "Rewards"
    }

/**
 * How to actually get there. Achievements has no tab of its own (it hangs off Status), so it
 * needs the extra hop spelled out rather than pointing at a tab that does not exist.
 */
private fun TutorialStep.navHintFrom(currentRoute: String): String = when (this) {
    TutorialStep.INTRO -> ""
    TutorialStep.COMPLETE_TASK -> "Tap the glowing Tasks tab below."
    // Achievements hangs off Status, so the hint drops the first hop once it is made.
    TutorialStep.SEE_ACHIEVEMENT -> ""
    TutorialStep.REDEEM_REWARD -> "Tap the glowing Rewards tab below."
}

private val TutorialStep.title: String
    get() = when (this) {
        TutorialStep.INTRO -> ""
        TutorialStep.COMPLETE_TASK -> "Finish a task"
        TutorialStep.SEE_ACHIEVEMENT -> "You unlocked something"
        TutorialStep.REDEEM_REWARD -> "Now spend it"
    }

private val TutorialStep.body: String
    get() = when (this) {
        TutorialStep.INTRO -> ""
        TutorialStep.COMPLETE_TASK ->
            "Tap the \"${TutorialCoordinator.TUTORIAL_TASK_NAME}\" card. It pays " +
                "${TutorialCoordinator.TUTORIAL_TASK_POINTS} points, and every " +
                "${PlayerStats.POINTS_PER_STAT} points in a stat raises it by one."
        TutorialStep.SEE_ACHIEVEMENT ->
            "That popup was an achievement unlocking - it paid " +
                "${TutorialCoordinator.FIRST_TASK_REWARD} points on top of the task. " +
                "They unlock on their own as you play, and most pay you back."
        TutorialStep.REDEEM_REWARD ->
            "You have ${TutorialCoordinator.TUTORIAL_TASK_POINTS + TutorialCoordinator.FIRST_TASK_REWARD} " +
                "points - exactly what \"${TutorialCoordinator.TUTORIAL_REWARD_NAME}\" costs. " +
                "Tap Redeem on it."
    }
