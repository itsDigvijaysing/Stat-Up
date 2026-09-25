package dev.statup.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import dev.statup.app.ui.components.glass.GlassButton
import dev.statup.app.ui.components.glass.GlassCard
import dev.statup.app.ui.theme.AccentPrimary
import dev.statup.app.ui.theme.BackgroundBase
import dev.statup.app.ui.theme.Inter
import dev.statup.app.ui.theme.TextPrimary
import dev.statup.app.ui.theme.TextSecondary
import dev.statup.app.ui.theme.TextTertiary

/** One "how this tab works" point: a short bold claim plus a concrete example. */
data class HelpPoint(val title: String, val detail: String, val example: String? = null)

/**
 * The `?` affordance that sits left of a screen's primary action. Sized to 48dp like every other
 * icon button so it clears Core App Quality's Touch_Target_Size check.
 */
@Composable
fun HelpIconButton(onClick: () -> Unit, contentDescription: String = "How this works") {
    // Previously a bare glyph with nothing drawn behind it — testers never registered it as
    // tappable. It now carries the same glass circle as the other icon buttons so it reads as
    // an affordance, while the 48dp touch target (Core App Quality Touch_Target_Size) and the
    // quiet accent colour keep it from competing with the screen's primary action.
    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick)
            .semantics { this.contentDescription = contentDescription },
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(CircleShape)
                .background(AccentPrimary.copy(alpha = 0.14f))
                .border(1.dp, AccentPrimary.copy(alpha = 0.35f), CircleShape)
        )
        Text(
            text = "?",
            color = AccentPrimary,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = Inter
        )
    }
}

/**
 * Plain-language explainer for a tab. Deliberately short: a one-line intro and a handful of
 * points, each with an example, so a first-time user can skim it in a few seconds.
 */
@Composable
fun HelpDialog(
    title: String,
    intro: String,
    points: List<HelpPoint>,
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
            GlassCard(modifier = Modifier.fillMaxWidth(), elevated = true) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp)
                ) {
                    Text(
                        text = title,
                        color = TextPrimary,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = Inter
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = intro,
                        color = TextSecondary,
                        fontSize = 14.sp,
                        fontFamily = Inter
                    )

                    Spacer(modifier = Modifier.height(18.dp))

                    points.forEach { point ->
                        Row(modifier = Modifier.fillMaxWidth()) {
                            Text(
                                text = "•",
                                color = AccentPrimary,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = Inter
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = point.title,
                                    color = TextPrimary,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    fontFamily = Inter
                                )
                                Text(
                                    text = point.detail,
                                    color = TextSecondary,
                                    fontSize = 13.sp,
                                    fontFamily = Inter
                                )
                                point.example?.let {
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = "e.g. $it",
                                        color = TextTertiary,
                                        fontSize = 12.sp,
                                        fontFamily = Inter
                                    )
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(14.dp))
                    }

                    Spacer(modifier = Modifier.height(4.dp))

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
