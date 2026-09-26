package dev.statup.app.data.local.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/** Also holds a zero-loss `reason = "day_processed"` row per day, DecayEngine's idempotency
 * marker (atomic with the day's mutation) - any decay-history UI must filter on `reason`. */
@Entity(tableName = "decay_log")
data class DecayLogEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val strLost: Int = 0,
    val intLost: Int = 0,
    val wisLost: Int = 0,
    val dexLost: Int = 0,
    val chaLost: Int = 0,
    val vitLost: Int = 0,
    val idleHours: Int?,
    val reason: String?,
    val createdAt: Long
)
