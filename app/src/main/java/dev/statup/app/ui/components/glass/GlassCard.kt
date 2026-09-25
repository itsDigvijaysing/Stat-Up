package dev.statup.app.ui.components.glass

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.statup.app.ui.theme.*

@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = GlassTokens.CardRadius,
    fillColor: Color = GlassFill,
    borderColor: Color = GlassBorder,
    elevated: Boolean = false,
    onClick: (() -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed && onClick != null) GlassTokens.PressScale else 1f,
        animationSpec = tween(150),
        label = "cardScale"
    )

    val actualFill = if (elevated) GlassFillElevated else fillColor
    val actualBorder = if (elevated) GlassBorderElevated else borderColor
    val actualRadius = if (elevated) GlassTokens.CardRadiusElevated else cornerRadius

    val shape = RoundedCornerShape(actualRadius)

    Box(
        modifier = modifier
            .scale(scale)
            .clip(shape)
            // Real backdrop blur via Haze (when LocalHazeState is provided + API 31+).
            // Replaces the previous static gradient fill. On older devices Haze auto-falls-back
            // to a translucent scrim, so the card stays readable.
            .hazeEffectOrFallback(elevated = elevated)
            // A subtle tint sits on top of the blur - keeps brand color visible without losing readability.
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        actualFill.copy(alpha = actualFill.alpha * 0.6f),
                        actualFill.copy(alpha = actualFill.alpha * 0.4f),
                        actualFill.copy(alpha = actualFill.alpha * 0.5f)
                    )
                )
            )
            .drawBehind {
                // Inner glow effect - keep the soft highlight for "lit from above" feel
                drawRect(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.03f),
                            Color.Transparent
                        ),
                        center = Offset(size.width * 0.3f, size.height * 0.2f),
                        radius = size.maxDimension * 0.8f
                    )
                )
            }
            .border(GlassTokens.BorderWidth, actualBorder, shape)
            .then(
                if (onClick != null) {
                    Modifier.clickable(
                        interactionSource = interactionSource,
                        indication = null,
                        onClick = onClick
                    )
                } else Modifier
            )
            .padding(GlassTokens.CardPadding),
        content = content
    )
}

@Composable
fun GlassCardWithHighlight(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = GlassTokens.CardRadius,
    elevated: Boolean = false,
    onClick: (() -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed && onClick != null) GlassTokens.PressScale else 1f,
        animationSpec = tween(150),
        label = "cardScale"
    )

    val actualFill = if (elevated) GlassFillElevated else GlassFill
    val actualBorder = if (elevated) GlassBorderElevated else GlassBorder
    val actualRadius = if (elevated) GlassTokens.CardRadiusElevated else cornerRadius

    val shape = RoundedCornerShape(actualRadius)

    Box(
        modifier = modifier
            .scale(scale)
            .clip(shape)
            .hazeEffectOrFallback(elevated = elevated)
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        GlassHighlight.copy(alpha = 0.10f),
                        actualFill.copy(alpha = actualFill.alpha * 0.45f),
                        actualFill.copy(alpha = actualFill.alpha * 0.5f)
                    )
                )
            )
            .drawBehind {
                // Top highlight shimmer
                drawRect(
                    brush = Brush.linearGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.08f),
                            Color.White.copy(alpha = 0.02f),
                            Color.Transparent
                        ),
                        start = Offset(0f, 0f),
                        end = Offset(size.width, size.height * 0.4f)
                    )
                )
            }
            .border(GlassTokens.BorderWidth, actualBorder, shape)
            .then(
                if (onClick != null) {
                    Modifier.clickable(
                        interactionSource = interactionSource,
                        indication = null,
                        onClick = onClick
                    )
                } else Modifier
            )
            .padding(GlassTokens.CardPadding),
        content = content
    )
}

@Composable
fun GlassCardAccent(
    modifier: Modifier = Modifier,
    accentColor: Color = AccentPrimary,
    onClick: (() -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed && onClick != null) GlassTokens.PressScale else 1f,
        animationSpec = tween(150),
        label = "cardScale"
    )

    val shape = RoundedCornerShape(GlassTokens.CardRadius)

    Box(
        modifier = modifier
            .scale(scale)
            .clip(shape)
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        accentColor.copy(alpha = 0.2f),
                        accentColor.copy(alpha = 0.12f),
                        GlassFill.copy(alpha = 0.15f)
                    )
                )
            )
            .border(
                width = 1.dp,
                brush = Brush.verticalGradient(
                    colors = listOf(
                        accentColor.copy(alpha = 0.5f),
                        accentColor.copy(alpha = 0.2f)
                    )
                ),
                shape = shape
            )
            .then(
                if (onClick != null) {
                    Modifier.clickable(
                        interactionSource = interactionSource,
                        indication = null,
                        onClick = onClick
                    )
                } else Modifier
            )
            .padding(GlassTokens.CardPadding),
        content = content
    )
}
