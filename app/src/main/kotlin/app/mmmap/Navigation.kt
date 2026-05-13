package app.mmmap

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import app.mmmap.ui.list.NearbyScreen
import app.mmmap.ui.map.MapScreen

sealed class Screen(val route: String) {
    data object Map : Screen("map")
    data object Nearby : Screen("nearby")
}

@Composable
fun MMMapNavGraph() {
    val navController = rememberNavController()
    NavHost(navController = navController, startDestination = Screen.Map.route) {
        composable(Screen.Map.route) {
            MapScreen(onNavigateToNearby = { navController.navigate(Screen.Nearby.route) })
        }
        composable(Screen.Nearby.route) {
            NearbyScreen()
        }
    }
}
