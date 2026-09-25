package dev.statup.app.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch

/**
 * Triggers a refresh of every [StatsWidgetProvider] instance currently on the user's home screen.
 *
 * Strategy: broadcast `ACTION_APPWIDGET_UPDATE` for the live widget IDs. That re-enters
 * [StatsWidgetProvider.onUpdate], which re-reads the DB and rebuilds the RemoteViews - so the
 * provider stays the single source of truth for how the widget looks.
 *
 * Call sites: data mutations that change anything the widget displays (rank, balance, streak,
 * today's earned points). Today that's [dev.statup.app.data.repository.PointsRepository]
 * (earn/redeem) and [dev.statup.app.rpg.DecayEngine] (daily tick → streak/rank).
 *
 * Cheap when no widget is on the home screen - `getAppWidgetIds()` returns empty and we early-out.
 *
 * [refresh] is COALESCED, not immediate: a Todoist first sync awards up to 200 tasks one at a
 * time, and an un-debounced broadcast per task means 200 widget rebuilds. The trailing debounce
 * collapses any burst into a single broadcast carrying the final state, and does it here so no
 * call site has to know about it.
 */
@OptIn(FlowPreview::class)
class StatsWidgetUpdater(private val appContext: Context) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    // replay = 1 so a refresh() that lands before the collector below has subscribed (StatUpApp
    // calls one right after Koin builds this singleton) is still delivered rather than dropped.
    private val requests = MutableSharedFlow<Unit>(
        replay = 1,
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )

    init {
        scope.launch {
            requests.debounce(DEBOUNCE_MS).collect { broadcast() }
        }
    }

    /** Request a refresh. Coalesced - a burst produces one broadcast [DEBOUNCE_MS] after the last. */
    fun refresh() {
        requests.tryEmit(Unit)
    }

    private fun broadcast() {
        val manager = AppWidgetManager.getInstance(appContext)
        val component = ComponentName(appContext, StatsWidgetProvider::class.java)
        val ids = manager.getAppWidgetIds(component)
        if (ids.isEmpty()) return

        val intent = Intent(AppWidgetManager.ACTION_APPWIDGET_UPDATE).apply {
            this.component = component
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
        }
        appContext.sendBroadcast(intent)
    }

    private companion object {
        const val DEBOUNCE_MS = 300L
    }
}
