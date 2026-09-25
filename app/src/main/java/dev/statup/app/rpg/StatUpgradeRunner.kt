package dev.statup.app.rpg

import dev.statup.app.ai.classifier.CategoryBackfill
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** The preference slice the one-time upgrade needs. Implemented by `UserPreferences`. */
interface StatUpgradeStore {
    suspend fun getStatCurveVersion(): Int
    suspend fun setStatCurveVersion(version: Int)
    /** Off means the classifier is never consulted, so the backfill is skipped entirely. */
    suspend fun isAutoCategoriseEnabled(): Boolean
}

/**
 * The one-time upgrade for installs created before the progression rebalance: categorise the
 * history that never got a stat, then rebuild every stat from lifetime points on the new
 * 5-points-per-stat curve.
 *
 * Order matters - the recompute sums `transactions.statType`, so it has to run *after* the
 * backfill or the tasks that were just categorised wouldn't count toward anything.
 *
 * Gated on a stored curve version, so it runs exactly once per install no matter how often
 * the process starts. Exposes [state] so the UI can hold a progress screen in front of the
 * user rather than letting their stats change under them mid-session.
 */
class StatUpgradeRunner(
    private val store: StatUpgradeStore,
    private val backfill: CategoryBackfill,
    private val recomputer: StatRecomputer
) {
    private val _state = MutableStateFlow<StatUpgradeState>(StatUpgradeState.Idle)
    val state: StateFlow<StatUpgradeState> = _state.asStateFlow()

    /**
     * @param isFreshInstall skips all the work and just stamps the version. A brand-new install has
     *   no history to categorise and no stats to rebuild, so the recompute would only rewrite the
     *   singleton row with the values it already holds - and this is now the common path, not the
     *   rare one. The tutorial flag is **not** touched here any more; `UserPreferences`
     *   `resolveFirstRunFlags` owns that decision, and having two writers for it is what made the
     *   old behaviour depend on which coroutine finished first.
     */
    suspend fun runIfNeeded(isFreshInstall: Boolean = false) {
        if (store.getStatCurveVersion() >= StatRecomputer.CURVE_VERSION) {
            _state.value = StatUpgradeState.Done
            return
        }
        if (isFreshInstall) {
            store.setStatCurveVersion(StatRecomputer.CURVE_VERSION)
            _state.value = StatUpgradeState.Done
            return
        }
        try {
            // Only surface the progress screen once we know there is visible work to do; a
            // fresh install has nothing to categorise and must not flash an upgrade screen.
            // Skipped entirely when auto-categorisation is off: "off" has to mean the model is never
            // consulted, and the 96 KB blob is never even read off disk.
            if (store.isAutoCategoriseEnabled()) {
                backfill.run { done, total ->
                    if (total > 0) _state.value = StatUpgradeState.Categorising(done, total)
                }
            }
            if (_state.value is StatUpgradeState.Categorising) {
                _state.value = StatUpgradeState.Rebuilding
            }
            recomputer.recompute()
            store.setStatCurveVersion(StatRecomputer.CURVE_VERSION)
        } finally {
            // A failure here must not wedge the app behind the progress screen. The version
            // flag stays unset, so the upgrade is simply retried on the next launch.
            _state.value = StatUpgradeState.Done
        }
    }
}

sealed class StatUpgradeState {
    data object Idle : StatUpgradeState()
    data class Categorising(val done: Int, val total: Int) : StatUpgradeState()
    data object Rebuilding : StatUpgradeState()
    data object Done : StatUpgradeState()

    val isRunning: Boolean get() = this is Categorising || this is Rebuilding
}
