package io.blueeye.navigation

import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import io.blueeye.feature.details.DetailsScreen
import io.blueeye.feature.radar.presentation.RadarScreen
import io.blueeye.feature.settings.SettingsScreen
import io.blueeye.feature.watchlist.WatchlistScreen
import io.blueeye.ui.AppDrawer
import kotlinx.coroutines.launch

@Suppress("FunctionNaming", "LongMethod")
@Composable
fun AppNavigation(navController: NavHostController = rememberNavController()) {
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination

    // Helper to determine current screen for drawer selection
    val currentScreen: Screen =
        when (currentRoute?.route) {
            "io.blueeye.navigation.Screen.Radar" -> Screen.Radar
            "io.blueeye.navigation.Screen.Watchlist" -> Screen.Watchlist
            "io.blueeye.navigation.Screen.Settings" -> Screen.Settings
            else -> Screen.Radar // Default fallback
        }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            AppDrawer(
                drawerState = drawerState,
                currentScreen = currentScreen,
                scope = scope,
                navigateTo = { screen ->
                    navController.navigate(screen) {
                        // Pop up to the start destination of the graph to
                        // avoid building up a large stack of destinations
                        // on the back stack as users select items
                        popUpTo(navController.graph.startDestinationId) {
                            saveState = true
                        }
                        // Avoid multiple copies of the same destination when
                        // reselecting the same item
                        launchSingleTop = true
                        // Restore state when reselecting a previously selected item
                        restoreState = true
                    }
                },
            )
        },
    ) {
        NavHost(
            navController = navController,
            startDestination = Screen.Radar,
        ) {
            composable<Screen.Radar> {
                RadarScreen(
                    onDeviceClick = { deviceId ->
                        navController.navigate(Screen.Details(deviceId = deviceId))
                    },
                    onMenuClick = {
                        scope.launch { drawerState.open() }
                    },
                )
            }

            composable<Screen.Details> { backStackEntry ->
                val route = backStackEntry.toRoute<Screen.Details>()
                DetailsScreen(
                    fingerprint = route.deviceId,
                    onBackClick = {
                        navController.popBackStack()
                    },
                )
            }

            composable<Screen.Watchlist> {
                WatchlistScreen(
                    onDeviceClick = { deviceId ->
                        navController.navigate(Screen.Details(deviceId = deviceId))
                    },
                    onMenuClick = {
                        scope.launch { drawerState.open() }
                    },
                )
            }

            composable<Screen.Settings> {
                SettingsScreen(
                    onMenuClick = { scope.launch { drawerState.open() } },
                    onDeviceClick = { deviceId ->
                        navController.navigate(Screen.Details(deviceId = deviceId))
                    },
                )
            }
        }
    }
}
