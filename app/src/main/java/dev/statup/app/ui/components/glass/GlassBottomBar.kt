package dev.statup.app.ui.components.glass

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.statup.app.ui.theme.*

data class BottomNavItem(
    val label: String,
    val icon: ImageVector,
    val route: String
)

@Composable
fun GlassBottomBar(
    items: List<BottomNavItem>,
    selectedRoute: String,
    onItemClick: (String) -> Unit,
    modifier: Modifier = Modifier,
    /** Pulses one tab to point the guided tutorial at its next destination. */
    highlightRoute: String? = null
) {
    val shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)

    Box(
        // No fixed height: the glass must paint all the way to the screen edge (the app is
        // edge-to-edge), while the nav items sit above the system navigation bar. The Row
        // below owns the height - BottomBarHeight for content, plus the navigation-bar inset.
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            // Backdrop blur of scrolling content underneath. Falls back to tinted scrim on API < 31.
            .hazeEffectOrFallback(elevated = true)
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        GlassFill.copy(alpha = 0.10f),
                        GlassFill.copy(alpha = 0.06f),
                        GlassFill.copy(alpha = 0.04f)
                    )
                )
            )
            .drawBehind {
                // Top edge highlight
                drawRect(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.1f),
                            Color.Transparent
                        ),
                        startY = 0f,
                        endY = 8.dp.toPx()
                    )
                )
            }
            .border(
                width = 1.dp,
                brush = Brush.verticalGradient(
                    colors = listOf(
                        GlassBorder.copy(alpha = 0.25f),
                        GlassBorder.copy(alpha = 0.08f)
                    )
                ),
                shape = shape
            )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .height(GlassTokens.BottomBarHeight)
                .padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            items.forEach { item ->
                BottomNavItemView(
                    item = item,
                    isSelected = item.route == selectedRoute,
                    isHighlighted = item.route == highlightRoute,
                    onClick = { onItemClick(item.route) }
                )
            }
        }
    }
}

@Composable
private fun BottomNavItemView(
    item: BottomNavItem,
    isSelected: Boolean,
    isHighlighted: Boolean = false,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }

    // Slow breathing pulse so the tutorial can point at a tab without hijacking navigation -
    // the user still taps it themselves.
    val pulse = rememberInfiniteTransition(label = "navPulse")
    val pulseAlpha by pulse.animateFloat(
        initialValue = 0.25f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "navPulseAlpha"
    )

    val iconColor by animateColorAsState(
        targetValue = if (isSelected || isHighlighted) AccentPrimary else TextSecondary,
        animationSpec = tween(200),
        label = "iconColor"
    )

    val textColor by animateColorAsState(
        targetValue = if (isSelected || isHighlighted) AccentPrimary else TextSecondary,
        animationSpec = tween(200),
        label = "textColor"
    )

    val indicatorWidth by animateDpAsState(
        targetValue = if (isSelected) 48.dp else 0.dp,
        animationSpec = tween(250),
        label = "indicatorWidth"
    )

    Column(
        modifier = Modifier
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            // Expose this as a selectable tab to TalkBack: without it the bar reads as five
            // unlabeled clickables with no active/inactive state (selection is colour-only).
            .semantics { selected = isSelected; role = Role.Tab }
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // FIXED-size indicator slot (48x32) for every item, selected or not. The pill used
        // to exist only while selected inside a wrap-content Box, so selecting a tab grew
        // that item and re-laid-out the whole SpaceEvenly row - the visible "bar nudge" on
        // every tab tap. With constant geometry, only colors/alpha animate.
        Box(
            modifier = Modifier.size(width = 48.dp, height = 32.dp),
            contentAlignment = Alignment.Center
        ) {
            // The tutorial's pointer. An outlined ring, NOT a filled pill: a filled pill is
            // exactly how this bar draws the *selected* tab, so highlighting that way told the
            // user "you are here" when it meant "tap here". Background stays default.
            if (isHighlighted && !isSelected) {
                Box(
                    modifier = Modifier
                        .size(width = 48.dp, height = 32.dp)
                        .border(
                            width = 2.dp,
                            color = AccentPrimary.copy(alpha = pulseAlpha),
                            shape = RoundedCornerShape(16.dp)
                        )
                )
            }
            if (isSelected) {
                Box(
                    modifier = Modifier
                        .width(indicatorWidth)
                        .height(32.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(
                            brush = Brush.radialGradient(
                                colors = listOf(
                                    AccentPrimary.copy(alpha = 0.25f),
                                    AccentPrimary.copy(alpha = 0.1f)
                                )
                            )
                        )
                        .border(
                            width = 1.dp,
                            color = AccentPrimary.copy(alpha = 0.2f),
                            shape = RoundedCornerShape(16.dp)
                        )
                )
            }
            Icon(
                imageVector = item.icon,
                contentDescription = item.label,
                tint = iconColor,
                modifier = Modifier.size(GlassTokens.IconSize)
            )
        }

        Spacer(modifier = Modifier.height(4.dp))

        // Constant weight: the SemiBold/Normal swap changed the label's measured width,
        // contributing a second small layout shift. Selection now reads via color only.
        Text(
            text = item.label,
            color = textColor,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            fontFamily = Inter
        )
    }
}
