package dev.statup.app.ui.navigation

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.statup.app.data.local.datastore.UserPreferences
import dev.statup.app.ui.components.AmbientBackground
import dev.statup.app.ui.components.rpg.AchievementUnlockedDialog
import dev.statup.app.ui.components.LocalHapticsEnabled
import dev.statup.app.ui.components.glass.BottomNavItem
import dev.statup.app.ui.components.glass.GlassBottomBar
import dev.statup.app.ui.components.glass.LocalHazeState
import dev.statup.app.ui.components.glass.hazeSourceOrFallback
import dev.chrisbanes.haze.rememberHazeState
import dev.statup.app.ui.screen.achievements.AchievementsScreen
import dev.statup.app.ui.screen.agent.AgentScreen
import dev.statup.app.ui.screen.history.HistoryScreen
import dev.statup.app.ui.screen.legal.PrivacyPolicyScreen
import dev.statup.app.ui.screen.onboarding.OnboardingScreen
import dev.statup.app.ui.screen.help.HowItWorksScreen
import dev.statup.app.ui.screen.tutorial.TutorialCoordinator
import dev.statup.app.ui.screen.tutorial.TutorialIntroDialog
import dev.statup.app.ui.screen.tutorial.TutorialOverlay
import dev.statup.app.ui.screen.tutorial.TutorialStep
import dev.statup.app.ui.screen.rewards.RewardsScreen
import dev.statup.app.ui.screen.settings.SettingsScreen
import dev.statup.app.ui.screen.stats.StatsScreen
import dev.statup.app.ui.screen.status.StatusScreen
import dev.statup.app.ui.screen.tasks.TasksScreen
import dev.statup.app.domain.model.Achievement
import dev.statup.app.rpg.AchievementUnlockNotifier
import dev.statup.app.rpg.StatUpgradeRunner
import dev.statup.app.rpg.StatUpgradeState
import dev.statup.app.ui.theme.AccentPrimary
import androidx.compose.foundation.layout.navigationBarsPadding
import dev.statup.app.ui.theme.BackgroundBase
import dev.statup.app.ui.theme.GlassTokens
import dev.statup.app.ui.theme.Inter
import dev.statup.app.ui.theme.TextPrimary
import dev.statup.app.ui.theme.TextSecondary
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.koin.compose.koinInject

/** Routes that own a bottom-bar tab; everything else is a hidden detail screen. */
private val bottomNavRoutes = listOf(
    Routes.STATUS, Routes.TASKS, Routes.REWARDS, Routes.AGENT, Routes.SETTINGS
)

private val bottomNavItems = listOf(
    BottomNavItem("Status", Icons.Outlined.Shield, Routes.STATUS),
    BottomNavItem("Tasks", Icons.Outlined.TaskAlt, Routes.TASKS),
    BottomNavItem("Rewards", Icons.Outlined.CardGiftcard, Routes.REWARDS),
    BottomNavItem("Agent", Icons.Outlined.SmartToy, Routes.AGENT),
    BottomNavItem("Settings", Icons.Outlined.Settings, Routes.SETTINGS)
)

@Composable
fun AppNavigation(
    navController: NavHostController = rememberNavController()
) {
    // First-run gate: show onboarding until the flag is set. `null` = the flag is still loading,
    // so we render a neutral dark frame (never a flash of onboarding for already-onboarded users).
    val userPreferences = koinInject<UserPreferences>()
    val onboarded by produceState<Boolean?>(initialValue = null, userPreferences) {
        userPreferences.onboardingComplete.collect { value = it }
    }
    // Expose the Haptic Feedback preference app-wide so action handlers can tick on confirm.
    val hapticsEnabled by userPreferences.hapticFeedback.collectAsStateWithLifecycle(initialValue = true)

    // The guided tutorial is a second gate behind onboarding: it must be finished before the
    // main shell unlocks, so a new user always sees the earn → stat → spend loop work once.
    val tutorialDone by produceState<Boolean?>(initialValue = null, userPreferences) {
        userPreferences.tutorialComplete.collect { value = it }
    }

    // Both flags default to false, indistinguishable from "predates them" - wait for this
    // before acting or an updating user is mistaken for a new one.
    val firstRunResolved by produceState<Boolean?>(initialValue = null, userPreferences) {
        userPreferences.firstRunResolved.collect { value = it }
    }

    // One-time stat rebuild for installs that predate the progression change. Held in front of
    // everything so the user never watches their stats move under them mid-session.
    val upgradeRunner = koinInject<StatUpgradeRunner>()
    val upgradeState by upgradeRunner.state.collectAsStateWithLifecycle()

    // The guided first run drives the real tabs, so it starts once onboarding is done and
    // runs inside the shell rather than replacing it.
    val tutorialCoordinator = koinInject<TutorialCoordinator>()
    val tutorialStep by tutorialCoordinator.step.collectAsStateWithLifecycle()
    val tutorialScope = rememberCoroutineScope()

    LaunchedEffect(onboarded, tutorialDone, firstRunResolved) {
        if (firstRunResolved == true && onboarded == true && tutorialDone == false) {
            tutorialCoordinator.start()
        }
    }

    CompositionLocalProvider(LocalHapticsEnabled provides hapticsEnabled) {
        Box(modifier = Modifier.fillMaxSize()) {
            when {
                // `null` = the flag is still loading, so render a neutral dark frame rather than
                // flashing onboarding at an already-onboarded user.
                onboarded == null -> Box(modifier = Modifier.fillMaxSize().background(BackgroundBase))
                onboarded == false -> OnboardingScreen()
                tutorialDone == null || firstRunResolved != true ->
                    Box(modifier = Modifier.fillMaxSize().background(BackgroundBase))
                else -> MainShell(
                    navController,
                    tutorialStep,
                    onTutorialAcknowledge = {
                        tutorialScope.launch { tutorialCoordinator.onAchievementAcknowledged() }
                    },
                    onTutorialSkip = {
                        tutorialScope.launch { tutorialCoordinator.skip() }
                    }
                )
            }

            if (upgradeState.isRunning) {
                StatUpgradeOverlay(state = upgradeState)
            }

            if (tutorialStep == TutorialStep.INTRO) {
                TutorialIntroDialog(
                    onStart = { tutorialScope.launch { tutorialCoordinator.beginTour() } },
                    onSkip = { tutorialScope.launch { tutorialCoordinator.finish() } }
                )
            }
        }
    }
}

