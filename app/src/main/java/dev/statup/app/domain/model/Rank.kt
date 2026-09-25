package dev.statup.app.domain.model

import androidx.compose.ui.graphics.Color
import dev.statup.app.ui.theme.*

/**
 * The rank ladder. Promotion needs BOTH requirements; demotion is driven by [daysRequired]
 * alone so a stat lost to decay can never cost the user a rank.
 *
 * [daysRequired] is measured against the cumulative Work Day counter
 * (`player_stats.rankUpStreakCounter` - same column, new meaning), which is never reset on
 * promotion. [statsRequired] is measured against [PlayerStats.averageStat].
 */
enum class Rank(
    val title: String,
    val color: Color,
    val order: Int,
    val daysRequired: Int,
    val statsRequired: Int
) {
    E("Novice", RankE, 0, daysRequired = 0, statsRequired = 0),
    D("Apprentice", RankD, 1, daysRequired = 7, statsRequired = 6),
    C("Warrior", RankC, 2, daysRequired = 15, statsRequired = 14),
    B("Elite", RankB, 3, daysRequired = 30, statsRequired = 24),
    A("Champion", RankA, 4, daysRequired = 60, statsRequired = 36),
    S("Master", RankS, 5, daysRequired = 120, statsRequired = 50),
    EX("Sovereign", RankEX, 6, daysRequired = 240, statsRequired = 80);

    fun canRankUp(): Boolean = this != EX
    fun canRankDown(): Boolean = this != E

    fun nextRank(): Rank? = entries.find { it.order == order + 1 }
    fun previousRank(): Rank? = entries.find { it.order == order - 1 }

    companion object {
        fun fromString(value: String): Rank = entries.find { it.name == value } ?: E

        /**
         * Highest rank whose day AND stat requirements are both met - the promotion target.
         * [E] requires nothing, so this always resolves.
         */
        fun highestQualified(workDays: Int, averageStat: Float): Rank =
            entries.last { workDays >= it.daysRequired && averageStat >= it.statsRequired }

        /**
         * Highest rank whose day requirement alone is met - the demotion floor. Stats are
         * deliberately ignored: losing a stat point must never demote.
         */
        fun highestByDays(workDays: Int): Rank =
            entries.last { workDays >= it.daysRequired }
    }
}
