package dev.statup.app.ui.components.rpg

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.statup.app.domain.model.PlayerStats
import dev.statup.app.domain.model.StatType
import dev.statup.app.ui.theme.*
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

enum class HexagonStyle {
    SIMPLE,  // Clean overlay chart with theme color, no vertex dots
    GLOW    // Radial glow effect spreading from center
}

@Composable
fun HexagonRadarChart(
    stats: PlayerStats,
    modifier: Modifier = Modifier,
    size: Dp = 200.dp,
    // Off by default: the breathe animation sits on the Haze blur source layer, so leaving it
    // on continuously re-blurs the bottom bar/cards every frame while Status is open.
    showAnimation: Boolean = false,
    showLabels: Boolean = true,
    style: HexagonStyle = HexagonStyle.SIMPLE
) {
    val infiniteTransition = rememberInfiniteTransition(label = "breathe")
    val breatheScale by infiniteTransition.animateFloat(
        initialValue = 0.98f,
        targetValue = 1.02f,
        animationSpec = infiniteRepeatable(
            animation = tween(3000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "breatheScale"
    )

    val animatedStr by animateFloatAsState(
        targetValue = stats.strStat.toFloat() / PlayerStats.MAX_STAT,
        animationSpec = tween(500, easing = FastOutSlowInEasing),
        label = "str"
    )
    val animatedInt by animateFloatAsState(
        targetValue = stats.intStat.toFloat() / PlayerStats.MAX_STAT,
        animationSpec = tween(500, easing = FastOutSlowInEasing),
        label = "int"
    )
    val animatedWis by animateFloatAsState(
        targetValue = stats.wisStat.toFloat() / PlayerStats.MAX_STAT,
        animationSpec = tween(500, easing = FastOutSlowInEasing),
        label = "wis"
    )
    val animatedDex by animateFloatAsState(
        targetValue = stats.dexStat.toFloat() / PlayerStats.MAX_STAT,
        animationSpec = tween(500, easing = FastOutSlowInEasing),
        label = "dex"
    )
    val animatedCha by animateFloatAsState(
        targetValue = stats.chaStat.toFloat() / PlayerStats.MAX_STAT,
        animationSpec = tween(500, easing = FastOutSlowInEasing),
        label = "cha"
    )
    val animatedVit by animateFloatAsState(
        targetValue = stats.vitStat.toFloat() / PlayerStats.MAX_STAT,
        animationSpec = tween(500, easing = FastOutSlowInEasing),
        label = "vit"
    )

    val statValues = listOf(animatedStr, animatedInt, animatedWis, animatedDex, animatedCha, animatedVit)
    val statColors = StatType.entries.map { it.color }
    val scale = if (showAnimation) breatheScale else 1f

    // Stats with values for labels
    val statLabels = listOf(
        StatType.STR to stats.strStat,
        StatType.INT to stats.intStat,
        StatType.WIS to stats.wisStat,
        StatType.DEX to stats.dexStat,
        StatType.CHA to stats.chaStat,
        StatType.VIT to stats.vitStat
    )

    Box(
        modifier = modifier.size(size + 48.dp),
        contentAlignment = Alignment.Center
    ) {
        // Canvas for hexagon
        Canvas(modifier = Modifier.size(size)) {
            val center = Offset(this.size.width / 2, this.size.height / 2)
            val radius = this.size.minDimension / 2 * 0.85f * scale

            drawHexagonGrid(center, radius)
            
            when (style) {
                HexagonStyle.SIMPLE -> drawSimpleStatPolygon(center, radius, statValues)
                HexagonStyle.GLOW -> drawGlowStatPolygon(center, radius, statValues, statColors)
            }
            
            // Only draw vertex dots for glow style
            if (style == HexagonStyle.GLOW) {
                drawVertexGlow(center, radius, statValues, statColors)
            }
        }

        // Labels positioned around hexagon
        if (showLabels) {
            HexagonLabels(
                stats = statLabels,
                chartSize = size
            )
        }
    }
}

@Composable
private fun HexagonLabels(
    stats: List<Pair<StatType, Int>>,
    chartSize: Dp
) {
    val labelOffset = chartSize / 2 + 24.dp

    Layout(
        content = {
            stats.forEach { (stat, value) ->
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = stat.name,
                        style = TextStyle(
                            color = stat.color,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = Inter,
                            textAlign = TextAlign.Center
                        )
                    )
                    Text(
                        text = "$value",
                        style = TextStyle(
                            color = stat.color.copy(alpha = 0.8f),
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Medium,
                            fontFamily = Inter,
                            textAlign = TextAlign.Center
                        )
                    )
                }
            }
        }
    ) { measurables, constraints ->
        val placeables = measurables.map { it.measure(constraints) }
        val centerX = constraints.maxWidth / 2
        val centerY = constraints.maxHeight / 2
        val radius = labelOffset.toPx()

        layout(constraints.maxWidth, constraints.maxHeight) {
            placeables.forEachIndexed { index, placeable ->
                val angle = -PI / 2 + (2 * PI / 6) * index
                val x = centerX + (radius * cos(angle)).toInt() - placeable.width / 2
                val y = centerY + (radius * sin(angle)).toInt() - placeable.height / 2
                placeable.place(x, y)
            }
        }
    }
}

