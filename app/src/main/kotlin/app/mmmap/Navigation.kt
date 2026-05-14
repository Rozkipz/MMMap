package app.mmmap

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import app.mmmap.ui.list.NearbyScreen
import app.mmmap.ui.map.MapScreen

sealed class Screen(val route: String, val label: String) {
    data object Map    : Screen("map",    "Map")
    data object Nearby : Screen("nearby", "Nearby")
}

private val bottomNavItems = listOf(Screen.Map, Screen.Nearby)

@Composable
fun MmmapNavGraph(
    themeMode: ThemeMode,
    onCycleTheme: () -> Unit,
    isDarkTheme: Boolean,
) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    Scaffold(
        bottomBar = {
            NavigationBar {
                bottomNavItems.forEach { screen ->
                    val selected = currentDestination
                        ?.hierarchy
                        ?.any { it.route == screen.route } ?: false
                    NavigationBarItem(
                        selected = selected,
                        onClick = {
                            navController.navigate(screen.route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = {
                            Icon(
                                imageVector = when (screen) {
                                    Screen.Map    -> Icons.Filled.Map
                                    Screen.Nearby -> Icons.AutoMirrored.Filled.List
                                },
                                contentDescription = screen.label,
                            )
                        },
                        label = { Text(screen.label) },
                    )
                }
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Screen.Map.route,
        ) {
            composable(Screen.Map.route) {
                MapScreen(
                    bottomPadding = innerPadding.calculateBottomPadding(),
                    isDarkTheme = isDarkTheme,
                    themeMode = themeMode,
                    onCycleTheme = onCycleTheme,
                )
            }
            composable(Screen.Nearby.route) {
                NearbyScreen()
            }
        }
    }
}
