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
 * One-time upgrade for pre-rebalance installs. Order matters: backfill must run before
 * recompute, since recompute sums `transactions.statType`.
 */
class StatUpgradeRunner(
    private val store: StatUpgradeStore,
    private val backfill: CategoryBackfill,
    private val recomputer: StatRecomputer
) {
    private val _state = MutableStateFlow<StatUpgradeState>(StatUpgradeState.Idle)
    val state: StateFlow<StatUpgradeState> = _state.asStateFlow()

    /**
     * @param isFreshInstall stamps the version and skips all work - nothing to recompute yet.
     *   Doesn't touch the tutorial flag; a second writer there once caused a race.
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
            // Only surface the progress screen once there's visible work; skipped entirely when
            // auto-categorisation is off, so the 96 KB blob is never read off disk.
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
