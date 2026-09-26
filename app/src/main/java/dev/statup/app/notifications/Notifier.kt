package dev.statup.app.notifications

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import dev.statup.app.MainActivity
import dev.statup.app.R

/**
 * Single entry point for all app notifications - centralises channel creation, permission
 * checks and `NotificationCompat.Builder` boilerplate.
 */
class Notifier(private val context: Context) {

    init {
        createChannels()
    }

    private fun createChannels() {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_SYNC,
                "Todoist sync",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Result of background and manual Todoist sync runs"
                setShowBadge(false)
            }
        )
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_REMINDERS,
                "Reminders",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Streak warnings, decay reminders, achievement unlocks"
            }
        )
    }

    fun arePermissionsGranted(): Boolean {
        // Pre-Android 13: notifications work without runtime permission as long as the user
        // hasn't disabled them in settings.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            return NotificationManagerCompat.from(context).areNotificationsEnabled()
        }
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun showSyncResult(tasksProcessed: Int, pointsEarned: Int) {
        if (tasksProcessed == 0) return // No new tasks - don't bother the user
        notify(
            id = NOTIF_SYNC_RESULT,
            channel = CHANNEL_SYNC,
            title = "Todoist synced",
            body = "$tasksProcessed task${if (tasksProcessed == 1) "" else "s"} → +$pointsEarned pts"
        )
    }

    fun showSyncAuthFailure() {
        notify(
            id = NOTIF_SYNC_AUTH,
            channel = CHANNEL_SYNC,
            title = "Todoist disconnected",
            body = "Your token is invalid or expired. Tap to reconnect in Settings."
        )
    }

    /**
     * Posted from DecayWorker because promotion can happen at midnight with the app closed,
     * when StatusViewModel's live rank-up animation never fires.
     */
    fun showRankUp(rankName: String) {
        notify(
            id = NOTIF_RANK_UP,
            channel = CHANNEL_REMINDERS,
            title = "Rank up - $rankName! 🎉",
            body = "Your work days and stats both cleared the bar. Open Stat Up to see your new status."
        )
    }

    /** An idle day dropped the player a rank - nudge them to come back and climb. */
    fun showRankDown(rankName: String) {
        notify(
            id = NOTIF_RANK_DOWN,
            channel = CHANNEL_REMINDERS,
            title = "Rank dropped to $rankName",
            body = "An idle day cost you a rank. Complete a task today to start climbing back."
        )
    }

    /** A Streak Freeze Shield absorbed an idle day - reassure and prompt re-engagement. */
    fun showShieldUsed(shieldsLeft: Int) {
        notify(
            id = NOTIF_SHIELD_USED,
            channel = CHANNEL_REMINDERS,
            title = "Streak Freeze used 🛡️",
            body = if (shieldsLeft > 0) {
                "A shield absorbed your idle day - streak safe. $shieldsLeft left."
            } else {
                "Your last shield absorbed your idle day. Complete a task to stay safe."
            }
        )
    }

    /**
     * Lint can't see through [arePermissionsGranted] (it only recognises inline
     * `checkSelfPermission`), so MissingPermission is suppressed - the runtime guard above is real.
     */
    @SuppressLint("MissingPermission")
    private fun notify(id: Int, channel: String, title: String, body: String) {
        if (!arePermissionsGranted()) return
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pending = PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_launcher_monochrome)
            .setContentTitle(title)
            .setContentText(body)
            .setContentIntent(pending)
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(context).notify(id, notification)
    }

    companion object {
        const val CHANNEL_SYNC = "sync"
        const val CHANNEL_REMINDERS = "reminders"

        private const val NOTIF_SYNC_RESULT = 1001
        private const val NOTIF_SYNC_AUTH = 1002
        private const val NOTIF_RANK_DOWN = 1003
        private const val NOTIF_SHIELD_USED = 1004
        private const val NOTIF_RANK_UP = 1005
    }
}
