package ch.desgin.basicsheets.ui

import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import ch.desgin.basicsheets.ui.library.LibraryScreen
import ch.desgin.basicsheets.ui.viewer.ViewerScreen

@Composable
fun AppRoot() {
    val nav = rememberNavController()
    NavHost(navController = nav, startDestination = "library") {
        composable("library") {
            LibraryScreen(onOpen = { id -> nav.navigate("viewer/$id") })
        }
        composable(
            route = "viewer/{id}",
            arguments = listOf(navArgument("id") { type = NavType.IntType }),
        ) { backStack ->
            val id = backStack.arguments?.getInt("id") ?: 0
            ViewerScreen(sheetId = id, onBack = { nav.popBackStack() })
        }
    }
}
