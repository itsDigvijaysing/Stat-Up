package dev.statup.app

import android.app.Application
import android.util.Log
import dev.statup.app.data.local.datastore.UserPreferences
import dev.statup.app.data.local.db.StarterContentSeeder
import dev.statup.app.data.local.db.StatMappingSeeder
import dev.statup.app.data.local.db.dao.MissionDao
import dev.statup.app.data.local.db.dao.RewardDao
import dev.statup.app.data.local.db.dao.StatMappingDao
import dev.statup.app.data.repository.AchievementRepository
import dev.statup.app.data.repository.PlayerRepository
import dev.statup.app.di.appModule
import dev.statup.app.notifications.Notifier
import dev.statup.app.rpg.StatUpgradeRunner
import dev.statup.app.widget.StatsWidgetUpdater
import org.koin.android.ext.android.get
import dev.statup.app.sync.DecayWorker
import dev.statup.app.sync.TodoistSyncWorker
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.startKoin
import org.koin.core.logger.Level

class StatUpApp : Application() {
    // SupervisorJob keeps siblings alive on one child failure; the handler swallows the
    // exception so it doesn't propagate to the global Thread uncaught handler (which on
    // Android typically crashes the app at startup - bad for a one-off init failure).
    private val initExceptionHandler = CoroutineExceptionHandler { _, e ->
        Log.e("StatUpApp", "Init coroutine failed", e)
    }
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO + initExceptionHandler)

    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidLogger(Level.ERROR)
            androidContext(this@StatUpApp)
            modules(appModule)
        }

        initializeData()
        scheduleWorkers()
    }

    private fun initializeData() {
        val playerRepository: PlayerRepository by inject()
        val achievementRepository: AchievementRepository by inject()
        val statMappingDao: StatMappingDao by inject()
        val database: dev.statup.app.data.local.db.AppDatabase by inject()
        val userPreferences: UserPreferences by inject()
        // Eagerly resolve Notifier so its channels are created before any worker tries to notify.
        // (`by inject()` would defer construction until first access.)
        get<Notifier>()

        // The two the first-run gate depends on. Held as Jobs so the gate can join them: the
        // tutorial pays a mission and then waits on the `first_task` achievement, so opening the gate
        // before the stats row or the achievement rows exist would let a fast user start a tour whose
        // arithmetic cannot complete. Unrelated init stays fire-and-forget below.
        val achievementsReady = appScope.launch { achievementRepository.initializeAchievements() }
        val statsReady = appScope.launch {
            playerRepository.initializeStats()
            get<StatUpgradeRunner>().runIfNeeded(isFreshInstall = isFreshInstall())
        }

        appScope.launch { StatMappingSeeder.seedIfEmpty(database, statMappingDao) }
        appScope.launch { userPreferences.loadSecretsIfNeeded() }

        // First-run resolution, then seeding, then the gate - strictly in that order, in ONE
        // coroutine. The tutorial flag and the starter-content flag both default to false, which is
        // indistinguishable from "an install that predates them", so they have to be decided before
        // the UI reads them. Previously two separate coroutines wrote them while AppNavigation was
        // already collecting, and whoever won decided whether an updating user got a tour and 13
        // sample items they never asked for. The gate is marked last so the tabs are never gate-open
        // and empty at the same time.
        appScope.launch {
            try {
                // Everything the first screen can touch must exist before the gate opens.
                statsReady.join()
                achievementsReady.join()
                userPreferences.resolveFirstRunFlags(isFreshInstall())
                StarterContentSeeder.seedIfNeeded(database, get<MissionDao>(), get<RewardDao>(), userPreferences)
            } finally {
                // The gate opens even if something above failed. The UI blocks on it, so leaving it
                // shut would strand the user on a blank frame, and a failed seed is self-healing -
                // its flag is only written on success, so the next launch simply retries.
                userPreferences.markFirstRunResolved()
            }
        }

        // Refresh any installed home-screen widget with the current DB state. The system also
        // calls onUpdate on install/boot; this covers app-open after background data changes.
        appScope.launch { get<StatsWidgetUpdater>().refresh() }
    }

    private fun isFreshInstall(): Boolean = runCatching {
        val info = packageManager.getPackageInfo(packageName, 0)
        info.firstInstallTime == info.lastUpdateTime
    }.getOrDefault(true)

    private fun scheduleWorkers() {
        DecayWorker.schedule(this)
        TodoistSyncWorker.schedule(this, intervalMinutes = 15)
    }
}
