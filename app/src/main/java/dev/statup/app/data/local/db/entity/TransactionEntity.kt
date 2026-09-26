package dev.statup.app.data.local.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "transactions",
    // Unique on externalId so concurrent Todoist syncs can't double-insert; SQLite's UNIQUE
    // allows multiple NULLs, so manual transactions are unaffected.
    indices = [Index(value = ["externalId"], unique = true)]
)
data class TransactionEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val type: String,
    val source: String = "manual",
    val description: String?,
    val points: Int,
    val statType: String? = null,
    val relatedId: String? = null,
    val externalId: String? = null,
    val createdAt: Long = System.currentTimeMillis()
) {
    // Factory for backward compatibility
    companion object {
        fun create(
            type: String,
            points: Int,
            description: String?,
            statAffected: String? = null,
            externalId: String? = null,
            timestamp: Long = System.currentTimeMillis()
        ) = TransactionEntity(
            type = type,
            source = if (externalId != null) "TODOIST" else "MANUAL",
            description = description,
            points = points,
            statType = statAffected,
            externalId = externalId,
            createdAt = timestamp
        )
    }
}
