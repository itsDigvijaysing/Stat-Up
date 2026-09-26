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

/** Broadcasts `ACTION_APPWIDGET_UPDATE` for live widget IDs to refresh [StatsWidgetProvider].
 * [refresh] is debounced so a 200-task Todoist first sync collapses into one broadcast, not 200. */
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
