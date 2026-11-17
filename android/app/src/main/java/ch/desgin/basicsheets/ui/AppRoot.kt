package ch.desgin.basicsheets.ui

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import ch.desgin.basicsheets.ui.collections.CollectionDetailScreen
import ch.desgin.basicsheets.ui.composer.ComposerDetailScreen
import ch.desgin.basicsheets.ui.home.AppShell
import ch.desgin.basicsheets.ui.setlists.SetlistDetailScreen
import ch.desgin.basicsheets.ui.viewer.ViewerScreen

@Composable
fun AppRoot() {
    val nav = rememberNavController()
    NavHost(navController = nav, startDestination = "home") {
        composable("home") {
            AppShell(
                onOpenSheet = { id -> nav.navigate("viewer/$id") },
                onOpenSetlist = { id -> nav.navigate("setlist/$id") },
                onOpenCollection = { id -> nav.navigate("collection/$id") },
                onOpenComposer = { name -> nav.navigate("composer/${Uri.encode(name)}") },
            )
        }
        composable(
            route = "composer/{name}",
            arguments = listOf(navArgument("name") { type = NavType.StringType }),
        ) { backStack ->
            val name = backStack.arguments?.getString("name").orEmpty()
            ComposerDetailScreen(
                composer = name,
                onOpenSheet = { sheetId -> nav.navigate("viewer/$sheetId") },
                onBack = { nav.popBackStack() },
            )
        }
        composable(
            route = "collection/{id}",
            arguments = listOf(navArgument("id") { type = NavType.IntType }),
        ) { backStack ->
            val id = backStack.arguments?.getInt("id") ?: 0
            CollectionDetailScreen(
                collectionId = id,
                onOpenSheet = { sheetId -> nav.navigate("viewer/$sheetId") },
                onBack = { nav.popBackStack() },
            )
        }
        composable(
            route = "setlist/{id}",
            arguments = listOf(navArgument("id") { type = NavType.IntType }),
        ) { backStack ->
            val id = backStack.arguments?.getInt("id") ?: 0
            SetlistDetailScreen(
                setlistId = id,
                onOpenSong = { sheetId -> nav.navigate("viewer/$sheetId?setlist=$id") },
                onBack = { nav.popBackStack() },
            )
        }
        composable(
            route = "viewer/{id}?setlist={setlist}",
            arguments = listOf(
                navArgument("id") { type = NavType.IntType },
                navArgument("setlist") { type = NavType.IntType; defaultValue = -1 },
            ),
        ) { backStack ->
            val id = backStack.arguments?.getInt("id") ?: 0
            val setlistId = backStack.arguments?.getInt("setlist") ?: -1
            ViewerScreen(
                sheetId = id,
                onBack = { nav.popBackStack() },
                setlistId = setlistId,
                onOpenSong = { nextId ->
                    nav.navigate("viewer/$nextId?setlist=$setlistId") {
                        if (setlistId >= 0) popUpTo("setlist/$setlistId")
                    }
                },
            )
        }
    }
}
