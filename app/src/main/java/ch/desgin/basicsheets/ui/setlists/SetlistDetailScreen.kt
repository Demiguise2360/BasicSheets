package ch.desgin.basicsheets.ui.setlists

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ch.desgin.basicsheets.BasicSheetsApp
import ch.desgin.basicsheets.data.Setlist
import ch.desgin.basicsheets.data.SetlistItem
import ch.desgin.basicsheets.data.Sheet
import ch.desgin.basicsheets.ui.theme.AppTopBar
import androidx.compose.material3.ButtonDefaults
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SetlistDetailScreen(setlistId: Int, onOpenSong: (Int) -> Unit, onBack: () -> Unit) {
    val app = BasicSheetsApp.instance
    val scope = rememberCoroutineScope()
    var setlist by remember { mutableStateOf<Setlist?>(null) }
    var rows by remember { mutableStateOf<List<Pair<SetlistItem, Sheet>>>(emptyList()) }
    var allSheets by remember { mutableStateOf<List<Sheet>>(emptyList()) }
    var showAdd by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf(false) }
    var reloadToken by remember { mutableIntStateOf(0) }

    LaunchedEffect(setlistId, reloadToken) {
        setlist = app.setlists.get(setlistId)
        val byId = app.library.all().associateBy { it.id }
        rows = app.setlists.items(setlistId).mapNotNull { item -> byId[item.sheetId]?.let { s -> item to s } }
        allSheets = app.library.all()
    }
    fun reload() { reloadToken++ }

    Scaffold(
        topBar = {
            AppTopBar(
                title = { Text(setlist?.name ?: "Setlist") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Zurück") }
                },
                actions = {
                    TextButton(
                        onClick = { rows.firstOrNull()?.let { onOpenSong(it.second.id) } },
                        enabled = rows.isNotEmpty(),
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onPrimary),
                    ) {
                        Text("Abspielen")
                    }
                    IconButton(onClick = { renaming = true }) { Icon(Icons.Filled.Edit, contentDescription = "Umbenennen") }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAdd = !showAdd }) {
                Icon(if (showAdd) Icons.Filled.Check else Icons.Filled.Add, contentDescription = "Noten hinzufügen")
            }
        },
    ) { pad ->
        Column(Modifier.fillMaxSize().padding(pad)) {
            if (showAdd) {
                Text(
                    "Zur Setlist hinzufügen",
                    Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 4.dp),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
                if (allSheets.isEmpty()) {
                    Text("Keine Noten in der Bibliothek.", Modifier.padding(16.dp, 4.dp), color = MaterialTheme.colorScheme.outline)
                } else {
                    LazyColumn(Modifier.fillMaxWidth().heightIn(max = 260.dp)) {
                        items(allSheets, key = { it.id }) { s ->
                            Row(
                                Modifier.fillMaxWidth()
                                    .clickable { scope.launch { app.setlists.addSheet(setlistId, s.id); reload() } }
                                    .padding(horizontal = 16.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(s.name, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Icon(Icons.Filled.Add, contentDescription = "Hinzufügen")
                            }
                        }
                    }
                }
                HorizontalDivider()
            }

            if (rows.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Setlist ist leer. Tippe + um Stücke hinzuzufügen.", color = MaterialTheme.colorScheme.outline)
                }
            } else {
                LazyColumn(
                    Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    itemsIndexed(rows, key = { _, r -> r.first.id }) { i, (item, sheet) ->
                        SetlistRow(
                            index = i, total = rows.size, sheet = sheet,
                            onOpen = { onOpenSong(sheet.id) },
                            onUp = { scope.launch { app.setlists.move(setlistId, item.id, -1); reload() } },
                            onDown = { scope.launch { app.setlists.move(setlistId, item.id, 1); reload() } },
                            onRemove = { scope.launch { app.setlists.removeItem(item); reload() } },
                        )
                    }
                }
            }
        }
    }

    if (renaming) {
        NameDialog(
            title = "Setlist umbenennen",
            initial = setlist?.name ?: "",
            onSave = { name -> scope.launch { app.setlists.rename(setlistId, name); reload() }; renaming = false },
            onDismiss = { renaming = false },
        )
    }
}

@Composable
private fun SetlistRow(
    index: Int,
    total: Int,
    sheet: Sheet,
    onOpen: () -> Unit,
    onUp: () -> Unit,
    onDown: () -> Unit,
    onRemove: () -> Unit,
) {
    ElevatedCard(Modifier.fillMaxWidth()) {
        Row(
            Modifier.padding(start = 12.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "${index + 1}",
                Modifier.width(28.dp),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.outline,
            )
            Column(Modifier.weight(1f).clickable { onOpen() }) {
                Text(sheet.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (sheet.information().isNotBlank()) {
                    Text(
                        sheet.information(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            IconButton(onClick = onUp, enabled = index > 0) { Icon(Icons.Filled.KeyboardArrowUp, contentDescription = "Nach oben") }
            IconButton(onClick = onDown, enabled = index < total - 1) { Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "Nach unten") }
            IconButton(onClick = onRemove) { Icon(Icons.Filled.Close, contentDescription = "Entfernen") }
        }
    }
}
