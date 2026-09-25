package dev.statup.app.data.local.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * One row per decay event - **and** one synthetic bookkeeping row per processed day.
 *
 * `DecayEngine` writes a zero-loss row with `reason = "day_processed"` for every day it handles,
 * active or idle, as its idempotency marker: being in the same table as the day's mutation is what
 * makes "this day is done" commit atomically with it. Any UI that ever shows decay history **must
 * filter on `reason`**, or those rows will render as empty entries.
 *
 * Known limitation: the marker cannot recover the past. Builds before it left no record for the
 * commit-then-die case, so it prevents future replay but cannot detect a day that was already
 * mis-handled. Repairing that would need a migration or a heuristic; neither is worth it.
 */
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
