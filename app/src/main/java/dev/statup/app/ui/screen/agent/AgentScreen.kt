package dev.statup.app.ui.screen.agent

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import dev.statup.app.ai.AgentMessage
import dev.statup.app.ui.components.glass.GlassButton
import dev.jeziellago.compose.markdowntext.MarkdownText
import dev.statup.app.ui.components.glass.GlassButtonSmall
import dev.statup.app.ui.components.glass.GlassCard
import dev.statup.app.ui.navigation.Routes
import dev.statup.app.ui.components.HelpDialog
import dev.statup.app.ui.components.HelpIconButton
import dev.statup.app.ui.components.HelpPoint
import dev.statup.app.ui.theme.*
import org.koin.androidx.compose.koinViewModel

@Composable
fun AgentScreen(
    navController: NavController,
    viewModel: AgentViewModel = koinViewModel()
) {
    var showHelp by remember { mutableStateOf(false) }
    val uiState by viewModel.uiState.collectAsState()

    if (!uiState.isConfigured) {
        NotConfiguredState(onOpenSettings = { navController.navigate(Routes.SETTINGS) })
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
            .imePadding()
    ) {
        Header(
            messageCount = uiState.messages.size,
            onHelp = { showHelp = true },
            onClear = { viewModel.clearChat() }
        )

        if (showHelp) AgentHelpDialog(onDismiss = { showHelp = false })

        Spacer(modifier = Modifier.height(8.dp))

        if (uiState.messages.isEmpty()) {
            // weight(1f) matches MessageList below - without it, EmptyState only takes its
            // natural (short) height and AgentInput ends up wherever that leaves off instead
            // of pinned to the bottom, leaving it stranded mid-screen instead of docked above
            // the keyboard.
            EmptyState(
                onPickPrompt = { viewModel.sendMessage(it) },
                modifier = Modifier.weight(1f)
            )
        } else {
            MessageList(
                messages = uiState.messages,
                modifier = Modifier.weight(1f)
            )
        }

        AnimatedVisibility(visible = uiState.error != null) {
            ErrorBanner(
                text = uiState.error.orEmpty(),
                onDismiss = { viewModel.dismissError() }
            )
        }

        AgentInput(
            isSending = uiState.isSending,
            onSend = { viewModel.sendMessage(it) }
        )
    }
}

@Composable
private fun Header(messageCount: Int, onClear: () -> Unit, onHelp: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(
                text = "AI Coach",
                color = TextPrimary,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = Inter
            )
            Text(
                text = "Powered by Gemini · ephemeral chat",
                color = TextTertiary,
                fontSize = 12.sp,
                fontFamily = Inter
            )
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            HelpIconButton(onClick = onHelp)
            if (messageCount > 0) {
                GlassButtonSmall(
                    text = "Clear",
                    onClick = onClear,
                    primary = false
                )
            }
        }
    }
}

