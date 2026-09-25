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

        appScope.launch { achievementRepository.initializeAchievements() }
        appScope.launch { StatMappingSeeder.seedIfEmpty(database, statMappingDao) }
        appScope.launch { userPreferences.loadSecretsIfNeeded() }

        // Starter missions + rewards, once per install, so Tasks and Rewards never open empty.
        appScope.launch {
            StarterContentSeeder.seedIfNeeded(database, get<MissionDao>(), get<RewardDao>(), userPreferences)
        }

        // One-time upgrade for pre-rebalance installs: categorise uncategorised history, then
        // rebuild every stat on the new curve. Must run AFTER initializeStats() has guaranteed
        // the singleton row exists, so it is chained onto that launch rather than racing it.
        appScope.launch {
            playerRepository.initializeStats()
            get<StatUpgradeRunner>().runIfNeeded()
        }

        // Refresh any installed home-screen widget with the current DB state. The system also
        // calls onUpdate on install/boot; this covers app-open after background data changes.
        appScope.launch { get<StatsWidgetUpdater>().refresh() }
    }

    private fun scheduleWorkers() {
        DecayWorker.schedule(this)
        TodoistSyncWorker.schedule(this, intervalMinutes = 15)
    }
}
