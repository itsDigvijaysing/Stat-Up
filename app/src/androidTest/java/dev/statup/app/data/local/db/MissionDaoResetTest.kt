package dev.statup.app.data.local.db

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.statup.app.data.local.db.dao.MissionDao
import dev.statup.app.data.local.db.entity.MissionEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Guards a regression where `resetDailyCompletions()` had no `WHERE isDaily = 1`, un-completing
 * one-off missions every midnight - runs against real Room since a fake can't reproduce raw SQL bugs.
 */
@RunWith(AndroidJUnit4::class)
class MissionDaoResetTest {

    private lateinit var db: AppDatabase
    private lateinit var dao: MissionDao

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java
        ).build()
        dao = db.missionDao()
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun resetClearsDailiesAndLeavesOneOffsAlone() = runBlocking {
        val dailyId = dao.insert(mission(name = "Workout", isDaily = true))
        val oneOffId = dao.insert(mission(name = "File taxes", isDaily = false))
        assertEquals(1, dao.completeIfNotDone(dailyId))
        assertEquals(1, dao.completeIfNotDone(oneOffId))

        dao.resetDailyCompletions()

        val rows = dao.getAllMissions().first().associateBy { it.id }
        assertFalse("a daily must come back tomorrow", rows.getValue(dailyId).isCompletedToday)
        assertTrue(
            "a one-off must stay done, or it can be farmed for points every night",
            rows.getValue(oneOffId).isCompletedToday
        )
    }

    @Test
    fun completeIsConditionalSoASecondAttemptChangesNothing() = runBlocking {
        val id = dao.insert(mission(name = "Workout", isDaily = true))

        assertEquals("first completion lands", 1, dao.completeIfNotDone(id))
        assertEquals("second matches no rows - this is the double-tap guard", 0, dao.completeIfNotDone(id))
        assertEquals("streak incremented once, not twice", 1, dao.getById(id)!!.streak)
    }

    private fun mission(name: String, isDaily: Boolean) = MissionEntity(
        name = name,
        description = null,
        pointsReward = 4,
        statType = "STR",
        isDaily = isDaily,
        isCompletedToday = false,
        createdAt = System.currentTimeMillis()
    )
}
