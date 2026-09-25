package dev.statup.app.ui.theme

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

object GlassTokens {
    // Corner Radius
    val CardRadius: Dp = 24.dp
    val CardRadiusElevated: Dp = 28.dp
    val ButtonRadius: Dp = 16.dp
    val InputRadius: Dp = 16.dp
    val BottomBarRadius: Dp = 28.dp
    val DialogRadius: Dp = 28.dp
    val BadgeRadius: Dp = 12.dp
    val PillRadius: Dp = 20.dp

    // Blur
    /**
     * Backdrop blur applied to the CONTENT behind a modal overlay (achievement celebration,
     * redeem confirmation). Blurring the content rather than the overlay avoids Haze's
     * source/effect relationship entirely - an effect nested inside the haze source cannot
     * reliably blur that source, which is why the redeem sheet stayed sharp while the
     * shell-level celebration blurred correctly.
     */
    val ModalBackdropBlur: Dp = 10.dp

    val CardBlur: Dp = 32.dp
    val CardBlurElevated: Dp = 40.dp
    val ButtonBlur: Dp = 8.dp
    val BottomBarBlur: Dp = 24.dp

    // Border
    val BorderWidth: Dp = 1.dp

    // Padding
    val CardPadding: Dp = 20.dp
    val CardPaddingSmall: Dp = 16.dp
    val CardPaddingLarge: Dp = 24.dp

    // Margin
    val CardMarginHorizontal: Dp = 12.dp
    val CardMarginVertical: Dp = 8.dp

    // Shadow
    val CardShadowElevation: Dp = 8.dp
    val CardShadowElevationHigh: Dp = 12.dp

    // Heights
    val BottomBarHeight: Dp = 72.dp
    val ButtonHeight: Dp = 52.dp
    val ButtonHeightSmall: Dp = 40.dp
    val InputHeight: Dp = 56.dp

    // Icon
    val IconSize: Dp = 24.dp
    val IconSizeSmall: Dp = 20.dp
    val IconSizeLarge: Dp = 28.dp

    // Animation
    const val PressScale: Float = 0.97f
    const val AnimationDurationShort: Int = 150
    const val AnimationDurationMedium: Int = 250
    const val AnimationDurationLong: Int = 400
}
