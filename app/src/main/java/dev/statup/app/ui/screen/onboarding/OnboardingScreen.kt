package dev.statup.app.ui.screen.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoGraph
import androidx.compose.material.icons.outlined.CardGiftcard
import androidx.compose.material.icons.automirrored.outlined.TrendingUp
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.statup.app.ui.components.AmbientBackground
import dev.statup.app.ui.components.glass.GlassButton
import dev.statup.app.ui.components.glass.GlassTextField
import dev.statup.app.ui.theme.AccentPrimary
import dev.statup.app.ui.theme.Inter
import dev.statup.app.ui.theme.TextPrimary
import dev.statup.app.ui.theme.TextSecondary
import dev.statup.app.ui.theme.TextTertiary
import org.koin.androidx.compose.koinViewModel

private data class OnboardingStep(
    val icon: ImageVector,
    val title: String,
    val body: String
)

private val steps = listOf(
    OnboardingStep(
        Icons.AutoMirrored.Outlined.TrendingUp,
        "Welcome to Stat Up",
        "Turn the things you already do into a character you level up - one task at a time."
    ),
    OnboardingStep(
        Icons.Outlined.AutoGraph,
        "Level up six stats",
        "Completing tasks and missions earns points that raise STR, INT, WIS, DEX, CHA and VIT. Every day you earn something is a work day, and work days plus your average stat carry you up the ranks E → EX. Miss a day and you lose a work day and a point off your highest stat - so keep showing up."
    ),
    OnboardingStep(
        Icons.Outlined.CardGiftcard,
        "Spend what you earn",
        "Trade points for rewards you define yourself. Todoist sync and the AI coach are optional add-ons - everything else works fully offline, on your device."
    )
)

@Composable
fun OnboardingScreen(viewModel: OnboardingViewModel = koinViewModel()) {
    var step by remember { mutableStateOf(0) }
    var name by remember { mutableStateOf("") }
    val isLast = step == steps.lastIndex
    val current = steps[step]

    Box(modifier = Modifier.fillMaxSize()) {
        AmbientBackground()
        Column(
            // Onboarding renders outside MainShell's Scaffold, so nothing else reserves room
            // for the system bars - without this the "Next" button sits under the navigation
            // bar (gesture pill or 3-button alike). AmbientBackground stays edge-to-edge.
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.weight(1f))

            Icon(
                imageVector = current.icon,
                contentDescription = null,
                tint = AccentPrimary,
                modifier = Modifier.size(72.dp)
            )
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = current.title,
                color = TextPrimary,
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = Inter,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = current.body,
                color = TextSecondary,
                fontSize = 15.sp,
                fontFamily = Inter,
                textAlign = TextAlign.Center
            )

            // Name field only on the first step.
            if (step == 0) {
                Spacer(modifier = Modifier.height(24.dp))
                GlassTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = "What should we call you? (optional)",
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            // Step indicator dots
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                steps.indices.forEach { i ->
                    Box(
                        modifier = Modifier
                            .size(if (i == step) 10.dp else 8.dp)
                            .clip(CircleShape)
                            .background(if (i == step) AccentPrimary else TextTertiary)
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (step > 0) {
                    GlassButton(
                        text = "Back",
                        onClick = { step-- },
                        primary = false,
                        modifier = Modifier.weight(1f)
                    )
                }
                GlassButton(
                    text = if (isLast) "Start" else "Next",
                    onClick = { if (isLast) viewModel.complete(name) else step++ },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}
