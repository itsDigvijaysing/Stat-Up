package dev.statup.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import dev.statup.app.ui.theme.*

/**
 * Background "ambient orbs" layer used as the Haze blur source for the whole app shell.
 *
 * Performance note: this Canvas was previously driven by 3 simultaneous `infiniteRepeatable`
 * animations at 60 fps, which forced the whole screen to redraw every frame. Because this
 * layer is the Haze source, every glass card / bottom bar on top re-blurred every frame too -
 * the dominant cause of intermittent lag on devices with weak GPUs (Waydroid, older Mali).
 *
 * Orbs are now drawn once at their natural mid-orbit positions. The motion was a ~60-second
 * cycle over only 10% of screen width - visually almost imperceptible. The static version
 * looks the same and lets Haze cache its blur instead of recomputing it every frame.
 */
@Composable
fun AmbientBackground(
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier.fillMaxSize()) {
        val width = size.width
        val height = size.height

        // Background base
        drawRect(color = BackgroundBase)

        // Orb 1 - Violet (top-left)
        val orb1 = Offset(width * 0.2f, height * 0.25f)
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    OrbViolet.copy(alpha = 0.3f),
                    OrbViolet.copy(alpha = 0.1f),
                    Color.Transparent
                ),
                center = orb1,
                radius = width * 0.5f
            ),
            radius = width * 0.5f,
            center = orb1
        )

        // Orb 2 - Blue (center-right)
        val orb2 = Offset(width * 0.8f, height * 0.4f)
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    OrbBlue.copy(alpha = 0.25f),
                    OrbBlue.copy(alpha = 0.08f),
                    Color.Transparent
                ),
                center = orb2,
                radius = width * 0.45f
            ),
            radius = width * 0.45f,
            center = orb2
        )

        // Orb 3 - Pink (bottom-left)
        val orb3 = Offset(width * 0.3f, height * 0.75f)
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    OrbPink.copy(alpha = 0.2f),
                    OrbPink.copy(alpha = 0.05f),
                    Color.Transparent
                ),
                center = orb3,
                radius = width * 0.4f
            ),
            radius = width * 0.4f,
            center = orb3
        )

        // Orb 4 - Teal (bottom-right, subtle)
        val orb4 = Offset(width * 0.75f, height * 0.85f)
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    OrbTeal.copy(alpha = 0.15f),
                    OrbTeal.copy(alpha = 0.03f),
                    Color.Transparent
                ),
                center = orb4,
                radius = width * 0.35f
            ),
            radius = width * 0.35f,
            center = orb4
        )
    }
}
