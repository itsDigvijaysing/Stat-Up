package dev.statup.app.ui.screen.tutorial

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.statup.app.domain.model.PlayerStats
import dev.statup.app.domain.model.Rank
import dev.statup.app.domain.model.StatType
import dev.statup.app.ui.components.AmbientBackground
import dev.statup.app.ui.components.glass.GlassButton
import dev.statup.app.ui.components.glass.GlassCard
import dev.statup.app.ui.components.rememberHapticTick
import dev.statup.app.ui.theme.*
import org.koin.androidx.compose.koinViewModel

/**
 * The guided first run. Every step highlights exactly one thing and only that thing is
 * interactive, so a new user cannot wander off before they have seen the loop work once:
 * complete a task → points → a stat actually moves → spend the points → an achievement.
 *
 * Deliberately not skippable. Testers who skipped the old intro had no second chance to learn
 * the model, and the app has no tooltips to fall back on.
 */
@Composable
fun TutorialScreen(viewModel: TutorialViewModel = koinViewModel()) {
    val state by viewModel.uiState.collectAsState()
    val hapticTick = rememberHapticTick()

    Box(modifier = Modifier.fillMaxSize()) {
        AmbientBackground()
        Column(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            StepDots(current = state.step, total = TOTAL_STEPS)

            Spacer(modifier = Modifier.weight(1f))

            when (state.step) {
                0 -> Intro()
                1 -> CompleteTaskStep(
                    busy = state.isBusy,
                    onComplete = { hapticTick(); viewModel.completeSampleTask() }
                )
                2 -> EarnedStep(strBefore = state.strBefore, strAfter = state.currentStr)
                3 -> RedeemStep(
                    busy = state.isBusy,
                    onRedeem = { hapticTick(); viewModel.redeemSampleReward() }
                )
                4 -> AchievementStep(
                    name = state.achievementName,
                    points = state.achievementPoints
                )
                else -> RankStep(workDays = state.workDays)
            }

            Spacer(modifier = Modifier.weight(1f))

            // Steps 1 and 3 are gated on the highlighted action — no Next button to escape with.
            if (state.step != 1 && state.step != 3) {
                GlassButton(
                    text = if (state.step >= TOTAL_STEPS - 1) "Start playing" else "Next",
                    onClick = { if (state.step >= TOTAL_STEPS - 1) viewModel.finish() else viewModel.next() },
                    modifier = Modifier.fillMaxWidth()
                )
            } else {
                Text(
                    text = "Tap the card above to continue",
                    color = TextTertiary,
                    fontSize = 12.sp,
                    fontFamily = Inter
                )
                Spacer(modifier = Modifier.height(18.dp))
            }
        }
    }
}

private const val TOTAL_STEPS = 6

@Composable
private fun StepDots(current: Int, total: Int) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        repeat(total) { i ->
            Box(
                modifier = Modifier
                    .size(if (i == current) 9.dp else 7.dp)
                    .clip(CircleShape)
                    .background(if (i <= current) AccentPrimary else TextTertiary.copy(alpha = 0.4f))
            )
        }
    }
}

@Composable
private fun StepText(title: String, body: String) {
    Text(
        text = title,
        color = TextPrimary,
        fontSize = 24.sp,
        fontWeight = FontWeight.Bold,
        fontFamily = Inter,
        textAlign = TextAlign.Center
    )
    Spacer(modifier = Modifier.height(10.dp))
    Text(
        text = body,
        color = TextSecondary,
        fontSize = 15.sp,
        fontFamily = Inter,
        textAlign = TextAlign.Center,
        lineHeight = 21.sp
    )
}

@Composable
private fun ColumnScope.Intro() {
    Text(text = "⚡", fontSize = 56.sp)
    Spacer(modifier = Modifier.height(16.dp))
    StepText(
        title = "Let's do one real task",
        body = "Thirty seconds, start to finish. Everything you do here counts for real — " +
            "the points, the stat and the achievement are yours to keep."
    )
}

