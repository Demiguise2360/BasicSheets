package ch.desgin.basicsheets.ui.viewer

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import ch.desgin.basicsheets.BasicSheetsApp
import ch.desgin.basicsheets.annot.PageAnnotations
import ch.desgin.basicsheets.annot.PenStroke
import ch.desgin.basicsheets.annot.Tool
import ch.desgin.basicsheets.audio.Metronome
import ch.desgin.basicsheets.data.Sheet
import ch.desgin.basicsheets.pdf.PdfDoc
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs

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
    var penColor by remember { mutableStateOf(Color.Red) }
    val undoStack = remember { mutableStateListOf<PageAnnotations>() }

    val renderPx = with(LocalDensity.current) { (LocalConfiguration.current.screenWidthDp.dp.toPx() * 2f).toInt() }

    LaunchedEffect(sheetId) {
        val s = app.library.byId(sheetId) ?: return@LaunchedEffect
        sheet = s
        mode = DisplayMode.from(s.pageMode)
        val d = withContext(Dispatchers.IO) { PdfDoc.open(app.library.fileFor(s)) }
        doc = d
        pageCount = d?.pageCount ?: 0
        page = 0
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

    fun turn(delta: Int) { page = (page + delta).coerceIn(0, maxOf(0, pageCount - 1)) }
    fun chooseMode(m: DisplayMode) {
        mode = m
        sheet?.let { s -> scope.launch { app.library.update(s.copy(pageMode = m.key)) } }
    }
    fun saveAnn() {
        val key = sheet?.filePath ?: return
        val p = page
        val data = annotations
        scope.launch { app.annotations.save(key, p, data) }
    }
    fun beginStroke() {
        undoStack.add(annotations)
        if (undoStack.size > 40) undoStack.removeAt(0)
    }
    fun addStroke(s: PenStroke) {
        annotations = annotations.copy(penStrokes = annotations.penStrokes + s)
        saveAnn()
    }
    fun eraseAt(nx: Float, ny: Float) {
        val r = 0.025f
        val kept = annotations.penStrokes.filter { st -> st.points.none { abs(it.x - nx) < r && abs(it.y - ny) < r } }
        if (kept.size != annotations.penStrokes.size) {
            annotations = annotations.copy(penStrokes = kept)
            saveAnn()
        }
    }
    fun undoAnn() {
        if (undoStack.isNotEmpty()) {
            annotations = undoStack.removeAt(undoStack.size - 1)
            saveAnn()
        }
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
                    }
                    IconButton(onClick = { editMode = !editMode }) {
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
                    data = annotations, tool = tool, color = penColor, strokeWidth = 0.005f,
                    onBeginStroke = { beginStroke() },
                    onAddStroke = { addStroke(it) },
                    onErase = { x, y -> eraseAt(x, y) },
                )
                Box(Modifier.align(Alignment.BottomCenter)) {
                    AnnotationToolbar(
                        tool = tool, color = penColor,
                        onTool = { tool = it }, onColor = { penColor = it },
                        onUndo = { undoAnn() }, onDone = { editMode = false },
                    )
                }
            } else {
                when (mode) {
                    DisplayMode.SINGLE -> SinglePageView(doc, page, pageCount, renderPx) { turn(it) }
                    DisplayMode.TWO -> TwoPageView(doc, page, pageCount, renderPx) { turn(it) }
                    DisplayMode.HALF -> HalfTurnView(
                        doc, page, pageCount, renderPx, (sheet?.halfSplit ?: 0.5).toFloat(),
                    ) { s -> sheet?.let { sh -> scope.launch { app.library.update(sh.copy(halfSplit = s.toDouble())) } } }
                    DisplayMode.SCROLL_V -> VerticalScrollView(doc, pageCount, renderPx, page)
                    DisplayMode.SCROLL_H -> HorizontalScrollView(doc, pageCount, renderPx, page)
                }
                if (showMetro) {
                    Box(Modifier.align(Alignment.BottomCenter)) {
                        MetronomePanel(metro, onClose = { showMetro = false })
                    }
                }
            }
        }
    }
}
