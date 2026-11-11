package ch.desgin.basicsheets.ui.library

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import ch.desgin.basicsheets.data.Sheet

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(onOpen: (Int) -> Unit, vm: LibraryViewModel = viewModel()) {
    val context = LocalContext.current
    val sheets by vm.shown.collectAsState()
    val tab by vm.tab.collectAsState()
    val query by vm.search.collectAsState()

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) vm.importPdf(context, uri)
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Meine Noten") }) },
        floatingActionButton = {
            FloatingActionButton(onClick = { picker.launch(arrayOf("application/pdf")) }) {
                Icon(Icons.Filled.Add, contentDescription = "Noten hinzufügen")
            }
        },
    ) { pad ->
        Column(Modifier.fillMaxSize().padding(pad)) {
            OutlinedTextField(
                value = query,
                onValueChange = vm::setSearch,
                leadingIcon = { Icon(Icons.Filled.Search, null) },
                placeholder = { Text("Suchen …") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            )
            val tabs = listOf(
                "songs" to "Titel", "recent" to "Zuletzt", "favorites" to "Favoriten",
                "composer" to "Komponisten", "genre" to "Genres",
            )
            Row(
                Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                tabs.forEach { (key, label) ->
                    FilterChip(selected = tab == key, onClick = { vm.setTab(key) }, label = { Text(label) })
                }
            }
            if (sheets.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        if (query.isBlank()) "Noch keine Noten. Tippe + um eine PDF hinzuzufügen."
                        else "Keine Treffer.",
                        color = MaterialTheme.colorScheme.outline,
                    )
                }
            } else {
                LazyColumn(
                    Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(sheets, key = { it.id }) { s ->
                        SheetCard(
                            sheet = s,
                            onOpen = { onOpen(s.id) },
                            onDelete = { vm.delete(s) },
                            onRate = { vm.rate(s, it) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SheetCard(sheet: Sheet, onOpen: () -> Unit, onDelete: () -> Unit, onRate: (Int) -> Unit) {
    var menu by remember { mutableStateOf(false) }
    ElevatedCard(Modifier.fillMaxWidth().clickable { onOpen() }) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    sheet.name,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                if (sheet.information().isNotBlank()) {
                    Text(
                        sheet.information(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                }
                Row {
                    (1..5).forEach { i ->
                        Icon(
                            Icons.Filled.Star,
                            contentDescription = null,
                            tint = if (i <= sheet.rating) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.outlineVariant,
                            modifier = Modifier.size(20.dp)
                                .clickable { onRate(if (sheet.rating == i) 0 else i) },
                        )
                    }
                }
            }
            Box {
                IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, "Mehr") }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text("Löschen") }, onClick = { menu = false; onDelete() })
                }
            }
        }
    }
}
