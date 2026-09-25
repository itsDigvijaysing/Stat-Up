package dev.statup.app.di

import dev.statup.app.data.local.datastore.UserPreferences
import dev.statup.app.data.local.db.AppDatabase
import dev.statup.app.data.local.db.RoomTransactor
import dev.statup.app.data.repository.AchievementRepository
import dev.statup.app.data.repository.MissionRepository
import dev.statup.app.data.repository.PlayerRepository
import dev.statup.app.data.repository.PointsRepository
import dev.statup.app.data.repository.RewardRepository
import dev.statup.app.ai.AgentApi
import dev.statup.app.ai.AgentContextBuilder
import dev.statup.app.ai.AgentRepository
import dev.statup.app.ai.GeminiAgentApi
import dev.statup.app.notifications.Notifier
import dev.statup.app.quotes.AnimechanApi
import dev.statup.app.quotes.OfflineQuotePack
import dev.statup.app.quotes.QuotePack
import dev.statup.app.quotes.QuoteRepository
import dev.statup.app.quotes.ZenQuotesApi
import dev.statup.app.ai.classifier.CategoryBackfill
import dev.statup.app.ai.classifier.HashedLinearTaskClassifier
import dev.statup.app.ai.classifier.TaskClassifier
import dev.statup.app.rpg.AchievementTracker
import dev.statup.app.rpg.DecayEngine
import dev.statup.app.rpg.StatRecomputer
import dev.statup.app.rpg.StatUpgradeRunner
import dev.statup.app.rpg.StatsEngine
import dev.statup.app.rpg.Transactor
import dev.statup.app.sync.TodoistApi
import dev.statup.app.sync.TodoistSyncManager
import dev.statup.app.ui.screen.agent.AgentViewModel
import dev.statup.app.widget.StatsWidgetUpdater
import dev.statup.app.ui.screen.achievements.AchievementsViewModel
import dev.statup.app.ui.screen.history.HistoryViewModel
import dev.statup.app.ui.screen.onboarding.OnboardingViewModel
import dev.statup.app.ui.screen.rewards.RewardsViewModel
import dev.statup.app.ui.screen.settings.SettingsViewModel
import dev.statup.app.ui.screen.stats.StatsViewModel
import dev.statup.app.ui.screen.status.StatusViewModel
import dev.statup.app.ui.screen.tasks.TasksViewModel
import io.ktor.client.*
import io.ktor.client.engine.android.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json
import org.koin.android.ext.koin.androidContext
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.dsl.module

private const val CLASSIFIER_ASSET = "classifier/stat_clf_v1.bin"