/**
 * Blocking progress screen for the one-time upgrade; dismissed once the runner reaches
 * [StatUpgradeState.Done].
 */
@Composable
private fun StatUpgradeOverlay(state: StatUpgradeState) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundBase.copy(alpha = 0.97f))
            // Swallows taps: a bare background Box doesn't consume pointer events, so without
            // this the app stayed operable while stats were mid-rewrite.
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = {}
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(32.dp)
        ) {
            CircularProgressIndicator(color = AccentPrimary)
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = "Updating your stats",
                color = TextPrimary,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = Inter
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = when (state) {
                    is StatUpgradeState.Categorising ->
                        "Sorting your past tasks into stats… ${state.done} / ${state.total}"
                    StatUpgradeState.Rebuilding -> "Rebuilding your stats from everything you've earned…"
                    else -> ""
                },
                color = TextSecondary,
                fontSize = 14.sp,
                fontFamily = Inter,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "Your points, history and rewards are untouched.",
                color = TextSecondary.copy(alpha = 0.7f),
                fontSize = 12.sp,
                fontFamily = Inter,
                textAlign = TextAlign.Center
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MainShell(
    navController: NavHostController,
    tutorialStep: TutorialStep? = null,
    onTutorialAcknowledge: () -> Unit = {},
    onTutorialSkip: () -> Unit = {}
) {
    // Ask for notification permission here (after onboarding), not over the intro.
    RequestNotificationPermissionOnce()



    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route ?: Routes.STATUS

    // Scaffold's bottomBar slot reserves a fixed height that never accounts for the IME,
    // fighting AgentScreen's chat input - hide the bar while the keyboard is visible instead.
    val imeVisible = WindowInsets.isImeVisible
    val showBottomBar = currentRoute in bottomNavRoutes && !imeVisible

    // Listener lives at the shell (not a screen) so background unlocks - e.g. a Todoist sync -
    // are still celebrated; must stay inside the LocalHazeState provider or the blur silently degrades to a dim.
    val unlockNotifier = koinInject<AchievementUnlockNotifier>()
    // A QUEUE, not a single slot: unlocks can land together (e.g. a sync crossing several
    // thresholds at once), and a single slot silently swallowed all but the last.
    val unlockQueue = remember { mutableStateListOf<Achievement>() }
    val pendingUnlock = unlockQueue.firstOrNull()
    LaunchedEffect(unlockNotifier) {
        unlockNotifier.events.collect { unlockQueue += it }
    }


    // Single HazeState shared across the whole shell - content is the "source", glass
    // primitives (cards, bottom bar) are "effects" that sample the source at blur time.
    val hazeState = rememberHazeState()

    // How much bottom space the tutorial coach-mark occupies, so the celebration popup can stay
    // clear of it. Measured from the strip itself rather than hard-coded.
    val density = LocalDensity.current
    var coachMarkHeight by remember { mutableStateOf(0.dp) }

    CompositionLocalProvider(LocalHazeState provides hazeState) {
    Box(modifier = Modifier.fillMaxSize()) {
        AmbientBackground()

        Scaffold(
            containerColor = androidx.compose.ui.graphics.Color.Transparent,
            bottomBar = {
                if (showBottomBar) {
                    GlassBottomBar(
                        items = bottomNavItems,
                        selectedRoute = currentRoute,
                        // Achievements has no tab of its own, so point at Status - the screen
                        // it is reached from.
                        highlightRoute = tutorialStep?.targetRoute?.takeIf { it != currentRoute }
                            ?.let { if (it == Routes.ACHIEVEMENTS) Routes.STATUS else it },
                        onItemClick = { route ->
                            if (route != currentRoute) {
                                navController.navigate(route) {
                                    popUpTo(Routes.STATUS) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        }
                    )
                }
            }
        ) { paddingValues ->
            // Box so the tutorial coach-mark can sit over the NavHost rather than beside it.
            Box(modifier = Modifier.fillMaxSize()) {
            NavHost(
                navController = navController,
                startDestination = Routes.STATUS,
                modifier = Modifier
                    .padding(paddingValues)
                    // Without this, descendants reading WindowInsets directly (e.g. AgentScreen's
                    // imePadding()) don't know paddingValues already reserved the bar's height, stacking a duplicate gap.
                    .consumeWindowInsets(paddingValues)
                    .hazeSourceOrFallback()
                    // Blur everything behind a modal celebration. Applied to the content, not
                    // the overlay, so it is identical to the redeem sheet's treatment.
                    .blur(if (pendingUnlock != null) GlassTokens.ModalBackdropBlur else 0.dp),
                // Subtle fade-through on route changes for a more fluid feel.
                enterTransition = { fadeIn(tween(220)) + slideInHorizontally(tween(220)) { it / 16 } },
                exitTransition = { fadeOut(tween(160)) },
                popEnterTransition = { fadeIn(tween(220)) },
                popExitTransition = { fadeOut(tween(160)) }
            ) {
                composable(Routes.STATUS) {
                    StatusScreen(navController = navController)
                }
                composable(Routes.TASKS) {
                    TasksScreen(navController = navController)
                }
                composable(Routes.REWARDS) {
                    RewardsScreen(navController = navController)
                }
                composable(Routes.AGENT) {
                    AgentScreen(navController = navController)
                }
                composable(Routes.SETTINGS) {
                    SettingsScreen(navController = navController)
                }
                composable(Routes.HISTORY) {
                    HistoryScreen(navController = navController)
                }
                composable(Routes.FULL_STATS) {
                    StatsScreen(navController = navController)
                }
                composable(Routes.ACHIEVEMENTS) {
                    AchievementsScreen(navController = navController)
                }
                composable(Routes.PRIVACY_POLICY) {
                    PrivacyPolicyScreen(navController = navController)
                }
                composable(Routes.HOW_IT_WORKS) {
                    HowItWorksScreen(navController = navController)
                }
            }

            }  // Box
        }

        pendingUnlock?.let { unlocked ->
            AchievementUnlockedDialog(
                achievement = unlocked,
                onDismiss = {
                    if (unlockQueue.isNotEmpty()) unlockQueue.removeAt(0)
                    // Dismissing the celebration IS the tutorial's achievement step (a no-op otherwise);
                    // held until the queue drains so a pile-up of unlocks can't skip ahead of ones still waiting.
                    if (unlockQueue.isEmpty()) onTutorialAcknowledge()
                },
                // Measured, not guessed: the strip's height depends on how the step's copy wraps, so
                // a constant would silently stop clearing it once the wording changes.
                bottomReserved = coachMarkHeight
            )
        }

        // Drawn LAST so it stays sharp above the blur, and outside the Scaffold - inside its
        // content slot the bottom bar (a sibling drawn afterwards) painted straight over it.
        LaunchedEffect(tutorialStep, pendingUnlock) {
            if (tutorialStep == TutorialStep.SEE_ACHIEVEMENT && pendingUnlock == null) {
                // Give the unlock a moment to arrive before giving up on the step. No recheck needed
                // after the delay: an arriving unlock changes this effect's key and cancels/relaunches it first.
                kotlinx.coroutines.delay(1500)
                onTutorialAcknowledge()
            }
        }

        // Used to sit on top of the unlock popup's dismiss button (unclickable "Nice!" stranded
        // the tour there) - now the popup reserves room for this strip instead of covering it.
        tutorialStep?.takeIf { it != TutorialStep.INTRO }?.let { step ->
            TutorialOverlay(
                step = step,
                isOnTargetTab = currentRoute == step.targetRoute,
                currentRoute = currentRoute,
                hasBottomBar = showBottomBar,
                onSkip = onTutorialSkip,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    // Tight to the bar: the block sits below the centred popup card, and
                    // every dp of clearance above it is one less chance of overlap.
                    .padding(bottom = if (showBottomBar) GlassTokens.BottomBarHeight else 8.dp)
                    .onGloballyPositioned {
                        coachMarkHeight = with(density) { it.size.height.toDp() } +
                            if (showBottomBar) GlassTokens.BottomBarHeight else 8.dp
                    }
            )
        }

    }
    }  // CompositionLocalProvider
}

/**
 * Asks for POST_NOTIFICATIONS once on Android 13+; decline is fine since Notifier re-checks
 * permission before every notify call, silently disabling notifications on refusal.
 */
@Composable
private fun RequestNotificationPermissionOnce() {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { /* result ignored - Notifier re-checks before each notify call */ }
    LaunchedEffect(Unit) {
        launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
}
