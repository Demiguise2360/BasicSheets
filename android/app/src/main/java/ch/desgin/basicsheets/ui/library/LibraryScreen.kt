package ch.desgin.basicsheets.ui.library

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AssistChip
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import ch.desgin.basicsheets.BasicSheetsApp
import ch.desgin.basicsheets.data.Collection
import ch.desgin.basicsheets.data.Setlist
import ch.desgin.basicsheets.data.Sheet
import ch.desgin.basicsheets.ui.collections.CollectionDialog
import ch.desgin.basicsheets.ui.setlists.NameDialog
import ch.desgin.basicsheets.ui.theme.appTopBarColors
import ch.desgin.basicsheets.ui.viewer.BOOKMARK_COLORS
import kotlinx.coroutines.launch
import kotlin.math.absoluteValue

private val TABS = listOf(
    "songs" to "Songs",
    "recent" to "Zuletzt",
    "favorites" to "Favoriten",
    "composer" to "Komponisten",
    "genre" to "Genres",
    "sammlungen" to "Sammlungen",
    "setlists" to "Setlists",
)
private val SONG_TABS = setOf("songs", "recent", "favorites", "composer", "genre")

private val FIELD_OPTIONS = listOf(
    "all" to "Alle", "Name" to "Name", "Composer" to "Komponist",
    "Part" to "Stimme", "Genre" to "Genre", "Tags" to "Tags",
)
private val SORT_OPTIONS = listOf(
    "name" to "Name", "date" to "Neueste", "composer" to "Komponist",
    "rating" to "Bewertung", "difficulty" to "Schwierigkeit",
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    onOpen: (Int) -> Unit,
    onOpenSetlist: (Int) -> Unit,
    onOpenCollection: (Int) -> Unit,
    onOpenComposer: (String) -> Unit,
    onOpenGenre: (String) -> Unit,
    onEdit: (Int) -> Unit,
    vm: LibraryViewModel = viewModel(),
) {
    val app = BasicSheetsApp.instance
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val sheets by vm.shown.collectAsState()
    val query by vm.search.collectAsState()
    val sortOrder by vm.sortOrder.collectAsState()
    val searchField by vm.searchField.collectAsState()
    val setlists by app.setlists.observeAll().collectAsState(initial = emptyList())
    val collections by app.collections.observeAll().collectAsState(initial = emptyList())

    var tabKey by remember { mutableStateOf("songs") }
    var creatingSetlist by remember { mutableStateOf(false) }
    var creatingCollection by remember { mutableStateOf(false) }

    LaunchedEffect(tabKey) { if (tabKey in SONG_TABS) vm.setTab(tabKey) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) vm.importPdf(context, uri)
    }
    fun onPlus() {
        when (tabKey) {
            "setlists" -> creatingSetlist = true
            "sammlungen" -> creatingCollection = true
            else -> picker.launch(arrayOf("application/pdf", "image/*"))
        }
    }

    Column(Modifier.fillMaxSize()) {
        // Petrol header: title + scrollable tabs
        Surface(color = MaterialTheme.colorScheme.primary) {
            Column {
                TopAppBar(title = { Text("Meine Noten") }, colors = appTopBarColors())
                ScrollableTabRow(
                    selectedTabIndex = TABS.indexOfFirst { it.first == tabKey }.coerceAtLeast(0),
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    edgePadding = 12.dp,
                    divider = {},
                ) {
                    TABS.forEach { (key, label) ->
                        Tab(selected = tabKey == key, onClick = { tabKey = key }, text = { Text(label) })
                    }
                }
            }
        }

        // Search + add
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = vm::setSearch,
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                placeholder = { Text("Suchen …") },
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
            FilledIconButton(
                onClick = { onPlus() },
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                ),
            ) { Icon(Icons.Filled.Add, contentDescription = "Hinzufügen") }
        }

        // Field + sort filters (songs tab, mirrors the MAUI library controls)
        if (tabKey == "songs") {
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
                    .padding(start = 12.dp, end = 12.dp, bottom = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilterDropdown("Feld", FIELD_OPTIONS, searchField) { vm.setSearchField(it) }
                FilterDropdown("Sortieren", SORT_OPTIONS, sortOrder) { vm.setSortOrder(it) }
            }
        }

        when (tabKey) {
            "setlists" -> SetlistsTab(setlists, onOpenSetlist)
            "sammlungen" -> CollectionsTab(collections, onOpenCollection)
            "composer" -> ComposerTab(sheets, query, onOpenComposer)
            "genre" -> GenreTab(sheets, query, onOpenGenre)
            "recent" -> SongList(sheets, query, onOpen, onDelete = { vm.delete(it) }, onRate = { s, n -> vm.rate(s, n) }, onTogglePractice = { vm.togglePractice(it) }, onEdit = onEdit, emptyHint = "Noch nichts geöffnet. Öffne eine Note, um sie hier zu sehen.")
            else -> SongList(sheets, query, onOpen, onDelete = { vm.delete(it) }, onRate = { s, n -> vm.rate(s, n) }, onTogglePractice = { vm.togglePractice(it) }, onEdit = onEdit)
        }
    }

    if (creatingSetlist) {
        NameDialog(
            title = "Neue Setlist",
            initial = "",
            onSave = { name -> scope.launch { app.setlists.create(name) }; creatingSetlist = false },
            onDismiss = { creatingSetlist = false },
        )
    }
    if (creatingCollection) {
        CollectionDialog(
            title = "Neue Sammlung",
            initialName = "",
            initialColor = BOOKMARK_COLORS.first(),
            onSave = { name, color -> scope.launch { app.collections.create(name, color) }; creatingCollection = false },
            onDismiss = { creatingCollection = false },
        )
    }
}

