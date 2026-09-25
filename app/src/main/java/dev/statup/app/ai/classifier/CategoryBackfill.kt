package dev.statup.app.ai.classifier

import dev.statup.app.domain.model.StatType
import dev.statup.app.rpg.DecayStatsStore
import dev.statup.app.rpg.StatsEngine
import dev.statup.app.rpg.Transactor

/** One completed earn that never got a stat assigned. */
data class UncategorisedEarn(val id: Long, val description: String?, val points: Int)

/** The slice of the transactions table the backfill needs. Implemented by `PointsRepository`. */
interface UncategorisedEarnStore {
    suspend fun uncategorisedEarns(): List<UncategorisedEarn>
    /** Writes the stat only if the row is still uncategorised. Returns true if it landed. */
    suspend fun assignStat(id: Long, stat: StatType): Boolean
}

/**
 * Assigns a stat to completed tasks that never got one — the case where points landed in the
 * balance but grew no stat at all.
 *
 * Only ever touches rows whose `statType IS NULL`; a category the user picked, or one a Todoist
 * label routed, is never overwritten. The UPDATE re-checks the NULL in SQL, so a concurrent
 * categorisation wins over this pass rather than being clobbered.
 *
 * Runs once automatically before the one-time stat recompute, and is re-runnable by hand from
 * Settings afterwards. The automatic first pass is followed by a full rebuild, so the
 * accumulator credit here only matters for those later manual runs — which is exactly why it
 * is applied incrementally instead of re-deriving every stat (a re-derivation would silently
 * undo every stat point the user has since lost to decay).
 */
class CategoryBackfill(
    private val earnStore: UncategorisedEarnStore,
    private val statsStore: DecayStatsStore,
    private val classifier: TaskClassifier,
    private val transactor: Transactor
) {
    suspend fun run(onProgress: (done: Int, total: Int) -> Unit = { _, _ -> }): BackfillResult {
        val pending = earnStore.uncategorisedEarns()
        if (pending.isEmpty()) return BackfillResult(scanned = 0, categorised = 0)

        var categorised = 0
        val credited = mutableMapOf<StatType, Int>()

        pending.forEachIndexed { index, earn ->
            val text = earn.description?.let(::stripSourcePrefix)
            val suggestion = text?.takeIf { it.isNotBlank() }?.let(classifier::classify)
            if (suggestion != null && earnStore.assignStat(earn.id, suggestion.stat)) {
                categorised++
                credited[suggestion.stat] = (credited[suggestion.stat] ?: 0) + earn.points
            }
            onProgress(index + 1, pending.size)
        }

        if (credited.isNotEmpty()) {
            transactor.transaction {
                val stats = statsStore.getStatsOnce() ?: return@transaction
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
        }

        return BackfillResult(scanned = pending.size, categorised = categorised)
    }

    /**
     * Todoist earns are stored as "Todoist: <task title>". The prefix is app plumbing, not part
     * of the task, and every such row would otherwise share it — feeding the model a constant
     * token that carries no signal.
     */
    private fun stripSourcePrefix(description: String): String =
        description.removePrefix("Todoist: ").trim()
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
