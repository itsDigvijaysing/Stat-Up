package dev.statup.app.ai.classifier

import dev.statup.app.domain.model.StatType
import dev.statup.app.rpg.DecayStatsStore
import dev.statup.app.rpg.StatsEngine
import dev.statup.app.rpg.Transactor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** One completed earn that never got a stat assigned. */
data class UncategorisedEarn(val id: Long, val description: String?, val points: Int)

/** The slice of the transactions table the backfill needs. Implemented by `PointsRepository`. */
interface UncategorisedEarnStore {
    suspend fun uncategorisedEarns(): List<UncategorisedEarn>
    /** Writes the stat only if the row is still uncategorised. Returns true if it landed. */
    suspend fun assignStat(id: Long, stat: StatType): Boolean
}

/**
 * Assigns a stat to completed tasks that never got one. Applies incrementally rather than
 * re-deriving, since re-derivation would undo stat points already lost to decay.
 */
class CategoryBackfill(
    private val earnStore: UncategorisedEarnStore,
    private val statsStore: DecayStatsStore,
    private val classifier: TaskClassifier,
    private val transactor: Transactor
) {
    /**
     * Runs on [Dispatchers.Default] since `classify` blocks; each [CHUNK_SIZE] batch assigns rows
     * and credits stats in one transaction so a mid-run crash can't strand categorised-but-uncredited rows.
     */
    suspend fun run(
        onProgress: (done: Int, total: Int) -> Unit = { _, _ -> }
    ): BackfillResult = withContext(Dispatchers.Default) {
        val pending = earnStore.uncategorisedEarns()
        if (pending.isEmpty()) return@withContext BackfillResult(scanned = 0, categorised = 0)

        var categorised = 0
        var done = 0

        for (chunk in pending.chunked(CHUNK_SIZE)) {
            val credited = mutableMapOf<StatType, Int>()
            transactor.transaction {
                for (earn in chunk) {
                    val text = earn.description?.let(::stripSourcePrefix)
                    val suggestion = text?.takeIf { it.isNotBlank() }?.let(classifier::classify)
                    if (suggestion != null && earnStore.assignStat(earn.id, suggestion.stat)) {
                        categorised++
                        credited[suggestion.stat] = (credited[suggestion.stat] ?: 0) + earn.points
                    }
                }
                if (credited.isNotEmpty()) creditStats(credited)
            }
            done += chunk.size
            onProgress(done, pending.size)
        }

        BackfillResult(scanned = pending.size, categorised = categorised)
    }

    /** Applies accumulated points to the stats row. Caller supplies the transaction. */
    private suspend fun creditStats(credited: Map<StatType, Int>) {
        val stats = statsStore.getStatsOnce() ?: return
        var updated = stats
        for ((stat, points) in credited) {
            val progress = StatsEngine.applyPoints(
                currentStat = updated.getStat(stat),
                currentAccumulator = updated.getStatAccumulator(stat),
                points = points
            )
            updated = updated.withStat(stat, progress.stat, progress.accumulator)
        }
        statsStore.updateStats(updated.copy(updatedAt = System.currentTimeMillis()))
    }

    /** Strips the "Todoist: " plumbing prefix, which is a constant token with no classifier signal. */
    private fun stripSourcePrefix(description: String): String =
        description.removePrefix("Todoist: ").trim()

    private companion object {
        /** Rows per transaction: bounds crash loss, and bounds progress emissions to one per batch. */
        const val CHUNK_SIZE = 50
    }
}

data class BackfillResult(val scanned: Int, val categorised: Int)

private fun dev.statup.app.domain.model.PlayerStats.withStat(
    type: StatType,
    value: Int,
    accumulator: Int
) = when (type) {
    StatType.STR -> copy(strStat = value, strPointsAcc = accumulator)
    StatType.INT -> copy(intStat = value, intPointsAcc = accumulator)
    StatType.WIS -> copy(wisStat = value, wisPointsAcc = accumulator)
    StatType.DEX -> copy(dexStat = value, dexPointsAcc = accumulator)
    StatType.CHA -> copy(chaStat = value, chaPointsAcc = accumulator)
    StatType.VIT -> copy(vitStat = value, vitPointsAcc = accumulator)
}
