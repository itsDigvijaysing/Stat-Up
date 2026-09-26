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
 * Fabricates a long player history so late-game UI (S/EX gates, filled hexagon) can be inspected.
 * Deliberately unreachable from the app - wire a temporary Settings button by hand for dev use.
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
