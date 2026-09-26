package dev.statup.app.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback

/** Whether in-app haptics are enabled - mirrors the Settings "Haptic Feedback" toggle. */
val LocalHapticsEnabled = staticCompositionLocalOf { true }

/**
 * A haptic tick, no-op when the Settings toggle is off. Uses the platform haptic feedback API, so
 * it needs no VIBRATE permission and respects the system's touch-feedback setting.
 */
@Composable
fun rememberHapticTick(type: HapticFeedbackType = HapticFeedbackType.LongPress): () -> Unit {
    val haptics = LocalHapticFeedback.current
    val enabled = LocalHapticsEnabled.current
    return remember(haptics, enabled, type) {
        { if (enabled) haptics.performHapticFeedback(type) }
    }
}
