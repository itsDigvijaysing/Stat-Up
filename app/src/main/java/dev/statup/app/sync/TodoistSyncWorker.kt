package dev.statup.app.sync

import android.content.Context
import android.util.Log
import androidx.work.*
import dev.statup.app.notifications.Notifier
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.io.IOException
import java.util.concurrent.TimeUnit

class TodoistSyncWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params), KoinComponent {

    private val syncManager: TodoistSyncManager by inject()
    private val notifier: Notifier by inject()

    override suspend fun doWork(): Result {
        return try {
            when (val result = syncManager.syncCompletedTasks()) {
                is SyncResult.Success -> {
                    notifier.showSyncResult(result.tasksProcessed, result.pointsEarned)
                    Result.success()
                }
                is SyncResult.NotConnected -> Result.success()
                is SyncResult.AuthFailed -> {
                    // Token is invalid/expired - retrying won't help until user re-enters it.
                    Log.w(TAG, "Auth failed during sync: ${result.message}")
                    notifier.showSyncAuthFailure()
                    Result.success()
                }
                is SyncResult.Error -> {
                    Log.w(TAG, "Sync error (will retry): ${result.message}")
                    Result.retry()
                }
            }
        } catch (e: IOException) {
            Log.w(TAG, "Network error during sync", e)
            Result.retry()
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error during sync", e)
            Result.failure()
        }
    }

    companion object {
        private const val TAG = "TodoistSyncWorker"
        private const val WORK_NAME = "todoist_sync"

        fun schedule(context: Context, intervalMinutes: Int = 15) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val request = PeriodicWorkRequestBuilder<TodoistSyncWorker>(
                intervalMinutes.toLong(), TimeUnit.MINUTES
            )
                .setConstraints(constraints)
                .setBackoffCriteria(
                    BackoffPolicy.EXPONENTIAL,
                    1, TimeUnit.MINUTES
                )
                .build()

            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(
                    WORK_NAME,
                    ExistingPeriodicWorkPolicy.KEEP,
                    request
                )
        }

        fun runOnce(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val request = OneTimeWorkRequestBuilder<TodoistSyncWorker>()
                .setConstraints(constraints)
                .build()

            WorkManager.getInstance(context).enqueue(request)
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        }
    }
}
