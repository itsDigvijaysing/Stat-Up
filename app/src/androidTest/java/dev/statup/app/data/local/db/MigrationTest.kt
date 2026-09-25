package dev.statup.app.data.local.db

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Guards the "updates must preserve user data" guarantee.
 *
 * Creates a v1 database and runs the registered migrations all the way to the current
 * version, validating the resulting schema against the exported JSON in app/schemas at
 * each step. If a future version bump forgets to add a migration (which would otherwise
 * silently wipe user data on update), this test fails.
 *
 * Instrumented - run on a device/emulator (e.g. Waydroid):
 *     ./gradlew connectedDebugAndroidTest
 */
@RunWith(AndroidJUnit4::class)
class MigrationTest {

    private val testDb = "migration-test"

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java
    )

    @Test
    fun migrateFrom1ToLatest_preservesSchemaAtEveryStep() {
        // Seed an empty v1 database, then migrate up to the current version. Validation of
        // the final schema against schemas/4.json is done by runMigrationsAndValidate.
        helper.createDatabase(testDb, 1).close()
        helper.runMigrationsAndValidate(
            testDb,
            AppDatabase.CURRENT_VERSION,
            /* validateDroppedTables = */ true,
            *AppDatabase.ALL_MIGRATIONS
        )
    }

    /**
     * Data-survival guard for MIGRATION_3_4. Seeds a v3 database with two duplicate-externalId
     * rows and one NULL-externalId manual row, migrates to the current version, and asserts the
     * dedupe kept the MIN(id) survivor, left the manual row untouched, and created the unique
     * index. The empty-DB test above only validates the schema - a regression in the dedupe
     * DELETE (e.g. dropping the `externalId IS NOT NULL` guard) would pass it while silently
     * wiping real transaction history on update; this test catches that.
     */
    @Test
    fun migration3to4_dedupesDuplicatesAndPreservesManualRows() {
        helper.createDatabase(testDb, 3).apply {
            // Two Todoist rows sharing externalId 'ext-dup' - MIN(id) = 10 must survive...
            execSQL("INSERT INTO transactions (id, type, source, points, externalId, createdAt) VALUES (10, 'EARN', 'TODOIST', 4, 'ext-dup', 1000)")
            execSQL("INSERT INTO transactions (id, type, source, points, externalId, createdAt) VALUES (20, 'EARN', 'TODOIST', 4, 'ext-dup', 2000)")
            // ...a unique Todoist row that must be kept...
            execSQL("INSERT INTO transactions (id, type, source, points, externalId, createdAt) VALUES (30, 'EARN', 'TODOIST', 2, 'ext-unique', 3000)")
            // ...and a manual row with no externalId, which must be untouched.
            execSQL("INSERT INTO transactions (id, type, source, points, externalId, createdAt) VALUES (40, 'EARN', 'MANUAL', 5, NULL, 4000)")
            close()
        }

        val db = helper.runMigrationsAndValidate(
            testDb,
            AppDatabase.CURRENT_VERSION,
            /* validateDroppedTables = */ true,
            *AppDatabase.ALL_MIGRATIONS
        )

        // The duplicate externalId collapsed to exactly one row, keeping the lowest id.
        db.query("SELECT id FROM transactions WHERE externalId = 'ext-dup'").use { c ->
            assertEquals("duplicate externalId should collapse to one row", 1, c.count)
            c.moveToFirst()
            assertEquals("MIN(id) survivor kept", 10L, c.getLong(0))
        }
        // The manual (NULL externalId) row is untouched, its data intact.
        db.query("SELECT points FROM transactions WHERE externalId IS NULL").use { c ->
            assertEquals("manual row must survive", 1, c.count)
            c.moveToFirst()
            assertEquals("manual row data intact", 5, c.getInt(0))
        }
        // Nothing else was dropped: 4 inserted - 1 duplicate = 3 rows.
        db.query("SELECT COUNT(*) FROM transactions").use { c ->
            c.moveToFirst()
            assertEquals("only the duplicate was removed", 3, c.getInt(0))
        }
        // The unique index that enforces dedupe at the DB level now exists.
        db.query("SELECT COUNT(*) FROM sqlite_master WHERE type = 'index' AND name = 'index_transactions_externalId'").use { c ->
            c.moveToFirst()
            assertEquals("unique externalId index created", 1, c.getInt(0))
        }
    }
}