val appModule = module {
    // Database
    single { AppDatabase.getInstance(androidContext()) }
    single { get<AppDatabase>().rewardDao() }
    single { get<AppDatabase>().transactionDao() }
    single { get<AppDatabase>().missionDao() }
    single { get<AppDatabase>().playerStatsDao() }
    single { get<AppDatabase>().statMappingDao() }
    single { get<AppDatabase>().decayLogDao() }
    single { get<AppDatabase>().titleDao() }
    single { get<AppDatabase>().aiMemoryDao() }

    // Wraps Room's withTransaction so engines (e.g. DecayEngine) can run a read-modify-write
    // atomically without depending on the concrete AppDatabase - keeps them JVM-unit-testable.
    single<Transactor> { RoomTransactor(get()) }

    // DataStore
    single { UserPreferences(androidContext()) }

    // Notifications
    single { Notifier(androidContext()) }

    // Home-screen widget refresher - pushes APPWIDGET_UPDATE broadcasts when data changes.
    single { StatsWidgetUpdater(androidContext()) }

    // HTTP Client
    single {
        HttpClient(Android) {
            install(ContentNegotiation) {
                json(Json {
                    ignoreUnknownKeys = true
                    isLenient = true
                })
            }
            // MUST be installed before HttpTimeout (Ktor requirement) or timeouts aren't retried.
            // Gemini's free tier returns 503 UNAVAILABLE often enough that a single attempt
            // surfaced a dead-end error for a condition one retry clears. 429 is NOT a server
            // error, so genuine quota exhaustion is still not retried.
            install(io.ktor.client.plugins.HttpRequestRetry) {
                retryOnServerErrors(maxRetries = 2)
                exponentialDelay()
            }
            install(io.ktor.client.plugins.HttpTimeout) {
                requestTimeoutMillis = 15000
                connectTimeoutMillis = 10000
                socketTimeoutMillis = 15000
            }
            expectSuccess = false
        }
    }

    // Todoist
    single { TodoistApi(get()) }
    single { TodoistSyncManager(get(), get(), get(), get(), get()) }

    // Daily Quote - UserPreferences implements the DailyQuoteStore slice.
    single<QuotePack> { OfflineQuotePack(androidContext()) }
    single {
        QuoteRepository(
            store = get<UserPreferences>(),
            animeApi = AnimechanApi(get()),
            motivationApi = ZenQuotesApi(get()),
            offlinePack = get()
        )
    }

    // AI Agent (Gemini)
    // GeminiAgentApi resolves the API key on-demand via a suspending lambda so it always
    // reads the latest value from encrypted storage - no need to recreate the singleton when
    // the user updates their key in Settings.
    single<AgentApi> {
        val userPreferences = get<dev.statup.app.data.local.datastore.UserPreferences>()
        GeminiAgentApi(
            httpClient = get(),
            apiKeyProvider = { userPreferences.getGeminiApiKey() }
        )
    }
    single { AgentContextBuilder(get<PlayerRepository>(), get(), get()) }
    single { AgentRepository(get(), get()) }

    // Repositories
    single { PlayerRepository(get(), get()) }
    single { MissionRepository(get(), get()) }
    single { PointsRepository(get(), get(), get(), get(), get(), get()) }
    // AchievementRepository takes an optional points-award lambda. We resolve PointsRepository
    // lazily through the Koin container so we don't introduce a circular dependency
    // (AchievementTracker → AchievementRepository → PointsRepository → AchievementTracker).
    single {
        AchievementRepository(
            database = get(),
            titleDao = get(),
            unlockNotifier = get(),
            pointsAwarder = { achievementId, points ->
                get<PointsRepository>().addPoints(
                    points = points,
                    type = dev.statup.app.domain.model.TransactionType.EARN,
                    source = dev.statup.app.domain.model.TransactionSource.MANUAL,
                    description = "${dev.statup.app.data.repository.ACHIEVEMENT_REWARD_PREFIX}$achievementId"
                )
            }
        )
    }
    single { RewardRepository(get(), get(), get()) }

    // RPG Engines
    single { StatsEngine() }
    single {
        DecayEngine(
            statsStore = get<PlayerRepository>(),
            decayLogDao = get(),
            dayStore = get<UserPreferences>(),
            transactor = get(),
            transactionDao = get(),
            achievementTracker = get(),
            widgetUpdater = get()
        )
    }
    single { dev.statup.app.rpg.AchievementUnlockNotifier() }
    single { AchievementTracker(get(), get(), get()) }

    // Offline task -> stat classifier. The 96 KB blob is read from assets on first use and
    // held for the process lifetime; passing a byte-array provider (not a Context) keeps the
    // scoring path JVM-testable.
    single<TaskClassifier> {
        val context = androidContext()
        HashedLinearTaskClassifier(
            modelBytes = { context.assets.open(CLASSIFIER_ASSET).use { it.readBytes() } }
        )
    }
    single {
        CategoryBackfill(
            earnStore = get<PointsRepository>(),
            statsStore = get<PlayerRepository>(),
            classifier = get(),
            transactor = get()
        )
    }
    single {
        StatRecomputer(
            statsStore = get<PlayerRepository>(),
            lifetimePoints = get<PointsRepository>(),
            transactor = get()
        )
    }
    single { StatUpgradeRunner(get<UserPreferences>(), get(), get()) }
    single { dev.statup.app.ui.screen.tutorial.TutorialCoordinator(get<UserPreferences>(), get()) }

    // ViewModels
    viewModel { StatusViewModel(get(), get(), get(), get(), get(), get(), get()) }
    viewModel { RewardsViewModel(get(), get(), get(), get()) }
    viewModel { SettingsViewModel(get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get()) }
    viewModel { TasksViewModel(get(), get(), get(), get(), get(), get(), get()) }
    viewModel {
        HistoryViewModel(
            transactions = get<PointsRepository>().transactions,
            playerStats = get<PlayerRepository>().playerStats
        )
    }
    viewModel { AchievementsViewModel(get()) }
    viewModel { StatsViewModel(get(), get()) }
    viewModel { AgentViewModel(get(), get()) }
    viewModel { OnboardingViewModel(get(), get()) }
}
