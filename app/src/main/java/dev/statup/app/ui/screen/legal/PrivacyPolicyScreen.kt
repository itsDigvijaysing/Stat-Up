package dev.statup.app.ui.screen.legal

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.produceState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import dev.statup.app.ui.components.glass.GlassCard
import dev.statup.app.ui.theme.AccentPrimary
import dev.statup.app.ui.theme.Inter
import dev.statup.app.ui.theme.TextPrimary
import dev.statup.app.ui.theme.TextSecondary
import dev.jeziellago.compose.markdowntext.MarkdownText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Reads from `assets/privacy_policy.md` (synced from repo-root PRIVACY_POLICY.md by
 * `syncPrivacyPolicy`) - Play requires the policy reachable in-app, not just the store listing.
 */
@Composable
fun PrivacyPolicyScreen(navController: NavController) {
    val context = LocalContext.current

    // `null` while the asset is still being read off the main thread.
    val markdown by produceState<String?>(initialValue = null, context) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                context.assets.open(POLICY_ASSET).bufferedReader().use { it.readText() }
            }.getOrElse { FALLBACK_TEXT }
        }
    }

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
                    tint = TextSecondary
                )
            }
            Text(
                text = "Privacy Policy",
                color = TextPrimary,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = Inter
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        GlassCard(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState())
            ) {
                val text = markdown
                if (text == null) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        CircularProgressIndicator(color = AccentPrimary)
                    }
                } else {
                    MarkdownText(
                        markdown = text,
                        style = TextStyle(
                            color = TextPrimary,
                            fontSize = 13.sp,
                            fontFamily = Inter
                        ),
                        linkColor = AccentPrimary
                    )
                }
            }
        }
    }
}

private const val POLICY_ASSET = "privacy_policy.md"

/** Shown only if the asset is somehow missing - the policy must never render as a blank page. */
private const val FALLBACK_TEXT = """
# Privacy Policy

Stat Up is offline-first. Your stats, tasks, rewards and history stay on your device and are
never sent anywhere. Todoist sync and the Gemini AI Coach are opt-in and only run if you add
your own API key in Settings.

The full policy is published at:
https://github.com/itsDigvijaysing/Stat-Up/blob/main/PRIVACY_POLICY.md
"""
