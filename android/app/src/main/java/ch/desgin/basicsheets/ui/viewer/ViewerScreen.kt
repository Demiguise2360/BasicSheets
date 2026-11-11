package ch.desgin.basicsheets.ui.viewer

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import ch.desgin.basicsheets.BasicSheetsApp
import ch.desgin.basicsheets.audio.Metronome
import ch.desgin.basicsheets.data.Sheet
import ch.desgin.basicsheets.pdf.PdfDoc
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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

    // Render at ~2x the screen width so zoom stays sharp.
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

    fun turn(delta: Int) { page = (page + delta).coerceIn(0, maxOf(0, pageCount - 1)) }
    fun chooseMode(m: DisplayMode) {
        mode = m
        sheet?.let { s -> scope.launch { app.library.update(s.copy(pageMode = m.key)) } }
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
                },
            )
        },
        bottomBar = {
            if (mode != DisplayMode.SCROLL_V && pageCount > 1) {
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
            when (mode) {
                DisplayMode.SINGLE -> SinglePageView(doc, page, pageCount, renderPx) { turn(it) }
                DisplayMode.TWO -> TwoPageView(doc, page, pageCount, renderPx) { turn(it) }
                DisplayMode.SCROLL_V -> VerticalScrollView(doc, pageCount, renderPx, page)
            }

            if (showMetro) {
                Box(Modifier.align(Alignment.BottomCenter)) {
                    MetronomePanel(metro, onClose = { showMetro = false })
                }
            }
        }
    }
}
