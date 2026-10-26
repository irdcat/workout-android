package com.github.irdcat.workout.application.ui

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.github.irdcat.workout.application.ui.components.KeepScreenOn
import com.github.irdcat.workout.application.ui.components.LocalMainTabs
import com.github.irdcat.workout.application.ui.components.MainTab
import com.github.irdcat.workout.application.ui.screens.AnalysisRoute
import com.github.irdcat.workout.application.ui.screens.BrowseRoute
import com.github.irdcat.workout.application.ui.screens.HomeRoute
import com.github.irdcat.workout.application.ui.screens.HomeSkeleton
import com.github.irdcat.workout.application.ui.screens.LoginRoute
import com.github.irdcat.workout.application.ui.screens.RestTimerRoute
import com.github.irdcat.workout.application.ui.screens.SessionRoute
import com.github.irdcat.workout.application.ui.screens.SettingsRoute
import com.github.irdcat.workout.application.ui.screens.SetupRoute
import com.github.irdcat.workout.application.ui.screens.WarmUpRoute
import com.github.irdcat.workout.application.ui.screens.WorkoutDetailRoute
import com.github.irdcat.workout.application.ui.screens.WorkoutSummaryRoute
import kotlinx.coroutines.launch

@Composable
fun AppNavHost(
    navController: NavHostController = rememberNavController(),
    appViewModel: AppViewModel = hiltViewModel(),
) {
    val startDestination by appViewModel.startDestination.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()

    // Wait for the saved account and spreadsheet before choosing where to start. Signed in, that's
    // nearly always the week, so its skeleton shows meanwhile.
    val start = startDestination ?: return HomeSkeleton()

    val keepScreenOn by appViewModel.keepScreenOnWhileTraining.collectAsStateWithLifecycle()
    val currentEntry by navController.currentBackStackEntryAsState()
    val isTraining = currentEntry?.destination?.let {
        it.hasRoute(Route.Session::class) || it.hasRoute(Route.WarmUp::class) || it.hasRoute(Route.RestTimer::class)
    } == true
    KeepScreenOn(enabled = keepScreenOn && isTraining)

    val mainTabs by appViewModel.mainTabs.collectAsStateWithLifecycle()
    CompositionLocalProvider(LocalMainTabs provides mainTabs) {
        NavHost(
            navController = navController,
            startDestination = start,
            enterTransition = { if (isTabSwitch()) EnterTransition.None else fadeIn(tween(180)) },
            exitTransition = { if (isTabSwitch()) ExitTransition.None else fadeOut(tween(180)) },
            popEnterTransition = { if (isTabSwitch()) EnterTransition.None else fadeIn(tween(180)) },
            popExitTransition = { if (isTabSwitch()) ExitTransition.None else fadeOut(tween(180)) },
        ) {
            composable<Route.Login> {
                LoginRoute(
                    onSignedIn = {
                        scope.launch {
                            navController.navigate(appViewModel.destinationAfterSignIn()) {
                                // Back from the next screen leaves the app instead of returning to sign-in.
                                popUpTo<Route.Login> { inclusive = true }
                            }
                        }
                    },
                )
            }

            composable<Route.Setup> { entry ->
                val editing = entry.toRoute<Route.Setup>().editing
                SetupRoute(
                    onSaved = {
                        if (editing) {
                            navController.popBackStack()
                        } else {
                            navController.navigate(Route.Home) { popUpTo<Route.Setup> { inclusive = true } }
                        }
                    },
                    onSignedOut = navController::navigateToLogin,
                )
            }

            // ---- Tabs ----------------------------------------------------------------

            composable<Route.Home> {
                HomeRoute(
                    onOpenSession = { navController.navigate(Route.Session) },
                    onOpenRest = {
                        navController.navigate(Route.Session)
                        navController.navigate(Route.RestTimer)
                    },
                    onOpenWorkout = { id -> navController.navigate(Route.WorkoutDetail(id)) },
                    // Browse opens on the latest month when this week has no plan.
                    onOpenLatestWeek = { navController.navigateToTab(MainTab.Browse) },
                    onTabSelected = navController::navigateToTab,
                )
            }

            composable<Route.Browse> {
                BrowseRoute(
                    onOpenWorkout = { id -> navController.navigate(Route.WorkoutDetail(id)) },
                    onTabSelected = navController::navigateToTab,
                )
            }

            composable<Route.Analysis> {
                AnalysisRoute(onTabSelected = navController::navigateToTab)
            }

            composable<Route.Settings> {
                SettingsRoute(
                    onEditSpreadsheet = { navController.navigate(Route.Setup(editing = true)) },
                    onSignedOut = navController::navigateToLogin,
                    onTabSelected = navController::navigateToTab,
                )
            }

            // ---- Workout -------------------------------------------------------------

            composable<Route.WorkoutDetail> {
                WorkoutDetailRoute(
                    onBack = { navController.popBackStack() },
                    onOpenSession = { navController.navigate(Route.Session) { launchSingleTop = true } },
                )
            }

            composable<Route.Session> {
                SessionRoute(
                    onBack = { navController.popBackStack() },
                    onFinishWorkout = { navController.navigate(Route.Summary) },
                    onRestStarted = { navController.navigate(Route.RestTimer) },
                    onOpenWarmUp = { navController.navigate(Route.WarmUp) { launchSingleTop = true } },
                    onSessionEnded = navController::navigateHomeClearingWorkout,
                )
            }

            composable<Route.WarmUp> {
                WarmUpRoute(
                    onBack = { navController.popBackStack() },
                    onFinishWorkout = { navController.navigate(Route.Summary) },
                    onRestStarted = { navController.navigate(Route.RestTimer) },
                    onSessionEnded = navController::navigateHomeClearingWorkout,
                )
            }

            composable<Route.RestTimer> {
                RestTimerRoute(
                    // Back to whichever screen logged the set: working sets or warm-ups.
                    onDone = { navController.popBackStack() },
                    onStartWorkingSets = { navController.popBackStack<Route.Session>(inclusive = false) },
                    // Leaves the rest running; Home's session card comes back to it.
                    onHome = navController::navigateHomeClearingWorkout,
                )
            }

            composable<Route.Summary> {
                WorkoutSummaryRoute(
                    onSaved = navController::navigateHomeClearingWorkout,
                    onBackToSession = { navController.popBackStack() },
                )
            }
        }
    }
}

/**
 * Switches tabs the usual way: one copy of each tab, each keeping its state, and
 * Back from Browse or Settings returning to the week.
 */
private fun NavHostController.navigateToTab(tab: MainTab) {
    navigate(tab.route) {
        popUpTo<Route.Home> { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

/** Drops the session's screens (detail, session, warm-up, rest, summary) and lands on the week. */
private fun NavHostController.navigateHomeClearingWorkout() {
    navigate(Route.Home) {
        popUpTo<Route.Home>()
        launchSingleTop = true
    }
}

/** After signing out nothing behind the sign-in screen should stay reachable. */
private fun NavHostController.navigateToLogin() {
    navigate(Route.Login) {
        popUpTo(graph.id) { inclusive = true }
    }
}

private val NavDestination.isTab: Boolean
    get() = hasRoute(Route.Home::class) || hasRoute(Route.Browse::class) || hasRoute(Route.Analysis::class) ||
        hasRoute(Route.Settings::class)

/** Tabs share the bottom bar, so switching between them shouldn't fade it. */
private fun AnimatedContentTransitionScope<NavBackStackEntry>.isTabSwitch(): Boolean =
    initialState.destination.isTab && targetState.destination.isTab