@Composable
private fun SongList(
    sheets: List<Sheet>,
    query: String,
    onOpen: (Int) -> Unit,
    onDelete: (Sheet) -> Unit,
    onRate: (Sheet, Int) -> Unit,
    onTogglePractice: (Sheet) -> Unit,
    onEdit: (Int) -> Unit,
    emptyHint: String = "Noch keine Noten. Tippe + um eine PDF hinzuzufügen.",
) {
    if (sheets.isEmpty()) {
        EmptyTab(if (query.isBlank()) emptyHint else "Keine Treffer.")
        return
    }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(sheets, key = { it.id }) { s ->
            SheetCard(s, onOpen = { onOpen(s.id) }, onDelete = { onDelete(s) }, onRate = { onRate(s, it) }, onTogglePractice = { onTogglePractice(s) }, onEdit = { onEdit(s.id) })
        }
    }
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
private fun SheetCard(sheet: Sheet, onOpen: () -> Unit, onDelete: () -> Unit, onRate: (Int) -> Unit, onTogglePractice: () -> Unit, onEdit: () -> Unit) {
    var menu by remember { mutableStateOf(false) }
    Box {
        ElevatedCard(Modifier.fillMaxWidth().combinedClickable(onClick = onOpen, onLongClick = { menu = true })) {
            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(34.dp).clip(RoundedCornerShape(8.dp)).background(squareColor(sheet)))
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(sheet.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (sheet.part.isNotBlank()) {
                            Text(sheet.part, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        if (sheet.inPractice) Pill("Üben", MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.onPrimary)
                        difficultyPill(sheet.difficulty)?.let { (label, bg, fg) -> Pill(label, bg, fg) }
                        sheet.tags.split(',').map { it.trim() }.filter { it.isNotEmpty() }.forEach {
                            Pill(it, MaterialTheme.colorScheme.secondaryContainer, MaterialTheme.colorScheme.onSecondaryContainer)
                        }
                        if (sheet.composer.isNotBlank()) {
                            Text(sheet.composer, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                        }
                    }
                }
                if (sheet.rating > 0) {
                    Row {
                        (1..5).forEach { i ->
                            Icon(
                                Icons.Filled.Star,
                                contentDescription = null,
                                tint = if (i <= sheet.rating) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.outlineVariant,
                                modifier = Modifier.size(16.dp).clickable { onRate(if (sheet.rating == i) 0 else i) },
                            )
                        }
                    }
                }
            }
        }
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            DropdownMenuItem(text = { Text("Bearbeiten") }, onClick = { menu = false; onEdit() })
            DropdownMenuItem(
                text = { Text(if (sheet.inPractice) "Üben-Markierung entfernen" else "Zum Üben markieren") },
                onClick = { menu = false; onTogglePractice() },
            )
            DropdownMenuItem(text = { Text("Bewerten ★") }, onClick = { menu = false; onRate(if (sheet.rating >= 5) 0 else sheet.rating + 1) })
            DropdownMenuItem(text = { Text("Löschen") }, onClick = { menu = false; onDelete() })
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FilterDropdown(leading: String, options: List<Pair<String, String>>, selected: String, onSelect: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val label = options.firstOrNull { it.first == selected }?.second ?: selected
    Box {
        AssistChip(
            onClick = { open = true },
            label = { Text("$leading: $label") },
            trailingIcon = { Icon(Icons.Filled.ArrowDropDown, contentDescription = null) },
        )
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            options.forEach { (value, lbl) ->
                DropdownMenuItem(
                    text = { Text(lbl) },
                    onClick = { open = false; onSelect(value) },
                    leadingIcon = { if (value == selected) Icon(Icons.Filled.Check, contentDescription = null) },
                )
            }
        }
    }
}

@Composable
private fun Pill(text: String, bg: Color, fg: Color) {
    Box(Modifier.clip(RoundedCornerShape(6.dp)).background(bg).padding(horizontal = 8.dp, vertical = 2.dp)) {
        Text(text, style = MaterialTheme.typography.labelSmall, color = fg)
    }
}

@Composable
private fun SetlistsTab(setlists: List<Setlist>, onOpen: (Int) -> Unit) {
    if (setlists.isEmpty()) {
        EmptyTab("Noch keine Setlists. Tippe + um eine zu erstellen.")
        return
    }
    val app = BasicSheetsApp.instance
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(setlists, key = { it.id }) { sl ->
            val count by produceState(-1, sl.id) { value = runCatching { app.setlists.sheets(sl.id).size }.getOrDefault(0) }
            ElevatedCard(Modifier.fillMaxWidth().clickable { onOpen(sl.id) }) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(sl.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(if (count < 0) "…" else "$count Stücke", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                    }
                }
            }
        }
    }
}