@Composable
private fun ColumnScope.CompleteTaskStep(busy: Boolean, onComplete: () -> Unit) {
    StepText(
        title = "Complete this task",
        body = "Tasks are worth points. This one is worth " +
            "${TutorialViewModel.TUTORIAL_POINTS}, and it trains ${StatType.STR.displayName}."
    )
    Spacer(modifier = Modifier.height(28.dp))

    GlassCard(
        modifier = Modifier.fillMaxWidth(),
        onClick = if (busy) null else onComplete
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(AccentPrimary.copy(alpha = 0.18f)),
                contentAlignment = Alignment.Center
            ) {
                Text(text = "○", color = AccentPrimary, fontSize = 16.sp)
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = TutorialViewModel.SAMPLE_TASK,
                    color = TextPrimary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = Inter
                )
                Text(
                    text = StatType.STR.name,
                    color = StatType.STR.color,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = Inter
                )
            }
            Text(
                text = "+${TutorialViewModel.TUTORIAL_POINTS}",
                color = PointsGold,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = Inter
            )
        }
    }
}

@Composable
private fun ColumnScope.EarnedStep(strBefore: Int, strAfter: Int) {
    StepText(
        title = "That's ${TutorialViewModel.TUTORIAL_POINTS} points",
        body = "Every ${PlayerStats.POINTS_PER_STAT} points you earn in a stat raises it by one. " +
            "You just moved ${StatType.STR.displayName}."
    )
    Spacer(modifier = Modifier.height(28.dp))

    val progress by animateFloatAsState(targetValue = 1f, animationSpec = tween(700), label = "str")
    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = StatType.STR.displayName,
                    color = StatType.STR.color,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = Inter,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = "$strBefore → $strAfter",
                    color = TextPrimary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = Inter
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(GlassFill)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(progress)
                        .clip(RoundedCornerShape(4.dp))
                        .background(StatType.STR.color)
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = StatType.STR.blurb,
                color = TextTertiary,
                fontSize = 12.sp,
                fontFamily = Inter
            )
        }
    }
}

@Composable
private fun ColumnScope.RedeemStep(busy: Boolean, onRedeem: () -> Unit) {
    StepText(
        title = "Now spend them",
        body = "Points are a currency. You set your own rewards and buy them with the points " +
            "you earned."
    )
    Spacer(modifier = Modifier.height(28.dp))

    GlassCard(
        modifier = Modifier.fillMaxWidth(),
        onClick = if (busy) null else onRedeem
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = "🍫", fontSize = 28.sp)
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "A small treat",
                    color = TextPrimary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = Inter
                )
                Text(
                    text = "${TutorialViewModel.TUTORIAL_POINTS} pts",
                    color = PointsGold,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = Inter
                )
            }
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(AccentPrimary.copy(alpha = 0.22f))
                    .padding(horizontal = 14.dp, vertical = 8.dp)
            ) {
                Text(
                    text = "Redeem",
                    color = TextPrimary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = Inter
                )
            }
        }
    }
}

@Composable
private fun ColumnScope.AchievementStep(name: String?, points: Int) {
    Text(text = "🏆", fontSize = 56.sp)
    Spacer(modifier = Modifier.height(16.dp))
    StepText(
        title = "Achievement unlocked",
        body = "Milestones unlock on their own as you play, and most of them pay points back."
    )
    Spacer(modifier = Modifier.height(24.dp))

    AnimatedVisibility(visible = name != null, enter = fadeIn(tween(400))) {
        GlassCard(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = name.orEmpty(),
                    color = TextPrimary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = Inter,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = "+$points pts",
                    color = PointsGold,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = Inter
                )
            }
        }
    }
}

@Composable
private fun ColumnScope.RankStep(workDays: Int) {
    StepText(
        title = "Keep showing up",
        body = "Any day you earn points is a work day. Collect " +
            "${Rank.D.daysRequired} of them and get your average stat to " +
            "${Rank.D.statsRequired} to reach Rank ${Rank.D.name}."
    )
    Spacer(modifier = Modifier.height(24.dp))

    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = "Work days  $workDays / ${Rank.D.daysRequired}",
                color = TextPrimary,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                fontFamily = Inter
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Miss a day and you lose one work day plus a point from your highest " +
                    "stat — they never reset to zero.",
                color = TextSecondary,
                fontSize = 13.sp,
                fontFamily = Inter,
                lineHeight = 18.sp
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "Stuck? Tap the ? on any screen.",
                color = AccentPrimary,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                fontFamily = Inter
            )
        }
    }
}
