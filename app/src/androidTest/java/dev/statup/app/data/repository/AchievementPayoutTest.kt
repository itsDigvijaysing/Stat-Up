package dev.statup.app.data.repository

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.statup.app.data.local.db.AppDatabase
import dev.statup.app.domain.model.Achievements
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The unlock-and-pay transaction, against real SQLite.
 *
 * The award used to run *after* the transaction that recorded the unlock, on the reasoning that it
 * opens its own transaction anyway. That left a window where the unlock committed and the payout did
 * not: the row then reads as unlocked so nothing retries it, and every caller wraps `updateProgress`
 * in `runCatching`, so the points were lost silently. This has to be an instrumented test - a
 * pass-through transactor cannot demonstrate a rollback, only a real one can.
 */
@RunWith(AndroidJUnit4::class)
class AchievementPayoutTest {

    private lateinit var db: AppDatabase

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java
        ).build()
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun unlockAndPayoutCommitTogether() = runBlocking {
        val paid = mutableListOf<Pair<String, Int>>()
        val repo = repository { id, points -> paid += id to points }
        repo.initializeAchievements()

        repo.updateProgress(FIRST_TASK, 1)

        assertTrue("unlock recorded", db.titleDao().getById(FIRST_TASK)!!.isUnlocked)
        assertEquals(
            "payout matches the achievement's own reward",
            listOf(FIRST_TASK to Achievements.getById(FIRST_TASK)!!.rewardPoints),
            paid
        )
    }

    @Test
    fun aFailingPayoutRollsTheUnlockBack() = runBlocking {
        val repo = repository { _, _ -> error("payout failed") }
        repo.initializeAchievements()

        runCatching { repo.updateProgress(FIRST_TASK, 1) }

        val row = db.titleDao().getById(FIRST_TASK)!!
        assertFalse(
            "an unlocked row with no payout is never retried, so the points would be lost for good",
            row.isUnlocked
        )
        assertNull("and the unlock timestamp must not survive either", row.unlockedAt)
    }

    @Test
    fun aSecondUpdateDoesNotPayTwice() = runBlocking {
        val paid = mutableListOf<Pair<String, Int>>()
        val repo = repository { id, points -> paid += id to points }
        repo.initializeAchievements()

        repo.updateProgress(FIRST_TASK, 1)
        repo.updateProgress(FIRST_TASK, 1)

        assertEquals("already-unlocked rows short-circuit before the award", 1, paid.size)
    }

    private fun repository(awarder: suspend (String, Int) -> Unit) = AchievementRepository(
        database = db,
        titleDao = db.titleDao(),
        unlockNotifier = null,
        pointsAwarder = awarder
    )

    private companion object {
        const val FIRST_TASK = "first_task"
    }
}