@Composable
private fun CollectionsTab(collections: List<Collection>, onOpen: (Int) -> Unit) {
    if (collections.isEmpty()) {
        EmptyTab("Noch keine Sammlungen. Tippe + um eine zu erstellen.")
        return
    }
    val app = BasicSheetsApp.instance
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(collections, key = { it.id }) { c ->
            val count by produceState(-1, c.id) { value = runCatching { app.collections.count(c.id) }.getOrDefault(0) }
            ElevatedCard(Modifier.fillMaxWidth().clickable { onOpen(c.id) }) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(28.dp).clip(RoundedCornerShape(8.dp)).background(hexColor(c.color)))
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(c.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(if (count < 0) "…" else "$count Stücke", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                    }
                }
            }
        }
    }
}

@Composable
private fun ComposerTab(sheets: List<Sheet>, query: String, onOpen: (String) -> Unit) {
    val composers = remember(sheets) {
        sheets.asSequence()
            .map { it.composer.trim() }
            .filter { it.isNotEmpty() }
            .groupingBy { it }
            .eachCount()
            .toList()
            .sortedBy { it.first.lowercase() }
    }
    if (composers.isEmpty()) {
        EmptyTab(if (query.isBlank()) "Noch keine Komponisten. Füge Noten mit Komponist hinzu." else "Keine Treffer.")
        return
    }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(composers, key = { it.first }) { (name, count) ->
            ElevatedCard(Modifier.fillMaxWidth().clickable { onOpen(name) }) {
                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(38.dp).clip(CircleShape).background(nameColor(name)), contentAlignment = Alignment.Center) {
                        Icon(Icons.Filled.Person, contentDescription = null, tint = Color.White, modifier = Modifier.size(22.dp))
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            "$count ${if (count == 1) "Stück" else "Stücke"}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun GenreTab(sheets: List<Sheet>, query: String, onOpen: (String) -> Unit) {
    val genres = remember(sheets) {
        sheets.asSequence()
            .map { it.genre.trim() }
            .filter { it.isNotEmpty() }
            .groupingBy { it }
            .eachCount()
            .toList()
            .sortedBy { it.first.lowercase() }
    }
    if (genres.isEmpty()) {
        EmptyTab(if (query.isBlank()) "Noch keine Genres. Füge Noten mit Genre hinzu." else "Keine Treffer.")
        return
    }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(genres, key = { it.first }) { (name, count) ->
            ElevatedCard(Modifier.fillMaxWidth().clickable { onOpen(name) }) {
                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(38.dp).clip(RoundedCornerShape(10.dp)).background(nameColor(name)), contentAlignment = Alignment.Center) {
                        Icon(Icons.Filled.Category, contentDescription = null, tint = Color.White, modifier = Modifier.size(22.dp))
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            "$count ${if (count == 1) "Stück" else "Stücke"}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline,
                        )
                    }
                }
            }
        }
    }
}

private fun nameColor(seed: String): Color =
    SQUARE_COLORS[seed.hashCode().absoluteValue % SQUARE_COLORS.size]

private fun hexColor(hex: String?): Color =
    runCatching { Color(android.graphics.Color.parseColor(hex ?: "#387373")) }.getOrDefault(Color(0xFF387373))

@Composable
private fun EmptyTab(text: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(text, color = MaterialTheme.colorScheme.outline)
    }
}

private val SQUARE_COLORS = listOf(
    Color(0xFF7E57C2), Color(0xFF26A69A), Color(0xFF66BB6A), Color(0xFF42A5F5),
    Color(0xFFFFA726), Color(0xFFEC407A), Color(0xFF26C6DA), Color(0xFF9CCC65),
)

private fun squareColor(sheet: Sheet): Color {
    val seed = (sheet.part.ifBlank { sheet.name }).hashCode().absoluteValue
    return SQUARE_COLORS[seed % SQUARE_COLORS.size]
}

/** (label, background, foreground) for a difficulty, or null for none. */
private fun difficultyPill(difficulty: String): Triple<String, Color, Color>? = when (difficulty) {
    "beginner" -> Triple("Anfänger", Color(0xFFCDEBD0), Color(0xFF2E7D32))
    "intermediate" -> Triple("Mittel", Color(0xFFFCEFC2), Color(0xFF8D6E00))
    "advanced" -> Triple("Fortgeschritten", Color(0xFFF8D2D2), Color(0xFFC0392B))
    "expert" -> Triple("Experte", Color(0xFFF3C0C6), Color(0xFF922B21))
    else -> null
}
