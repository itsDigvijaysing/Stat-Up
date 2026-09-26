package dev.statup.app.sync

import android.content.Context
import androidx.work.*
import dev.statup.app.data.repository.MissionRepository
import dev.statup.app.notifications.Notifier
import dev.statup.app.rpg.DailyDecayResult
import dev.statup.app.rpg.DecayEngine
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.util.Calendar
import java.util.concurrent.TimeUnit

class DecayWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params), KoinComponent {

    private val decayEngine: DecayEngine by inject()
    private val missionRepository: MissionRepository by inject()
    private val notifier: Notifier by inject()

    override suspend fun doWork(): Result {
        return try {
            val result = decayEngine.applyDailyDecay()
            // Resets daily missions even on days the user never opens Tasks; idempotent via
            // lastMissionResetDay, independent of decay's own retry marker.
            missionRepository.resetDailyIfNeeded()
            // Only consequential outcomes (rank change, shield consumed) notify - ordinary
            // active/idle days stay silent to avoid daily spam.
            when (result) {
                is DailyDecayResult.ActiveWithRankUp -> notifier.showRankUp(result.newRank.name)
                is DailyDecayResult.IdleWithRankDown -> notifier.showRankDown(result.newRank.name)
                is DailyDecayResult.ShieldConsumed -> notifier.showShieldUsed(result.shieldsLeft)
                else -> {}
            }
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }

    companion object {
        private const val WORK_NAME = "daily_decay"

        fun schedule(context: Context) {
            // Calculate delay until next midnight
            val now = Calendar.getInstance()
            val midnight = Calendar.getInstance().apply {
                add(Calendar.DAY_OF_YEAR, 1)
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            val delayMillis = midnight.timeInMillis - now.timeInMillis

            val request = PeriodicWorkRequestBuilder<DecayWorker>(
                24, TimeUnit.HOURS
            )
                .setInitialDelay(delayMillis, TimeUnit.MILLISECONDS)
                .setBackoffCriteria(
                    BackoffPolicy.LINEAR,
                    15, TimeUnit.MINUTES
                )
                .build()

            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(
                    WORK_NAME,
                    ExistingPeriodicWorkPolicy.KEEP,
                    request
                )
        }

        fun runNow(context: Context) {
            val request = OneTimeWorkRequestBuilder<DecayWorker>().build()
            WorkManager.getInstance(context).enqueue(request)
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        }
    }
}
