package dev.statup.app.ui.theme

import androidx.compose.ui.graphics.Color

// Background
val BackgroundBase = Color(0xFF0A0A0F)
val BackgroundSurface = Color(0xFF12121A)

// Ambient Gradient Orbs
val OrbViolet = Color(0xFF6C3CE1)
val OrbBlue = Color(0xFF2196F3)
val OrbPink = Color(0xFFE91E8A)
val OrbTeal = Color(0xFF00BFA5)

// Accent & Semantic
val AccentPrimary = Color(0xFF7C4DFF)
val AccentSecondary = Color(0xFF00E5FF)
val PointsGold = Color(0xFFFFD740)
val Success = Color(0xFF69F0AE)
val Warning = Color(0xFFFFB74D)
val Error = Color(0xFFFF5252)
val AccentSuccess = Color(0xFF69F0AE)
val AccentWarning = Color(0xFFFFB74D)
val AccentError = Color(0xFFFF5252)

// Text
val TextPrimary = Color(0xFFF0F0F5)
val TextSecondary = Color(0xFFA0A0B8)
// Lightened from #6B6B80 (3.8:1 on the #0A0A0F background - below WCAG AA) to #8A8AA0 (≈5.85:1)
// so tertiary informational text (quote attribution, sync status, legends, footers) passes AA.
val TextTertiary = Color(0xFF8A8AA0)
val TextOnAccent = Color.White

// Stat Colors
val StatSTR = Color(0xFFFF5252)
val StatINT = Color(0xFF448AFF)
val StatWIS = Color(0xFFAB47BC)
val StatDEX = Color(0xFFFFD740)
val StatCHA = Color(0xFFFF4081)
val StatVIT = Color(0xFF69F0AE)

// Stat Colors (alternative naming for cleaner code)
val StatStrength = Color(0xFFFF5252)
val StatIntelligence = Color(0xFF448AFF)
val StatWisdom = Color(0xFFAB47BC)
val StatDexterity = Color(0xFFFFD740)
val StatCharisma = Color(0xFFFF4081)
val StatVitality = Color(0xFF69F0AE)

// Rank Colors
val RankE = Color(0xFF9E9E9E)
val RankD = Color(0xFF8D6E63)
val RankC = Color(0xFF66BB6A)
val RankB = Color(0xFF42A5F5)
val RankA = Color(0xFFAB47BC)
val RankS = Color(0xFFFFD740)
// EX sits above S: a cold cyan-white so it reads as "past gold" rather than more gold.
val RankEX = Color(0xFF00E5FF)

// Glass
val GlassFill = Color.White.copy(alpha = 0.10f)
val GlassBorder = Color.White.copy(alpha = 0.18f)
val GlassHighlight = Color.White.copy(alpha = 0.08f)
val GlassFillElevated = Color.White.copy(alpha = 0.14f)
val GlassBorderElevated = Color.White.copy(alpha = 0.22f)

// Special
val TodoistRed = Color(0xFFE44332)
val StreakFireStart = Color(0xFFFF6D00)
val StreakFireEnd = Color(0xFFFFD740)