@Composable
private fun NotConfiguredState(onOpenSettings: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        GlassCard(modifier = Modifier.fillMaxWidth(), elevated = true) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(text = "🤖", fontSize = 56.sp)
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Connect AI Coach",
                    color = TextPrimary,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = Inter
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Add your free Gemini API key in Settings to chat with your coach. Your key is stored encrypted on this device only.",
                    color = TextSecondary,
                    fontSize = 14.sp,
                    fontFamily = Inter,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(20.dp))
                GlassButton(
                    text = "Open Settings",
                    onClick = onOpenSettings,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Composable
private fun EmptyState(onPickPrompt: (String) -> Unit, modifier: Modifier = Modifier) {
    val starters = listOf(
        "How am I doing this week?",
        "Suggest 3 quick STR missions",
        "What's my weakest stat?",
        "Help me build a bedtime routine"
    )
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(text = "💬", fontSize = 48.sp)
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = "Ask anything about your progress",
            color = TextPrimary,
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold,
            fontFamily = Inter
        )
        Spacer(modifier = Modifier.height(20.dp))
        starters.forEach { prompt ->
            GlassCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                onClick = { onPickPrompt(prompt) }
            ) {
                Text(
                    text = prompt,
                    color = TextPrimary,
                    fontSize = 14.sp,
                    fontFamily = Inter,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Composable
private fun MessageList(
    messages: List<AgentMessage>,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()
    // Auto-scroll to bottom on new message
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }
    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        state = listState,
        verticalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(vertical = 8.dp)
    ) {
        items(messages, key = { it.id }) { msg ->
            MessageBubble(msg)
        }
    }
}

@Composable
private fun MessageBubble(message: AgentMessage) {
    val isUser = message.role == AgentMessage.Role.USER
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        Box(
            modifier = Modifier
                .widthIn(max = 320.dp)
                .clip(
                    RoundedCornerShape(
                        topStart = 16.dp,
                        topEnd = 16.dp,
                        bottomStart = if (isUser) 16.dp else 4.dp,
                        bottomEnd = if (isUser) 4.dp else 16.dp
                    )
                )
                .background(
                    if (isUser) AccentPrimary.copy(alpha = 0.18f) else GlassFill.copy(alpha = 0.08f)
                )
                .padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            if (message.isPending) {
                Text(
                    text = "···",
                    color = TextSecondary,
                    fontSize = 16.sp,
                    fontFamily = Inter
                )
            } else if (isUser) {
                // User messages are plain text - they typed it.
                Text(
                    text = message.content,
                    color = TextPrimary,
                    fontSize = 14.sp,
                    fontFamily = Inter
                )
            } else {
                // Assistant replies render markdown: bold, italic, bullets, code spans.
                MarkdownText(
                    markdown = message.content,
                    style = androidx.compose.ui.text.TextStyle(
                        color = TextPrimary,
                        fontSize = 14.sp,
                        fontFamily = Inter
                    ),
                    linkColor = AccentPrimary
                )
            }
        }
    }
}

@Composable
private fun ErrorBanner(text: String, onDismiss: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(AccentError.copy(alpha = 0.15f))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = text,
            color = AccentError,
            fontSize = 12.sp,
            fontFamily = Inter,
            modifier = Modifier.weight(1f)
        )
        // 48dp accessibility minimum touch target (Core App Quality Touch_Target_Size).
        IconButton(onClick = onDismiss, modifier = Modifier.size(48.dp)) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "Dismiss",
                tint = AccentError,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

@Composable
private fun AgentInput(
    isSending: Boolean,
    onSend: (String) -> Unit
) {
    var input by remember { mutableStateOf("") }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(24.dp))
                .background(GlassFill.copy(alpha = 0.06f))
                .padding(horizontal = 16.dp, vertical = 12.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            if (input.isEmpty()) {
                Text(
                    text = if (isSending) "Thinking…" else "Ask your coach…",
                    color = TextTertiary,
                    fontSize = 15.sp,
                    fontFamily = Inter
                )
            }
            BasicTextField(
                value = input,
                onValueChange = { input = it },
                enabled = !isSending,
                textStyle = TextStyle(
                    color = TextPrimary,
                    fontSize = 15.sp,
                    fontFamily = Inter
                ),
                cursorBrush = SolidColor(AccentPrimary),
                modifier = Modifier.fillMaxWidth()
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        IconButton(
            onClick = {
                if (input.isNotBlank() && !isSending) {
                    onSend(input)
                    input = ""
                }
            },
            enabled = input.isNotBlank() && !isSending,
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(
                    if (input.isNotBlank() && !isSending) AccentPrimary.copy(alpha = 0.4f)
                    else GlassFill.copy(alpha = 0.04f)
                )
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.Send,
                contentDescription = "Send",
                tint = if (input.isNotBlank() && !isSending) TextPrimary else TextTertiary
            )
        }
    }
}

private val AGENT_HELP = listOf(
    HelpPoint("It knows your stats", "Ask about your own progress, not general trivia.", "\"which stat am I neglecting?\""),
    HelpPoint("Needs a free key", "Add a Google AI Studio key in Settings. Nothing is sent until you do."),
    HelpPoint("Chats aren't saved", "The conversation clears when you leave.")
)

@Composable
private fun AgentHelpDialog(onDismiss: () -> Unit) {
    HelpDialog(
        title = "AI Coach",
        intro = "A coach that already knows your stats. Ask it what to focus on next.",
        points = AGENT_HELP,
        onDismiss = onDismiss
    )
}
