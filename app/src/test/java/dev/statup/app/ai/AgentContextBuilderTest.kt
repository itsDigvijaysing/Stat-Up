package dev.statup.app.ai

import dev.statup.app.data.local.db.dao.MissionDao
import dev.statup.app.data.local.db.dao.TransactionDao
import dev.statup.app.data.local.db.entity.MissionEntity
import dev.statup.app.data.local.db.entity.TransactionEntity
import dev.statup.app.data.repository.PlayerStateProvider
import dev.statup.app.domain.model.PlayerStats
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AgentContextBuilderTest {

    /**
     * Regression test for the earns-context starvation bug: when the most-recent transactions
     * are dominated by REDEEMs, the agent's "Recent earns" block must still surface real EARN
     * history rather than reporting "(none yet)". A type-filtered query fixes this; the old
     * "fetch recent N then filter to EARN" approach reported nothing.
     */
    @Test
    fun `recent earns are shown even when latest transactions are all redemptions`() = runTest {
        // 12 most-recent rows are all REDEEM (would starve a getRecent(12)+filter approach)...
        val redemptions = List(12) {
            TransactionEntity(type = "REDEEM", description = "Bought reward", points = 10)
        }
        // ...but the player does have EARN history.
        val earns = listOf(
            TransactionEntity(type = "EARN", description = "Study session", points = 5, statType = "INT"),
            TransactionEntity(type = "EARN", description = "Workout", points = 4, statType = "STR"),
        )

        val builder = AgentContextBuilder(
            playerState = FakePlayerState,
            transactionDao = FakeTransactionDao(recent = redemptions, recentEarns = earns),
            missionDao = FakeMissionDao,
        )

        val context = builder.build()

        assertTrue("expected a real earn in the context", context.contains("Study session"))
        assertFalse("earns block should not be empty", context.contains("(none yet)"))
    }

    private object FakePlayerState : PlayerStateProvider {
        override val username: Flow<String> = flowOf("Tester")
        override suspend fun getStatsOnce(): PlayerStats? = PlayerStats()
    }

    private class FakeTransactionDao(
        private val recent: List<TransactionEntity>,
        private val recentEarns: List<TransactionEntity>,
    ) : TransactionDao {
        override fun getRecent(limit: Int): Flow<List<TransactionEntity>> = flowOf(recent.take(limit))
        override fun getRecentByType(type: String, limit: Int): Flow<List<TransactionEntity>> =
            flowOf(recentEarns.filter { it.type == type }.take(limit))
        override fun getByType(type: String): Flow<List<TransactionEntity>> =
            flowOf(recentEarns.filter { it.type == type })

        override fun getAll(): Flow<List<TransactionEntity>> = flowOf(recent + recentEarns)
        override fun getByStatType(statType: String): Flow<List<TransactionEntity>> = error("unused")
        override fun getByDateRange(startTime: Long, endTime: Long): Flow<List<TransactionEntity>> = error("unused")
        override suspend fun getByExternalId(externalId: String): TransactionEntity? = error("unused")
        override suspend fun getActiveEarnDays(): List<String> = error("unused")
        override suspend fun deleteByDescriptionPrefix(prefix: String) = error("unused")
        override suspend fun getLifetimePointsForStat(statType: String): Int = error("unused")
        override suspend fun getUncategorisedEarns(): List<TransactionEntity> = error("unused")
        override suspend fun assignStatTypeIfMissing(id: Long, statType: String): Int = error("unused")
        override fun countUncategorisedEarns(): Flow<Int> = error("unused")
        override suspend fun getTotalEarned(): Int? = error("unused")
        override suspend fun getTotalRedeemed(): Int? = error("unused")
        override fun getBalance(): Flow<Int> = error("unused")
        override suspend fun getEarnedInRange(startTime: Long, endTime: Long): Int? = error("unused")
        override fun observeEarnedInRange(startTime: Long, endTime: Long): Flow<Int> = error("unused")
        override suspend fun insert(transaction: TransactionEntity): Long = error("unused")
        override suspend fun insertIgnore(transaction: TransactionEntity): Long = error("unused")
        override suspend fun delete(transaction: TransactionEntity) = error("unused")
        override suspend fun getTaskTransactionCount(): Int = error("unused")
        override fun countBySourceInRange(source: String, startTime: Long, endTime: Long): Flow<Int> = error("unused")
        override suspend fun deleteAll() = error("unused")
    }

    private object FakeMissionDao : MissionDao {
        override fun getAllMissions(): Flow<List<MissionEntity>> = flowOf(emptyList())
        override fun getAll(): Flow<List<MissionEntity>> = error("unused")
        override fun getDailyMissions(): Flow<List<MissionEntity>> = error("unused")
        override suspend fun getById(id: Long): MissionEntity? = error("unused")
        override suspend fun insert(mission: MissionEntity): Long = error("unused")
        override suspend fun update(mission: MissionEntity) = error("unused")
        override suspend fun markCompleted(id: Long, completed: Boolean, completedAt: Long) = error("unused")
        override suspend fun resetDailyCompletions() = error("unused")
        override suspend fun delete(mission: MissionEntity) = error("unused")
        override suspend fun deleteById(id: Long) = error("unused")
    }
}
