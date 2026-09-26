package dev.statup.app.data.local.db

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * `seedIfNeeded` writes rows then sets its DataStore flag, so a crash in between must not
 * duplicate rows on retry - the seed itself is idempotent by name to cover that gap.
 */
@RunWith(AndroidJUnit4::class)
class StarterContentSeederTest {

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
    fun seedingTwiceDoesNotDuplicateAnything() = runBlocking {
        StarterContentSeeder.seed(db, db.missionDao(), db.rewardDao())
        val missionsAfterFirst = db.missionDao().getAllMissions().first().size
        val rewardsAfterFirst = db.rewardDao().getAll().first().size

        // Second pass = the crash-before-flag-write case replaying on the next launch.
        StarterContentSeeder.seed(db, db.missionDao(), db.rewardDao())

        assertEquals(missionsAfterFirst, db.missionDao().getAllMissions().first().size)
        assertEquals(rewardsAfterFirst, db.rewardDao().getAll().first().size)
    }

    @Test
    fun seedingFillsInOnlyWhatIsMissing() = runBlocking {
        StarterContentSeeder.seed(db, db.missionDao(), db.rewardDao())
        val missions = db.missionDao().getAllMissions().first()
        db.missionDao().delete(missions.first())
        assertEquals(missions.size - 1, db.missionDao().getAllMissions().first().size)

        StarterContentSeeder.seed(db, db.missionDao(), db.rewardDao())

        assertEquals(
            "the deleted starter mission comes back, the rest are not duplicated",
            missions.size,
            db.missionDao().getAllMissions().first().size
        )
    }

    @Test
    fun theTutorialsTargetRewardIsTheCheapestOne() = runBlocking {
        StarterContentSeeder.seed(db, db.missionDao(), db.rewardDao())

        val cheapest = db.rewardDao().getAll().first().minBy { it.pointsCost }

        // The tour quotes this cost to the user and asks them to spend exactly that, so the constants
        // it reads have to agree with what actually got seeded.
        assertEquals(StarterContentSeeder.CHEAPEST_REWARD_NAME, cheapest.name)
        assertEquals(StarterContentSeeder.CHEAPEST_REWARD_COST, cheapest.pointsCost)
    }
}
