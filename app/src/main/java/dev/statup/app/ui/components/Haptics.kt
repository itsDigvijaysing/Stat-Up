package dev.statup.app.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback

/** Whether in-app haptics are enabled - mirrors the Settings "Haptic Feedback" toggle. */
val LocalHapticsEnabled = staticCompositionLocalOf { true }

/**
 * Returns a callback that performs a short haptic tick when haptics are enabled, and is a no-op
 * when the user has turned the Settings toggle off. Use to confirm high-signal actions (redeem,
 * mission complete, mood check-in, shield buy, rank-up). Uses the platform haptic feedback, so
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