private fun DrawScope.drawHexagonGrid(center: Offset, radius: Float) {
    val gridLevels = listOf(0.25f, 0.5f, 0.75f, 1f)
    val gridColor = Color.White.copy(alpha = 0.08f)

    gridLevels.forEach { level ->
        val points = getHexagonPoints(center, radius * level)
        val path = Path().apply {
            moveTo(points.first().x, points.first().y)
            points.drop(1).forEach { lineTo(it.x, it.y) }
            close()
        }
        drawPath(
            path = path,
            color = gridColor,
            style = Stroke(width = 1.dp.toPx())
        )
    }

    // Draw lines from center to vertices
    val outerPoints = getHexagonPoints(center, radius)
    outerPoints.forEach { point ->
        drawLine(
            color = gridColor,
            start = center,
            end = point,
            strokeWidth = 1.dp.toPx()
        )
    }
}

// Simple style: Clean overlay with single theme color, no vertex dots
private fun DrawScope.drawSimpleStatPolygon(
    center: Offset,
    radius: Float,
    statValues: List<Float>
) {
    val points = statValues.mapIndexed { index, value ->
        val angle = -PI / 2 + (2 * PI / 6) * index
        val r = radius * value.coerceIn(0.05f, 1f)
        Offset(
            x = center.x + (r * cos(angle)).toFloat(),
            y = center.y + (r * sin(angle)).toFloat()
        )
    }

    val path = Path().apply {
        if (points.isNotEmpty()) {
            moveTo(points.first().x, points.first().y)
            points.drop(1).forEach { lineTo(it.x, it.y) }
            close()
        }
    }

    // Clean fill with theme accent color
    val fillGradient = Brush.radialGradient(
        colorStops = arrayOf(
            0.0f to AccentPrimary.copy(alpha = 0.35f),
            0.6f to AccentPrimary.copy(alpha = 0.2f),
            1.0f to AccentPrimary.copy(alpha = 0.08f)
        ),
        center = center,
        radius = radius
    )

    drawPath(
        path = path,
        brush = fillGradient
    )

    // Clean border with theme color
    drawPath(
        path = path,
        color = AccentPrimary,
        style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
    )
}

// Glow style: Radial glow spreading from center with multicolor
private fun DrawScope.drawGlowStatPolygon(
    center: Offset,
    radius: Float,
    statValues: List<Float>,
    statColors: List<Color>
) {
    val points = statValues.mapIndexed { index, value ->
        val angle = -PI / 2 + (2 * PI / 6) * index
        val r = radius * value.coerceIn(0.05f, 1f)
        Offset(
            x = center.x + (r * cos(angle)).toFloat(),
            y = center.y + (r * sin(angle)).toFloat()
        )
    }

    val path = Path().apply {
        if (points.isNotEmpty()) {
            moveTo(points.first().x, points.first().y)
            points.drop(1).forEach { lineTo(it.x, it.y) }
            close()
        }
    }

    // Calculate average stat for intensity
    val avgStat = statValues.average().toFloat().coerceIn(0.1f, 1f)
    
    // RPG-style radial glow spreading from center based on stats
    val glowGradient = Brush.radialGradient(
        colorStops = arrayOf(
            0.0f to Color(0xFFFFFFFF).copy(alpha = 0.15f * avgStat),
            0.2f to Color(0xFF7C4DFF).copy(alpha = 0.5f * avgStat),
            0.5f to Color(0xFF00E5FF).copy(alpha = 0.35f * avgStat),
            0.75f to Color(0xFF7C4DFF).copy(alpha = 0.2f * avgStat),
            1.0f to Color.Transparent
        ),
        center = center,
        radius = radius * 1.1f
    )

    drawPath(
        path = path,
        brush = glowGradient
    )
    
    // Inner glow from center (power aura effect)
    val innerGlow = Brush.radialGradient(
        colorStops = arrayOf(
            0.0f to Color(0xFFFFFFFF).copy(alpha = 0.25f * avgStat),
            0.3f to Color(0xFF7C4DFF).copy(alpha = 0.15f * avgStat),
            1.0f to Color.Transparent
        ),
        center = center,
        radius = radius * 0.4f
    )
    
    drawCircle(
        brush = innerGlow,
        radius = radius * 0.4f,
        center = center
    )

    // Draw border with glow
    drawPath(
        path = path,
        brush = Brush.sweepGradient(
            colors = statColors + statColors.first(),
            center = center
        ),
        style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
    )
}

private fun DrawScope.drawVertexGlow(
    center: Offset,
    radius: Float,
    statValues: List<Float>,
    statColors: List<Color>
) {
    statValues.forEachIndexed { index, value ->
        val angle = -PI / 2 + (2 * PI / 6) * index
        val r = radius * value.coerceIn(0.05f, 1f)
        val point = Offset(
            x = center.x + (r * cos(angle)).toFloat(),
            y = center.y + (r * sin(angle)).toFloat()
        )

        // Glow
        drawCircle(
            color = statColors[index].copy(alpha = 0.3f),
            radius = 6.dp.toPx(),
            center = point
        )

        // Point
        drawCircle(
            color = statColors[index],
            radius = 3.dp.toPx(),
            center = point
        )
    }
}

private fun getHexagonPoints(center: Offset, radius: Float): List<Offset> {
    return (0 until 6).map { i ->
        val angle = -PI / 2 + (2 * PI / 6) * i
        Offset(
            x = center.x + (radius * cos(angle)).toFloat(),
            y = center.y + (radius * sin(angle)).toFloat()
        )
    }
}
