package com.geotag.app.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.geotag.app.ui.screens.auth.LoginScreen
import com.geotag.app.ui.screens.camera.CameraScreen
import com.geotag.app.ui.screens.detail.DetailScreen
import com.geotag.app.ui.screens.detail.EditScreen
import com.geotag.app.ui.screens.home.HomeScreen
import com.geotag.app.ui.screens.route.RouteScreen
import com.google.firebase.auth.FirebaseAuth

sealed class Screen(val route: String) {
    object Login : Screen("login")
    object Home : Screen("home")
    object Camera : Screen("camera")
    object Route : Screen("route")
    object Detail : Screen("detail/{locationId}") {
        fun createRoute(locationId: String) = "detail/$locationId"
    }
    object Edit : Screen("edit/{locationId}") {
        fun createRoute(locationId: String) = "edit/$locationId"
    }
}

@Composable
fun NavGraph() {
    val navController = rememberNavController()
    val startDestination = if (FirebaseAuth.getInstance().currentUser != null) {
        Screen.Home.route
    } else {
        Screen.Login.route
    }

    NavHost(navController = navController, startDestination = startDestination) {
        composable(Screen.Login.route) {
            LoginScreen(
                onLoginSuccess = {
                    navController.navigate(Screen.Home.route) {
                        popUpTo(Screen.Login.route) { inclusive = true }
                    }
                }
            )
        }
        composable(Screen.Home.route) {
            HomeScreen(
                onAddClick = { navController.navigate(Screen.Camera.route) },
                onItemClick = { locationId -> navController.navigate(Screen.Detail.createRoute(locationId)) },
                onRouteClick = { navController.navigate(Screen.Route.route) },
                onLogout = {
                    navController.navigate(Screen.Login.route) {
                        popUpTo(Screen.Home.route) { inclusive = true }
                    }
                }
            )
        }
        composable(Screen.Camera.route) {
            CameraScreen(
                onSaved = { navController.popBackStack() },
                onBack = { navController.popBackStack() }
            )
        }
        composable(Screen.Route.route) {
            RouteScreen(
                onBack = { navController.popBackStack() }
            )
        }
        composable(
            Screen.Detail.route,
            arguments = listOf(navArgument("locationId") { type = NavType.StringType })
        ) { backStackEntry ->
            val locationId = backStackEntry.arguments?.getString("locationId") ?: ""
            DetailScreen(
                locationId = locationId,
                onBack = { navController.popBackStack() },
                onEdit = { navController.navigate(Screen.Edit.createRoute(locationId)) },
                onDeleted = { navController.popBackStack() }
            )
        }
        composable(
            Screen.Edit.route,
            arguments = listOf(navArgument("locationId") { type = NavType.StringType })
        ) { backStackEntry ->
            val locationId = backStackEntry.arguments?.getString("locationId") ?: ""
            EditScreen(
                locationId = locationId,
                onSaved = { navController.popBackStack() },
                onBack = { navController.popBackStack() }
            )
        }
    }
}
