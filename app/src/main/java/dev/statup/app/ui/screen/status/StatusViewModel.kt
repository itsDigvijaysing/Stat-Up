package dev.statup.app.ui.screen.status

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.statup.app.data.local.datastore.UserPreferences
import dev.statup.app.data.local.db.dao.TitleDao
import dev.statup.app.data.local.db.dao.TransactionDao
import dev.statup.app.data.local.db.entity.TitleEntity
import dev.statup.app.data.repository.PlayerRepository
import dev.statup.app.data.repository.PointsRepository
import dev.statup.app.domain.model.PlayerStats
import dev.statup.app.domain.model.Quote
import dev.statup.app.domain.model.Rank
import dev.statup.app.domain.model.StatType
import dev.statup.app.domain.model.TransactionSource
import dev.statup.app.domain.model.TransactionType
import dev.statup.app.quotes.QuoteRepository
import dev.statup.app.rpg.AchievementTracker
import dev.statup.app.rpg.StatsEngine
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId

// flatMapLatest is still marked experimental in kotlinx-coroutines. It is used deliberately here
// to re-issue the day-scoped queries when the local day rolls over - opting in explicitly rather
// than building on an unacknowledged warning.
@OptIn(ExperimentalCoroutinesApi::class)
class StatusViewModel(
    private val playerRepository: PlayerRepository,
    private val pointsRepository: PointsRepository,
    private val achievementTracker: AchievementTracker,
    private val userPreferences: UserPreferences,
    private val transactionDao: TransactionDao,
    private val quoteRepository: QuoteRepository,
    private val titleDao: TitleDao
) : ViewModel() {

    private val _uiState = MutableStateFlow(StatusUiState())
    val uiState: StateFlow<StatusUiState> = _uiState.asStateFlow()

    // One-time rank-up events survive a no-collector gap (Status tab off-composition) via a
    // buffered Channel and are delivered exactly once - see RankUpNotifier.
    private val rankUpNotifier = RankUpNotifier()
    val rankUpEvent: Flow<Rank> = rankUpNotifier.events

    private var previousRank: Rank? = null

    /**
     * Emits the current local day's [start, endExclusive) epoch-ms bounds, re-emitting once the
     * day rolls over. Probes every 60s; `distinctUntilChanged` filters out the per-minute
     * heartbeat so downstream `flatMapLatest` only re-issues on actual day boundaries.
     * Shared (`shareIn`) so the mood flag and today's-points collectors ride a single
     * ticker instead of each running their own.
     */
    private val dayRangeFlow: Flow<Pair<Long, Long>> = flow {
        while (true) {
            emit(todayMillisRange())
            delay(60_000L)
        }
    }
        .distinctUntilChanged()
        .shareIn(viewModelScope, SharingStarted.WhileSubscribed(), replay = 1)

    init {
        viewModelScope.launch {
            combine(
                playerRepository.username,
                playerRepository.playerStats,
                userPreferences.hexagonStyle
            ) { username, stats, hexStyle ->
                // Check for rank up
                stats?.let {
                    previousRank?.let { prev ->
                        if (it.rank.order > prev.order) {
                            rankUpNotifier.notify(it.rank)
                        }
                    }
                    previousRank = it.rank
                }

                _uiState.update { currentState ->
                    currentState.copy(
                        username = username,
                        stats = stats ?: PlayerStats(),
                        hexagonStyle = hexStyle,
                        isLoading = false
                    )
                }
            }.collect()
        }

        viewModelScope.launch {
            loadTodayPoints()
        }

        viewModelScope.launch {
            loadCurrentBalance()
        }

        viewModelScope.launch {
            observeMoodCheckedInToday()
        }

        viewModelScope.launch {
            loadDailyQuote()
        }

        viewModelScope.launch {
            observeEquippedTitle()
        }
    }

    /**
     * Resolve the equipped title against the live unlocked list, so un-equipping,
     * achievement deletion, or a full reset all degrade gracefully (title disappears
     * rather than pointing at a stale id). Also feeds the picker dialog's options.
     */
    private suspend fun observeEquippedTitle() {
        combine(
            userPreferences.equippedTitleId,
            titleDao.getUnlocked()
        ) { equippedId, unlocked ->
            val equipped = unlocked.firstOrNull { it.id == equippedId }
            equipped to unlocked
        }.collect { (equipped, unlocked) ->
            _uiState.update {
                it.copy(
                    equippedTitle = equipped?.let { t ->
                        listOfNotNull(t.emoji, t.name).joinToString(" ")
                    },
                    unlockedTitles = unlocked
                )
            }
        }
    }

    fun equipTitle(titleId: String?) {
        viewModelScope.launch {
            userPreferences.setEquippedTitleId(titleId)
        }
    }

    /** Buy one Streak Freeze Shield. Outcome (success or friendly error) lands in
     *  [StatusUiState.shieldMessage] for the shield dialog to display. */
    fun buyShield() {
        viewModelScope.launch {
            pointsRepository.buyStreakShield().fold(
                onSuccess = { count ->
                    _uiState.update { it.copy(shieldMessage = "Shield acquired! You now hold $count.") }
                },
                onFailure = { e ->
                    _uiState.update { it.copy(shieldMessage = e.message ?: "Couldn't buy a shield.") }
                }
            )
        }
    }

    fun dismissShieldMessage() {
        _uiState.update { it.copy(shieldMessage = null) }
    }

    /**
     * Resolve the day's quote. Re-resolves when the local day rolls over (dayStartFlow)
     * or the user changes the source in Settings (quoteSource). Cache hits inside
     * QuoteRepository make repeat emissions free; failures resolve to the offline pack
     * inside the repository, so this never errors - worst case the card stays hidden.
     */
    private suspend fun loadDailyQuote() {
        combine(dayRangeFlow, userPreferences.quoteSource) { _, _ -> }
            .collect {
                val quote: Quote? = runCatching { quoteRepository.getDailyQuote() }.getOrNull()
                _uiState.update { it.copy(dailyQuote = quote) }
            }
    }

    private suspend fun observeMoodCheckedInToday() {
        // flatMapLatest off the day ticker so the underlying query is re-issued with the
        // new range whenever the local day rolls over (catches users who leave the app
        // open across midnight). Old subscription is cancelled by flatMapLatest semantics.
        dayRangeFlow
            .flatMapLatest { (start, end) ->
                transactionDao.countBySourceInRange(TransactionSource.MOOD.name, start, end)
            }
            .collect { count ->
                _uiState.update { it.copy(hasCheckedInMoodToday = count > 0) }
            }
    }

    /** Local-day bounds derived from LocalDate, so DST days (23h/25h) stay correct. */
    private fun todayMillisRange(): Pair<Long, Long> {
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        return today.atStartOfDay(zone).toInstant().toEpochMilli() to
            today.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
    }

    private suspend fun loadTodayPoints() {
        // Same midnight-aware pattern as mood: re-issue the SUM query when the day flips.
        dayRangeFlow
            .flatMapLatest { (start, end) ->
                transactionDao.observeEarnedInRange(start, end)
            }
            .collect { todayPoints ->
                _uiState.update { it.copy(todayPoints = todayPoints) }
            }
    }

    private suspend fun loadCurrentBalance() {
        pointsRepository.balanceFlow.collect { balance ->
            _uiState.update { it.copy(currentBalance = balance) }
        }
    }

    fun checkInMood(mood: String) {
        // Guard: only one mood check-in per local day.
        if (_uiState.value.hasCheckedInMoodToday) return
        viewModelScope.launch {
            // Re-check inside the coroutine to close a race against a concurrent first-tap.
            val (start, end) = todayMillisRange()
            val alreadyToday = transactionDao.countBySourceInRange(TransactionSource.MOOD.name, start, end).first() > 0
            if (alreadyToday) return@launch

            pointsRepository.addPoints(
                points = StatsEngine.MOOD_POINTS,
                type = TransactionType.EARN,
                source = TransactionSource.MOOD,
                description = "Mood check-in: $mood",
                statType = StatType.WIS,
                relatedId = null
            )
            // Achievement checks are best-effort: a failure must not crash the check-in flow
            // (points were already awarded atomically above).
            runCatching {
                achievementTracker.onMoodCheckedIn()
                achievementTracker.onPointsEarned(TransactionSource.MOOD)
            }
        }
    }

    fun addManualPoints(points: Int, statType: StatType, description: String) {
        viewModelScope.launch {
            pointsRepository.addPoints(
                points = points,
                type = TransactionType.EARN,
                source = TransactionSource.MANUAL,
                description = description,
                statType = statType,
                relatedId = null
            )
            // Best-effort: don't let an achievement-check failure crash the earn flow.
            runCatching { achievementTracker.onPointsEarned(TransactionSource.MANUAL) }
        }
    }
}

data class StatusUiState(
    val username: String = "Player",
    val stats: PlayerStats = PlayerStats(),
    val todayPoints: Int = 0,
    val currentBalance: Int = 0,
    val hexagonStyle: String = "simple",
    val hasCheckedInMoodToday: Boolean = false,
    val dailyQuote: Quote? = null,
    val equippedTitle: String? = null,
    val unlockedTitles: List<TitleEntity> = emptyList(),
    val shieldMessage: String? = null,
    val isLoading: Boolean = true,
    val error: String? = null
)
