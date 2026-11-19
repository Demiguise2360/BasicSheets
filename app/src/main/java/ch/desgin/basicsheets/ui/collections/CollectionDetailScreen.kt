package ch.desgin.basicsheets.ui.collections

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ch.desgin.basicsheets.BasicSheetsApp
import ch.desgin.basicsheets.data.Collection
import ch.desgin.basicsheets.data.Sheet
import ch.desgin.basicsheets.ui.theme.AppTopBar
import ch.desgin.basicsheets.ui.viewer.BOOKMARK_COLORS
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CollectionDetailScreen(collectionId: Int, onOpenSheet: (Int) -> Unit, onBack: () -> Unit) {
    val app = BasicSheetsApp.instance
    val scope = rememberCoroutineScope()
    var collection by remember { mutableStateOf<Collection?>(null) }
    var members by remember { mutableStateOf<List<Sheet>>(emptyList()) }
    var allSheets by remember { mutableStateOf<List<Sheet>>(emptyList()) }
    var memberIds by remember { mutableStateOf<Set<Int>>(emptySet()) }
    var showAdd by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf(false) }
    var reload by remember { mutableIntStateOf(0) }

    LaunchedEffect(collectionId, reload) {
        collection = app.collections.get(collectionId)
        members = app.collections.sheets(collectionId)
        memberIds = members.map { it.id }.toSet()
        allSheets = app.library.all()
    }

    Scaffold(
        topBar = {
            AppTopBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(16.dp).clip(CircleShape).background(hexColor(collection?.color)))
                        Spacer(Modifier.width(10.dp))
                        Text(collection?.name ?: "Sammlung", maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Zurück") }
                },
                actions = {
                    IconButton(onClick = { editing = true }) { Icon(Icons.Filled.Edit, contentDescription = "Bearbeiten") }
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
                    "Noten hinzufügen",
                    Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 4.dp),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
                val addable = allSheets.filterNot { it.id in memberIds }
                if (addable.isEmpty()) {
                    Text("Keine weiteren Noten.", Modifier.padding(16.dp, 4.dp), color = MaterialTheme.colorScheme.outline)
                } else {
                    LazyColumn(Modifier.fillMaxWidth().heightIn(max = 260.dp)) {
                        items(addable, key = { it.id }) { s ->
                            Row(
                                Modifier.fillMaxWidth()
                                    .clickable { scope.launch { app.collections.addSheet(collectionId, s.id); reload++ } }
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

            if (members.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Sammlung ist leer. Tippe + um Noten hinzuzufügen.", color = MaterialTheme.colorScheme.outline)
                }
            } else {
                LazyColumn(
                    Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    items(members, key = { it.id }) { s ->
                        ElevatedCard(Modifier.fillMaxWidth().clickable { onOpenSheet(s.id) }) {
                            Row(Modifier.padding(start = 12.dp, end = 4.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                                Box(Modifier.size(30.dp).clip(RoundedCornerShape(8.dp)).background(hexColor(collection?.color)))
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(s.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    if (s.information().isNotBlank()) {
                                        Text(s.information(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    }
                                }
                                IconButton(onClick = { scope.launch { app.collections.removeSheet(collectionId, s.id); reload++ } }) {
                                    Icon(Icons.Filled.Close, contentDescription = "Entfernen")
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (editing) {
        CollectionDialog(
            title = "Sammlung bearbeiten",
            initialName = collection?.name ?: "",
            initialColor = collection?.color ?: BOOKMARK_COLORS.first(),
            onSave = { name, color ->
                scope.launch { collection?.let { app.collections.update(it.copy(name = name, color = color)); reload++ } }
                editing = false
            },
            onDelete = {
                scope.launch { collection?.let { app.collections.delete(it) } }
                editing = false
                onBack()
            },
            onDismiss = { editing = false },
        )
    }
}

/** Create / edit dialog for a collection: a name and a color (optionally a delete action). */
@Composable
fun CollectionDialog(
    title: String,
    initialName: String,
    initialColor: String,
    onSave: (String, String) -> Unit,
    onDelete: (() -> Unit)? = null,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf(initialName) }
    var color by remember { mutableStateOf(initialColor) }
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = { if (name.isNotBlank()) onSave(name.trim(), color) }) { Text("Speichern") } },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                if (onDelete != null) TextButton(onClick = onDelete) { Text("Löschen", color = MaterialTheme.colorScheme.error) }
                TextButton(onClick = onDismiss) { Text("Abbrechen") }
            }
        },
        title = { Text(title) },
        text = {
            Column {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Name") }, singleLine = true)
                Spacer(Modifier.size(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    for (c in BOOKMARK_COLORS) {
                        Box(
                            Modifier.size(30.dp).clip(CircleShape).background(hexColor(c))
                                .then(if (c == color) Modifier.border(3.dp, MaterialTheme.colorScheme.onSurface, CircleShape) else Modifier)
                                .clickable { color = c },
                        )
                    }
                }
            }
        },
    )
}

private fun hexColor(hex: String?): Color =
    runCatching { Color(android.graphics.Color.parseColor(hex ?: "#387373")) }.getOrDefault(Color(0xFF387373))
