package ch.desgin.basicsheets.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import ch.desgin.basicsheets.BasicSheetsApp
import ch.desgin.basicsheets.data.Sheet

/**
 * Placeholder library screen — proves the Room/Compose wiring. The full library (search,
 * sort, filters, favorites, import, the viewer) is built in the following phases (see PORTING.md).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppRoot() {
    val app = BasicSheetsApp.instance
    var sheets by remember { mutableStateOf<List<Sheet>>(emptyList()) }
    LaunchedEffect(Unit) { sheets = app.db.sheets().all() }

    Scaffold(topBar = { TopAppBar(title = { Text("Meine Noten") }) }) { pad ->
        if (sheets.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(pad), contentAlignment = Alignment.Center) {
                Text("Noch keine Noten. (Import folgt in einer der nächsten Phasen.)")
            }
        } else {
            LazyColumn(Modifier.fillMaxSize().padding(pad)) {
                items(sheets, key = { it.id }) { s ->
                    ListItem(
                        headlineContent = { Text(s.name) },
                        supportingContent = { if (s.information().isNotBlank()) Text(s.information()) },
                    )
                    HorizontalDivider()
                }
            }
        }
    }
}
