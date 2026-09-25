package dev.statup.app.data.local.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import dev.statup.app.data.local.db.dao.*
import dev.statup.app.data.local.db.entity.*

// Single source of truth for the schema version. Bump this AND add the matching Migration to
// AppDatabase.ALL_MIGRATIONS when the schema changes - never lower it (downgrades wipe).
private const val DB_VERSION = 5

@Database(
    entities = [
        RewardEntity::class,
        TransactionEntity::class,
        MissionEntity::class,
        PlayerStatsEntity::class,
        StatMappingEntity::class,
        DecayLogEntity::class,
        TitleEntity::class,
        TodoistTaskEntity::class,
        AiMemoryEntity::class,
        AiConversationEntity::class
    ],
    version = DB_VERSION,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun rewardDao(): RewardDao
    abstract fun transactionDao(): TransactionDao
    abstract fun missionDao(): MissionDao
    abstract fun playerStatsDao(): PlayerStatsDao
    abstract fun statMappingDao(): StatMappingDao
    abstract fun decayLogDao(): DecayLogDao
    abstract fun titleDao(): TitleDao
    abstract fun aiMemoryDao(): AiMemoryDao

    companion object {
        const val DATABASE_NAME = "stat_up_db"

        /** Current schema version - exposed for MigrationTest. Mirror of [DB_VERSION]. */
        const val CURRENT_VERSION = DB_VERSION

        // v1 and v2 share the same identity hash - the bump was metadata-only.
        // We still need a registered Migration so v1 installs can upgrade without
        // hitting fallbackToDestructiveMigration.
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) { /* no-op */ }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE titles ADD COLUMN rewardPoints INTEGER NOT NULL DEFAULT 0")
            }
        }

        // Add a unique index on transactions.externalId so the Todoist sync dedupe
        // check + insert is enforced at the DB level (overlapping sync runs can race).
        // We first drop any duplicates so the index can be created.
        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    DELETE FROM transactions
                    WHERE externalId IS NOT NULL
                      AND id NOT IN (
                        SELECT MIN(id) FROM transactions
                        WHERE externalId IS NOT NULL
                        GROUP BY externalId
                      )
                    """.trimIndent()
                )
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS index_transactions_externalId " +
                            "ON transactions(externalId)"
                )
            }
        }

        // Streak Freeze Shields (v5): count of owned shields on the singleton stats row.
        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE player_stats ADD COLUMN streakShields INTEGER NOT NULL DEFAULT 0")
            }
        }

        /**
         * Every registered migration, in order. Exposed (internal) so the instrumented
         * MigrationTest can validate each upgrade path against the exported schemas in
         * app/schemas - see app/src/androidTest/.../MigrationTest.kt.
         *
         * When bumping [version], add the new Migration here. NEVER fall back to a
         * destructive wipe to "handle" a schema change - that silently erases user data on
         * update, which is exactly what the migration path prevents.
         */
        internal val ALL_MIGRATIONS: Array<Migration>
            get() = arrayOf(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)

        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    DATABASE_NAME
                )
                    .addMigrations(*ALL_MIGRATIONS)
                    // Registered migrations preserve user data across every version bump.
                    // We deliberately do NOT call fallbackToDestructiveMigration(): that sets
                    // requireMigration=false, so a FORGOTTEN upgrade migration would silently
                    // drop+recreate the tables and wipe the user's progress on update. With
                    // the default (requireMigration=true) a missing migration throws instead,
                    // surfacing the mistake in testing before it ships. Only a downgrade
                    // (impossible for end users - Play enforces monotonic versionCode) is
                    // allowed to destructively recreate, so dev-side reinstalls of older
                    // builds don't hard-crash.
                    .fallbackToDestructiveMigrationOnDowngrade(false)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
