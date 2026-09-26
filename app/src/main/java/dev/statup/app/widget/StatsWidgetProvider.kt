package dev.statup.app.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import dev.statup.app.MainActivity
import dev.statup.app.R
import dev.statup.app.data.local.db.AppDatabase
import dev.statup.app.data.local.db.entity.PlayerStatsEntity
import dev.statup.app.domain.model.Rank
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId

/**
 * 4x2 home-screen widget. onUpdate arrives via BroadcastReceiver.onReceive on the MAIN thread,
 * so the DB read is moved off it with goAsync() rather than blocking there.
 */
class StatsWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        // goAsync() keeps the receiver alive while reading off the main thread; blocking here
        // instead risks an ANR on three DB queries per broadcast.
        val pending = goAsync()
        val appContext = context.applicationContext
        scope.launch {
            try {
                val snapshot = readSnapshot(appContext)
                appWidgetIds.forEach { id ->
                    appWidgetManager.updateAppWidget(id, buildViews(appContext, snapshot))
                }
            } finally {
                pending.finish()
            }
        }
    }

    private suspend fun readSnapshot(context: Context): WidgetSnapshot {
        val db = AppDatabase.getInstance(context)
        val stats = db.playerStatsDao().getStatsOnce()
            ?: PlayerStatsEntity(id = 1, lastActivityAt = null, updatedAt = System.currentTimeMillis())
        val txDao = db.transactionDao()
        val (start, end) = todayMillisRange()
        val today = txDao.getEarnedInRange(start, end) ?: 0
        val balance = txDao.getBalance().first()
        return WidgetSnapshot(stats, today, balance)
    }

    /** Local-day bounds derived from LocalDate, so DST days (23h/25h) stay correct. */
    private fun todayMillisRange(): Pair<Long, Long> {
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        return today.atStartOfDay(zone).toInstant().toEpochMilli() to
            today.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
    }

    private fun buildViews(context: Context, snapshot: WidgetSnapshot): RemoteViews {
        val rank = runCatching { Rank.valueOf(snapshot.stats.rank) }.getOrDefault(Rank.E)

        return RemoteViews(context.packageName, R.layout.widget_stats).apply {
            setTextViewText(R.id.widget_rank, rank.name)
            setTextViewText(R.id.widget_rank_title, rank.title)
            setTextViewText(R.id.widget_balance, "✨ ${snapshot.balance}")
            setTextViewText(R.id.widget_streak, "🔥 ${snapshot.stats.streak}d")
            setTextViewText(R.id.widget_today, "+${snapshot.today} today")

            setOnClickPendingIntent(R.id.widget_root, openAppIntent(context))
        }
    }

    private fun openAppIntent(context: Context): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        return PendingIntent.getActivity(
            context,
            REQUEST_OPEN_APP,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private data class WidgetSnapshot(
        val stats: PlayerStatsEntity,
        val today: Int,
        val balance: Int
    )

    companion object {
        private const val REQUEST_OPEN_APP = 100

        // One scope for the class, not the instance: Provider instances are transient (recreated
        // per broadcast), so a scope can't hang off `this`.
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    }
}
