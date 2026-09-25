package dev.statup.app.ui.screen.help

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import dev.statup.app.domain.model.PlayerStats
import dev.statup.app.domain.model.Rank
import dev.statup.app.domain.model.StatType
import dev.statup.app.ui.components.glass.GlassCard
import dev.statup.app.ui.theme.*

/**
 * The complete explainer, in plain language: "work days", not "star lines", and no RPG jargon
 * beyond the stat names themselves.
 *
 * Every number on this screen is read from the live constants ([PlayerStats.POINTS_PER_STAT],
 * [Rank.daysRequired], [Rank.statsRequired], [StatType.blurb]) rather than written into the
 * copy, so rebalancing the game can never leave this page quietly lying to the user — which is
 * exactly what happened to the old hardcoded help text.
 */
@Composable
fun HowItWorksScreen(navController: NavController) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { navController.popBackStack() }) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = AccentPrimary
                )
            }
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = "How It Works",
                color = TextPrimary,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = Inter
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
        ) {
            Section("The loop") {
                Body(
                    "Do something you were going to do anyway. Tick it off here. You get " +
                        "points. Points do two things: they raise a stat, and they buy rewards " +
                        "you set for yourself."
                )
                Spacer(modifier = Modifier.height(8.dp))
                Body(
                    "The same points do both — spending them on a reward never takes away " +
                        "stat progress you already earned."
                )
            }

            Section("Points into stats") {
                Body(
                    "Every ${PlayerStats.POINTS_PER_STAT} points you earn in one stat raises " +
                        "that stat by 1. Leftovers carry over, so nothing is wasted."
                )
                Spacer(modifier = Modifier.height(8.dp))
                Body(
                    "Stats start at ${PlayerStats.BASE_STAT} and cap at ${PlayerStats.MAX_STAT}. " +
                        "A task is worth 1 to 4 points depending on how big it is."
                )
            }

            Section("The six stats") {
                StatType.entries.forEach { stat ->
                    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
                        Text(
                            text = stat.name,
                            color = stat.color,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = Inter,
                            modifier = Modifier.width(42.dp)
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stat.blurb,
                                color = TextSecondary,
                                fontSize = 13.sp,
                                fontFamily = Inter
                            )
                            Text(
                                text = stat.examples,
                                color = TextTertiary,
                                fontSize = 11.sp,
                                fontFamily = Inter
                            )
                        }
                    }
                }
            }

            Section("Work days and rank") {
                Body(
                    "Any day you earn at least one point counts as a work day. Work days add " +
                        "up for as long as you use the app and never reset — not even when you " +
                        "rank up."
                )
                Spacer(modifier = Modifier.height(8.dp))
                Body(
                    "To move up a rank you need both enough work days and a high enough average " +
                        "stat. Having one without the other is not enough."
                )
                Spacer(modifier = Modifier.height(12.dp))

                Row(modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp)) {
                    Text("Rank", color = TextTertiary, fontSize = 10.sp, fontFamily = Inter, modifier = Modifier.weight(1f))
                    Text("Work days", color = TextTertiary, fontSize = 10.sp, fontFamily = Inter, textAlign = TextAlign.End, modifier = Modifier.width(72.dp))
                    Text("Avg stat", color = TextTertiary, fontSize = 10.sp, fontFamily = Inter, textAlign = TextAlign.End, modifier = Modifier.width(60.dp))
                }
                Rank.entries.filter { it != Rank.E }.forEach { rank ->
                    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                        Text(
                            text = "${rank.name}  ${rank.title}",
                            color = rank.color,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            fontFamily = Inter,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = "${rank.daysRequired}",
                            color = TextSecondary,
                            fontSize = 13.sp,
                            fontFamily = Inter,
                            textAlign = TextAlign.End,
                            modifier = Modifier.width(72.dp)
                        )
                        Text(
                            text = "${rank.statsRequired}",
                            color = TextSecondary,
                            fontSize = 13.sp,
                            fontFamily = Inter,
                            textAlign = TextAlign.End,
                            modifier = Modifier.width(60.dp)
                        )
                    }
                }
            }

            Section("Missing a day") {
                Body(
                    "Miss a day and you lose one work day and one point from whichever stat is " +
                        "highest. Just one stat, not all six — one good day puts it back."
                )
                Spacer(modifier = Modifier.height(8.dp))
                Body(
                    "You only drop a rank if your work days fall below what that rank needs. " +
                        "Losing a stat point on its own never costs you a rank."
                )
                Spacer(modifier = Modifier.height(8.dp))
                Body(
                    "A Streak Freeze Shield (${PlayerStats.SHIELD_COST} points, up to " +
                        "${PlayerStats.MAX_SHIELDS} held) absorbs one missed day completely — " +
                        "no lost work day, no lost stat."
                )
            }

            Section("Stat categories") {
                Body(
                    "When you type a task name, the app guesses which stat it belongs to and " +
                        "pre-selects it. The guess runs on your device — nothing is sent " +
                        "anywhere. You can always change it, and a category you pick yourself " +
                        "is never overwritten."
                )
                Spacer(modifier = Modifier.height(8.dp))
                Body(
                    "Settings → Assign missing categories does the same for tasks you finished " +
                        "before this existed."
                )
            }

            Section("Rewards") {
                Body(
                    "Rewards are yours to invent — the starter list is only a suggestion. Edit " +
                        "them, delete them, set any cost up to 999,999. Redeeming spends points " +
                        "from your balance; your stats and rank are unaffected."
                )
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable ColumnScope.() -> Unit) {
    Spacer(modifier = Modifier.height(12.dp))
    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = title,
                color = TextPrimary,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = Inter
            )
            Spacer(modifier = Modifier.height(10.dp))
            content()
        }
    }
}

@Composable
private fun Body(text: String) {
    Text(
        text = text,
        color = TextSecondary,
        fontSize = 13.sp,
        fontFamily = Inter,
        lineHeight = 19.sp
    )
}
