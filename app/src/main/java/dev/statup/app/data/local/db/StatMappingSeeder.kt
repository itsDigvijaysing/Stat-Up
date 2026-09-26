package dev.statup.app.data.local.db

import androidx.room.withTransaction
import dev.statup.app.data.local.db.dao.StatMappingDao
import dev.statup.app.data.local.db.entity.StatMappingEntity

/**
 * Default Todoist label -> stat mappings, called from app startup and `fullReset` so a reset
 * doesn't leave the table empty until the next process start.
 */
object StatMappingSeeder {

    private val DEFAULTS = listOf(
        "STR" to "STR", "INT" to "INT", "WIS" to "WIS",
        "DEX" to "DEX", "CHA" to "CHA", "VIT" to "VIT"
    )

    /**
     * Seeds only if empty; the check-then-insert runs inside a transaction so a concurrent
     * caller (e.g. app start racing a reset) can't double-insert.
     */
    suspend fun seedIfEmpty(database: AppDatabase, dao: StatMappingDao) {
        database.withTransaction {
            if (dao.getAllOnce().isNotEmpty()) return@withTransaction
            insertDefaults(dao)
        }
    }

    /** Unconditional seed (e.g. after `database.clearAllTables()` during fullReset). */
    suspend fun seed(database: AppDatabase, dao: StatMappingDao) {
        database.withTransaction { insertDefaults(dao) }
    }

    private suspend fun insertDefaults(dao: StatMappingDao) {
        val now = System.currentTimeMillis()
        DEFAULTS.forEach { (label, stat) ->
            dao.insert(
                StatMappingEntity(
                    sourceType = "LABEL",
                    sourceId = label.lowercase(),
                    sourceName = label,
                    statType = stat,
                    createdAt = now
                )
            )
        }
    }
}
