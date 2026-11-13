package ch.desgin.basicsheets.ui.viewer

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bookmarks
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import ch.desgin.basicsheets.BasicSheetsApp
import ch.desgin.basicsheets.annot.Hairpin
import ch.desgin.basicsheets.annot.MarkerStroke
import ch.desgin.basicsheets.annot.PageAnnotations
import ch.desgin.basicsheets.annot.PenStroke
import ch.desgin.basicsheets.annot.ShapeAnnot
import ch.desgin.basicsheets.annot.Stamp
import ch.desgin.basicsheets.annot.TextAnnot
import ch.desgin.basicsheets.annot.Tool
import ch.desgin.basicsheets.audio.Metronome
import ch.desgin.basicsheets.data.Bookmark
import ch.desgin.basicsheets.data.Sheet
import ch.desgin.basicsheets.pdf.OutlineEntry
import ch.desgin.basicsheets.pdf.PdfDoc
import ch.desgin.basicsheets.pdf.PdfOutline
import ch.desgin.basicsheets.pdf.PdfTextIndex
import ch.desgin.basicsheets.pdf.SearchHit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.hypot

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ViewerScreen(sheetId: Int, onBack: () -> Unit) {
    val app = BasicSheetsApp.instance
    val scope = rememberCoroutineScope()

    var sheet by remember { mutableStateOf<Sheet?>(null) }
    var doc by remember { mutableStateOf<PdfDoc?>(null) }
    var pageCount by remember { mutableIntStateOf(0) }
    var page by remember { mutableIntStateOf(0) }
    var mode by remember { mutableStateOf(DisplayMode.SINGLE) }

    val metro = remember { Metronome() }
    var showMetro by remember { mutableStateOf(false) }
    var showModeMenu by remember { mutableStateOf(false) }
    DisposableEffect(Unit) { onDispose { metro.release() } }

    // Annotation edit state
    var editMode by remember { mutableStateOf(false) }
    var annotations by remember { mutableStateOf(PageAnnotations()) }
    var tool by remember { mutableStateOf(Tool.PEN) }
    var inkColor by remember { mutableStateOf(Color.Red) }
    var sizeLevel by remember { mutableIntStateOf(4) }
    var fill by remember { mutableStateOf(false) }
    var stampType by remember { mutableStateOf("flat") }
    var showStamps by remember { mutableStateOf(false) }
    var pendingText by remember { mutableStateOf<Offset?>(null) }
    var textInput by remember { mutableStateOf("") }
    var annoVersion by remember { mutableIntStateOf(0) }   // bumped on edit exit → reading overlays reload
    val undoStack = remember { mutableStateListOf<PageAnnotations>() }

    // Bookmarks + outline
    var showBookmarks by remember { mutableStateOf(false) }
    var addingBookmark by remember { mutableStateOf(false) }
    var bookmarks by remember { mutableStateOf<List<Bookmark>>(emptyList()) }
    var outline by remember { mutableStateOf<List<OutlineEntry>>(emptyList()) }
    var jumpToken by remember { mutableIntStateOf(0) }

    // In-document search
    var showSearch by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var searchResults by remember { mutableStateOf<List<SearchHit>>(emptyList()) }
    var searching by remember { mutableStateOf(false) }
    var searchIdx by remember { mutableIntStateOf(-1) }
    var showMatchNav by remember { mutableStateOf(false) }
    val textIndex = remember { PdfTextIndex() }

    val renderPx = with(LocalDensity.current) { (LocalConfiguration.current.screenWidthDp.dp.toPx() * 2f).toInt() }

    LaunchedEffect(sheetId) {
        val s = app.library.byId(sheetId) ?: return@LaunchedEffect
        sheet = s
        bookmarks = app.bookmarks.forFile(s.filePath)
        mode = DisplayMode.from(s.pageMode)
        val d = withContext(Dispatchers.IO) { PdfDoc.open(app.library.fileFor(s)) }
        doc = d
        pageCount = d?.pageCount ?: 0
        page = 0
        outline = PdfOutline.extract(app.library.fileFor(s))
    }
    DisposableEffect(doc) { onDispose { doc?.close() } }

    // Load the page's annotations when entering edit mode or turning pages while editing.
    LaunchedEffect(editMode, page, sheet?.filePath) {
        val key = sheet?.filePath
        if (editMode && !key.isNullOrEmpty()) {
            annotations = app.annotations.load(key, page)
            undoStack.clear()
        }
    }

    // Debounced in-document search while the search sheet is open.
    LaunchedEffect(searchQuery, showSearch) {
        if (!showSearch) return@LaunchedEffect
        showMatchNav = false
        searchIdx = -1
        val q = searchQuery.trim()
        if (q.length < 2) { searchResults = emptyList(); searching = false; return@LaunchedEffect }
        searching = true
        delay(300)
        val s = sheet
        if (s != null) {
            textIndex.ensure(app.library.fileFor(s), s.filePath)
            searchResults = textIndex.search(q)
        }
        searching = false
    }

    fun turn(delta: Int) { page = (page + delta).coerceIn(0, maxOf(0, pageCount - 1)) }
    fun chooseMode(m: DisplayMode) {
        mode = m
        sheet?.let { s -> scope.launch { app.library.update(s.copy(pageMode = m.key)) } }
    }
    fun exitEdit() { editMode = false; annoVersion++ }
    fun saveAnn() {
        val key = sheet?.filePath ?: return
        val p = page
        val data = annotations
        scope.launch { app.annotations.save(key, p, data) }
    }
    fun pushUndo() {
        undoStack.add(annotations)
        if (undoStack.size > 50) undoStack.removeAt(0)
    }
    fun addPen(s: PenStroke) { annotations = annotations.copy(penStrokes = annotations.penStrokes + s); saveAnn() }
    fun addMarker(m: MarkerStroke) { annotations = annotations.copy(markerData = annotations.markerData + m); saveAnn() }
    fun addShape(s: ShapeAnnot) { annotations = annotations.copy(shapeData = annotations.shapeData + s); saveAnn() }
    fun addHairpin(h: Hairpin) { annotations = annotations.copy(hairpinData = annotations.hairpinData + h); saveAnn() }
    fun placeStamp(nx: Float, ny: Float) {
        pushUndo()
        annotations = annotations.copy(stampData = annotations.stampData + Stamp(stampType, nx, ny, inkColor.toArgb(), stampFrac(sizeLevel)))
        saveAnn()
    }
    fun commitText(nx: Float, ny: Float, text: String) {
        if (text.isBlank()) return
        pushUndo()
        annotations = annotations.copy(textData = annotations.textData + TextAnnot(text, nx, ny, inkColor.toArgb(), textFrac(sizeLevel)))
        saveAnn()
    }
    fun onTapAt(nx: Float, ny: Float) {
        if (tool == Tool.STAMP) placeStamp(nx, ny)
        else if (tool == Tool.TEXT) { pendingText = Offset(nx, ny); textInput = "" }
    }
    fun eraseAt(nx: Float, ny: Float) {
        val r = 0.03f
        fun nearPts(pts: List<ch.desgin.basicsheets.annot.Pt>) = pts.any { abs(it.x - nx) < r && abs(it.y - ny) < r }
        val before = annotations
        val next = annotations.copy(
            penStrokes = annotations.penStrokes.filterNot { nearPts(it.points) },
            markerData = annotations.markerData.filterNot { nearPts(it.points) },
            shapeData = annotations.shapeData.filterNot { distToSeg(nx, ny, it.x1, it.y1, it.x2, it.y2) < r },
            hairpinData = annotations.hairpinData.filterNot { distToSeg(nx, ny, it.x1, it.y1, it.x2, it.y2) < r },
            stampData = annotations.stampData.filterNot { abs(it.x - nx) < r && abs(it.y - ny) < r * 2 },
            textData = annotations.textData.filterNot { abs(it.x - nx) < r * 3 && abs(it.y - ny) < r * 2 },
        )
        if (next != before) { annotations = next; saveAnn() }
    }
    fun undoAnn() {
        if (undoStack.isNotEmpty()) { annotations = undoStack.removeAt(undoStack.size - 1); saveAnn() }
    }
    fun jumpToPage(target: Int) {
        page = target.coerceIn(0, maxOf(0, pageCount - 1))
        jumpToken++
        showBookmarks = false
    }
    fun addBookmark(label: String, color: String) {
        val key = sheet?.filePath ?: return
        val bm = Bookmark(
            id = java.util.UUID.randomUUID().toString(),
            pdfFile = key, sheetName = sheet?.name ?: key, page = page + 1, label = label, color = color,
        )
        scope.launch { app.bookmarks.put(bm); bookmarks = app.bookmarks.forFile(key) }
        addingBookmark = false
    }
    fun deleteBookmark(bm: Bookmark) {
        val key = sheet?.filePath ?: return
        scope.launch { app.bookmarks.remove(bm.id); bookmarks = app.bookmarks.forFile(key) }
    }
    fun matchNav(delta: Int) {
        if (searchResults.isEmpty()) return
        searchIdx = (searchIdx + delta).coerceIn(0, searchResults.size - 1)
        jumpToPage(searchResults[searchIdx].page)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(sheet?.name ?: "") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Zurück")
                    }
                },
                actions = {
                    if (!editMode) {
                        Box {
                            IconButton(onClick = { showModeMenu = true }) {
                                Icon(Icons.Filled.MoreVert, contentDescription = "Ansicht")
                            }
                            DropdownMenu(expanded = showModeMenu, onDismissRequest = { showModeMenu = false }) {
                                for (m in DisplayMode.entries) {
                                    DropdownMenuItem(
                                        text = { Text(m.label) },
                                        onClick = { showModeMenu = false; chooseMode(m) },
                                        leadingIcon = { if (m == mode) Icon(Icons.Filled.Check, contentDescription = null) },
                                    )
                                }
                            }
                        }
                        TextButton(onClick = { showMetro = !showMetro }) {
                            Text(if (metro.running) "♩ ${metro.bpm} ▶" else "♩ ${metro.bpm}")
                        }
                        IconButton(onClick = { showSearch = true }) {
                            Icon(Icons.Filled.Search, contentDescription = "Suchen")
                        }
                        IconButton(onClick = { showBookmarks = true }) {
                            Icon(Icons.Filled.Bookmarks, contentDescription = "Lesezeichen")
                        }
                    }
                    IconButton(onClick = { if (editMode) exitEdit() else editMode = true }) {
                        Icon(Icons.Filled.Edit, contentDescription = "Annotieren")
                    }
                },
            )
        },
        bottomBar = {
            if (!editMode && (mode == DisplayMode.SINGLE || mode == DisplayMode.TWO) && pageCount > 1) {
                val step = if (mode == DisplayMode.TWO) 2 else 1
                BottomAppBar {
                    TextButton(onClick = { turn(-step) }, enabled = page > 0) { Text("‹") }
                    Spacer(Modifier.weight(1f))
                    Text("${page + 1} / $pageCount", Modifier.align(Alignment.CenterVertically))
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = { turn(step) }, enabled = page < pageCount - 1) { Text("›") }
                }
            }
        },
    ) { pad ->
        Box(Modifier.fillMaxSize().padding(pad)) {
            if (editMode) {
                EditablePage(
                    doc = doc, page = page, renderPx = renderPx,
                    data = annotations, tool = tool, color = inkColor,
                    sizeFrac = sizeFracFor(tool, sizeLevel), fill = fill,
                    onBeginStroke = { pushUndo() },
                    onAddPen = { addPen(it) },
                    onAddMarker = { addMarker(it) },
                    onAddShape = { addShape(it) },
                    onAddHairpin = { addHairpin(it) },
                    onEraseAt = { x, y -> eraseAt(x, y) },
                    onTapAt = { x, y -> onTapAt(x, y) },
                )
                Box(Modifier.align(Alignment.BottomCenter)) {
                    AnnotationToolbar(
                        tool = tool, color = inkColor, sizeLevel = sizeLevel, fill = fill, stampType = stampType,
                        onTool = { tool = it }, onColor = { inkColor = it }, onSize = { sizeLevel = it }, onFill = { fill = it },
                        onOpenStamps = { showStamps = true }, onUndo = { undoAnn() }, onDone = { exitEdit() },
                    )
                }
                if (showStamps) {
                    StampPickerDialog(
                        onPick = { id -> stampType = id; tool = Tool.STAMP; showStamps = false },
                        onDismiss = { showStamps = false },
                    )
                }
                pendingText?.let { pos ->
                    AlertDialog(
                        onDismissRequest = { pendingText = null },
                        confirmButton = { TextButton(onClick = { commitText(pos.x, pos.y, textInput); pendingText = null }) { Text("OK") } },
                        dismissButton = { TextButton(onClick = { pendingText = null }) { Text("Abbrechen") } },
                        title = { Text("Text") },
                        text = { OutlinedTextField(value = textInput, onValueChange = { textInput = it }, label = { Text("Notiz") }) },
                    )
                }
            } else {
                val key = sheet?.filePath
                when (mode) {
                    DisplayMode.SINGLE -> SinglePageView(doc, page, pageCount, renderPx, app.annotations, key, annoVersion) { turn(it) }
                    DisplayMode.TWO -> TwoPageView(doc, page, pageCount, renderPx, app.annotations, key, annoVersion) { turn(it) }
                    DisplayMode.HALF -> HalfTurnView(
                        doc, page, pageCount, renderPx, (sheet?.halfSplit ?: 0.5).toFloat(), jumpToken,
                    ) { s -> sheet?.let { sh -> scope.launch { app.library.update(sh.copy(halfSplit = s.toDouble())) } } }
                    DisplayMode.SCROLL_V -> VerticalScrollView(doc, pageCount, renderPx, page, app.annotations, key, annoVersion, jumpToken)
                    DisplayMode.SCROLL_H -> HorizontalScrollView(doc, pageCount, renderPx, page, app.annotations, key, annoVersion, jumpToken)
                }
                if (showMetro) {
                    Box(Modifier.align(Alignment.BottomCenter)) {
                        MetronomePanel(metro, onClose = { showMetro = false })
                    }
                }
            }

            if (showBookmarks) {
                BookmarkSheet(
                    outline = outline,
                    bookmarks = bookmarks,
                    onJump = { jumpToPage(it) },
                    onDelete = { deleteBookmark(it) },
                    onAddHere = { showBookmarks = false; addingBookmark = true },
                    onDismiss = { showBookmarks = false },
                )
            }
            if (addingBookmark) {
                BookmarkAddDialog(
                    page = page + 1,
                    onSave = { label, color -> addBookmark(label, color) },
                    onDismiss = { addingBookmark = false },
                )
            }

            if (showMatchNav && searchResults.isNotEmpty() && !editMode && !showSearch) {
                Surface(
                    Modifier.align(Alignment.TopCenter).padding(8.dp),
                    shape = RoundedCornerShape(20.dp),
                    tonalElevation = 4.dp,
                    shadowElevation = 6.dp,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { matchNav(-1) }, enabled = searchIdx > 0) { Text("‹") }
                        Text("Treffer ${searchIdx + 1}/${searchResults.size}", style = MaterialTheme.typography.labelLarge)
                        IconButton(onClick = { matchNav(1) }, enabled = searchIdx < searchResults.size - 1) { Text("›") }
                        IconButton(onClick = { showMatchNav = false }) { Icon(Icons.Filled.Close, contentDescription = "Schließen") }
                    }
                }
            }
            if (showSearch) {
                SearchSheet(
                    query = searchQuery,
                    onQueryChange = { searchQuery = it },
                    searching = searching,
                    results = searchResults,
                    onJump = { hit, i -> searchIdx = i; showMatchNav = true; showSearch = false; jumpToPage(hit.page) },
                    onDismiss = { showSearch = false },
                )
            }
        }
    }
}

private fun sizeFracFor(tool: Tool, level: Int): Float = when (tool) {
    Tool.PEN -> level * 0.0015f
    Tool.MARKER -> level * 0.006f
    Tool.CRESCENDO, Tool.DECRESCENDO, Tool.LINE, Tool.ARROW, Tool.RECT, Tool.ELLIPSE -> level * 0.0012f
    else -> level * 0.0015f
}

private fun stampFrac(level: Int): Float = 0.03f + level * 0.008f

private fun textFrac(level: Int): Float = 0.025f + level * 0.006f

/** Distance from a point to a segment, all in normalized [0,1] coords. */
private fun distToSeg(px: Float, py: Float, x1: Float, y1: Float, x2: Float, y2: Float): Float {
    val dx = x2 - x1
    val dy = y2 - y1
    val len2 = dx * dx + dy * dy
    if (len2 == 0f) return hypot(px - x1, py - y1)
    val t = (((px - x1) * dx + (py - y1) * dy) / len2).coerceIn(0f, 1f)
    return hypot(px - (x1 + t * dx), py - (y1 + t * dy))
}
