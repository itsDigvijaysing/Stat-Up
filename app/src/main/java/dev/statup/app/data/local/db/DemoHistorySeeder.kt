package dev.statup.app.data.local.db

import androidx.room.withTransaction
import dev.statup.app.data.local.db.dao.TransactionDao
import dev.statup.app.data.local.db.entity.TransactionEntity
import dev.statup.app.domain.model.StatType
import dev.statup.app.domain.model.TransactionSource
import dev.statup.app.domain.model.TransactionType
import java.time.LocalDate
import java.time.ZoneId

/**
 * Fabricates the history of a long-running player so the late-game UI can be looked at
 * without waiting months - rank badges, a filled hexagon, the S/EX gates, and how the Status
 * footer copes with three- and four-digit values.
 *
 * **Deliberately not reachable from the app.** It was briefly exposed behind a
 * `BuildConfig.DEBUG` Settings row and that was removed: a button that invents hundreds of
 * completed tasks is a footgun sitting next to real user data, and `BuildConfig.DEBUG` is one
 * wrong build variant away from shipping. The code stays because the late-game screens are
 * otherwise untestable.
 *
 * To use it, temporarily add this to `SettingsScreen` and remove it again before committing:
 * ```
 * GlassButton("Seed demo", onClick = { viewModel.seedDemoHistory(clearFirst = false) })
 * GlassButton("Clear demo", onClick = { viewModel.seedDemoHistory(clearFirst = true) })
 * ```
 * `clear` only deletes rows carrying the [MARKER] prefix, so real history is never touched.
 *
 * Writes plain EARN rows dated across the last [DAYS] days and nothing else. Stats, Work Days
 * and rank are then re-derived by the normal `StatRecomputer`, so what you see is what the
 * real engine produces from that history - not hand-set numbers that could disagree with it.
 */
object DemoHistorySeeder {

    const val DAYS = 200
    const val TASKS_PER_DAY = 4
    private const val POINTS_PER_TASK = 3
    const val MARKER = "[demo] "

    /** Rotates so every stat grows, with a realistic lean toward admin/life tasks. */
    private val ROTATION = listOf(
        StatType.WIS, StatType.INT, StatType.STR, StatType.CHA,
        StatType.WIS, StatType.VIT, StatType.DEX, StatType.INT
    )

    suspend fun seed(database: AppDatabase, transactionDao: TransactionDao) {
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        database.withTransaction {
            var n = 0
            for (dayOffset in DAYS downTo 1) {
                val dayStart = today.minusDays(dayOffset.toLong())
                    .atStartOfDay(zone).toInstant().toEpochMilli()
                repeat(TASKS_PER_DAY) { i ->
                    val stat = ROTATION[n % ROTATION.size]
                    transactionDao.insert(
                        TransactionEntity(
                            type = TransactionType.EARN.name,
                            source = TransactionSource.MISSION.name,
                            description = "$MARKER${stat.displayName} task",
                            points = POINTS_PER_TASK,
                            statType = stat.name,
                            // Spread through the day so nothing collides on one timestamp.
                            createdAt = dayStart + (i + 1) * 3_600_000L,
                            externalId = null
                        )
                    )
                    n++
                }
            }
        }
    }

    /** Removes only the fabricated rows, leaving any real history alone. */
    suspend fun clear(database: AppDatabase, transactionDao: TransactionDao) {
        database.withTransaction {
            transactionDao.deleteByDescriptionPrefix("$MARKER%")
        }
    }
}
