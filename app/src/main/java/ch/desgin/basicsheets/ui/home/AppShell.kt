package ch.desgin.basicsheets.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bookmark
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.TrackChanges
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import ch.desgin.basicsheets.ui.library.LibraryScreen
import ch.desgin.basicsheets.ui.settings.SettingsScreen
import ch.desgin.basicsheets.ui.theme.PetrolGradient

/** The home shell: a persistent petrol bottom nav over the Noten / Lesezeichen / Üben / Einstellungen tabs. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppShell(
    onOpenSheet: (Int) -> Unit,
    onOpenSetlist: (Int) -> Unit,
    onOpenCollection: (Int) -> Unit,
    onOpenComposer: (String) -> Unit,
    onOpenGenre: (String) -> Unit,
    onEditSheet: (Int) -> Unit,
) {
    var tab by remember { mutableStateOf("noten") }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),   // tabs provide their own top inset; the nav bar handles the bottom
        bottomBar = {
            NavigationBar(containerColor = Color.Transparent, modifier = Modifier.background(PetrolGradient)) {
                val colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = MaterialTheme.colorScheme.onPrimary,
                    selectedTextColor = MaterialTheme.colorScheme.onPrimary,
                    indicatorColor = Color.Transparent,   // Figma shows no selection pill
                    unselectedIconColor = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f),
                    unselectedTextColor = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f),
                )
                NavigationBarItem(tab == "noten", { tab = "noten" }, icon = { Icon(Icons.Outlined.MusicNote, null) }, label = { Text("Noten") }, colors = colors)
                NavigationBarItem(tab == "lesezeichen", { tab = "lesezeichen" }, icon = { Icon(Icons.Outlined.Bookmark, null) }, label = { Text("Lesezeichen") }, colors = colors)
                NavigationBarItem(tab == "ueben", { tab = "ueben" }, icon = { Icon(Icons.Outlined.TrackChanges, null) }, label = { Text("Üben") }, colors = colors)
                NavigationBarItem(tab == "einstellungen", { tab = "einstellungen" }, icon = { Icon(Icons.Outlined.Settings, null) }, label = { Text("Einstellungen") }, colors = colors)
            }
        },
    ) { pad ->
        Box(Modifier.fillMaxSize().padding(pad)) {
            when (tab) {
                "noten" -> LibraryScreen(onOpen = onOpenSheet, onOpenSetlist = onOpenSetlist, onOpenCollection = onOpenCollection, onOpenComposer = onOpenComposer, onOpenGenre = onOpenGenre, onEdit = onEditSheet)
                "lesezeichen" -> BookmarksScreen(onOpen = onOpenSheet)
                "ueben" -> PracticeScreen(onOpen = onOpenSheet)
                else -> SettingsScreen()
            }
        }
    }
}
