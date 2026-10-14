package dev.statup.app.data.repository

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AchievementRewardTest {

    @Test fun `a payout row resolves to its achievement id`() {
        assertEquals("streak_7", achievementIdFromReward("${ACHIEVEMENT_REWARD_PREFIX}streak_7"))
    }

    @Test fun `ordinary rows are not achievement payouts`() {
        assertNull(achievementIdFromReward("Todoist: Achievement reward plan"))
        assertNull(achievementIdFromReward("Completed: Morning walk"))
        assertNull(achievementIdFromReward(null))
    }
}
