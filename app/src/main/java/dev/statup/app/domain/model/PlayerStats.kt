package dev.statup.app.domain.model

data class PlayerStats(
    val strStat: Int = BASE_STAT,
    val intStat: Int = BASE_STAT,
    val wisStat: Int = BASE_STAT,
    val dexStat: Int = BASE_STAT,
    val chaStat: Int = BASE_STAT,
    val vitStat: Int = BASE_STAT,
    val strPointsAcc: Int = 0,
    val intPointsAcc: Int = 0,
    val wisPointsAcc: Int = 0,
    val dexPointsAcc: Int = 0,
    val chaPointsAcc: Int = 0,
    val vitPointsAcc: Int = 0,
    val totalPointsEarned: Int = 0,
    val rank: Rank = Rank.E,
    val streak: Int = 0,
    val longestStreak: Int = 0,
    val rankUpStreakCounter: Int = 0,
    val rankDownBreakCounter: Int = 0,
    val streakShields: Int = 0,
    val lastActivityAt: Long? = null,
    val updatedAt: Long = System.currentTimeMillis()
) {
    fun getStat(type: StatType): Int = when (type) {
        StatType.STR -> strStat
        StatType.INT -> intStat
        StatType.WIS -> wisStat
        StatType.DEX -> dexStat
        StatType.CHA -> chaStat
        StatType.VIT -> vitStat
    }

    fun getStatAccumulator(type: StatType): Int = when (type) {
        StatType.STR -> strPointsAcc
        StatType.INT -> intPointsAcc
        StatType.WIS -> wisPointsAcc
        StatType.DEX -> dexPointsAcc
        StatType.CHA -> chaPointsAcc
        StatType.VIT -> vitPointsAcc
    }

    fun totalStats(): Int = strStat + intStat + wisStat + dexStat + chaStat + vitStat
    fun averageStat(): Float = totalStats() / 6f

    /**
     * Cumulative Work Days: +1 per active day, -1 per idle day, floored at 0 and never reset
     * on promotion. Backed by the legacy `rankUpStreakCounter` column (same column, new
     * meaning) so the change needs no DB migration.
     */
    val workDays: Int get() = rankUpStreakCounter

    companion object {
        const val MAX_STAT = 100
        const val BASE_STAT = 5
        const val POINTS_PER_STAT = 5

        // Streak Freeze Shield: bought with points, consumed automatically on an idle day.
        // A consumed shield turns the idle day into a "rest day" - no stat decay, Work Day
        // counter and streak untouched. Capped so decay stays a real threat (no hoarding).
        const val SHIELD_COST = 30
        const val MAX_SHIELDS = 3
    }
}
