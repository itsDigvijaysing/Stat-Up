package dev.statup.app.rpg

import dev.statup.app.domain.model.PlayerStats
import dev.statup.app.domain.model.StatType
import org.junit.Assert.assertEquals
import org.junit.Test

class PlayerStatsTest {

    @Test fun `default PlayerStats uses base values`() {
        val stats = PlayerStats()
        assertEquals(PlayerStats.BASE_STAT, stats.strStat)
        assertEquals(PlayerStats.BASE_STAT, stats.vitStat)
        assertEquals(6 * PlayerStats.BASE_STAT, stats.totalStats())
        assertEquals(PlayerStats.BASE_STAT.toFloat(), stats.averageStat(), 0.001f)
    }

    @Test fun `getStat returns correct value per StatType`() {
        val stats = PlayerStats(strStat = 10, intStat = 20, wisStat = 30, dexStat = 40, chaStat = 50, vitStat = 60)
        assertEquals(10, stats.getStat(StatType.STR))
        assertEquals(20, stats.getStat(StatType.INT))
        assertEquals(30, stats.getStat(StatType.WIS))
        assertEquals(40, stats.getStat(StatType.DEX))
        assertEquals(50, stats.getStat(StatType.CHA))
        assertEquals(60, stats.getStat(StatType.VIT))
    }

    @Test fun `points-per-stat invariant`() {
        // Documents the contract: 5 points = 1 stat point gain
        assertEquals(5, PlayerStats.POINTS_PER_STAT)
        assertEquals(100, PlayerStats.MAX_STAT)
        assertEquals(5, PlayerStats.BASE_STAT)
    }

    @Test fun `workDays reads the legacy counter column`() {
        // The column kept its name so the model change needed no DB migration.
        assertEquals(23, PlayerStats(rankUpStreakCounter = 23).workDays)
    }
}
