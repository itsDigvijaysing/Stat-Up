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
    // exception so it can't crash app startup via the global uncaught-exception handler.
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

        // Held as Jobs so the gate can join them: opening the gate before the stats/achievement
        // rows exist would let a fast user start a tutorial whose arithmetic can't complete.
        val achievementsReady = appScope.launch { achievementRepository.initializeAchievements() }
        val statsReady = appScope.launch {
            playerRepository.initializeStats()
            get<StatUpgradeRunner>().runIfNeeded(isFreshInstall = isFreshInstall())
        }

        appScope.launch { StatMappingSeeder.seedIfEmpty(database, statMappingDao) }
        appScope.launch { userPreferences.loadSecretsIfNeeded() }

        // Strictly ordered in ONE coroutine: two separate coroutines previously raced to write
        // these flags, so whoever won decided if an updating user got a tour + sample items.
        appScope.launch {
            try {
                // Everything the first screen can touch must exist before the gate opens.
                statsReady.join()
                achievementsReady.join()
                userPreferences.resolveFirstRunFlags(isFreshInstall())
                StarterContentSeeder.seedIfNeeded(database, get<MissionDao>(), get<RewardDao>(), userPreferences)
            } finally {
                // Opens even on failure - the UI blocks on this gate, and a failed seed is
                // self-healing (flag only written on success, so next launch retries).
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
