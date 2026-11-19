package ch.desgin.basicsheets.ui.viewer

import android.content.Context
import android.graphics.Bitmap
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.focusable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bookmarks
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.nativeKeyCode
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import ch.desgin.basicsheets.BasicSheetsApp
import ch.desgin.basicsheets.data.Crop
import ch.desgin.basicsheets.data.ViewerButton
import ch.desgin.basicsheets.ui.theme.appTopBarColors
import ch.desgin.basicsheets.annot.Hairpin
import ch.desgin.basicsheets.annot.MarkerStroke
import ch.desgin.basicsheets.annot.PageAnnotations
import ch.desgin.basicsheets.annot.PenStroke
import ch.desgin.basicsheets.annot.ShapeAnnot
import ch.desgin.basicsheets.annot.Stamp
import ch.desgin.basicsheets.annot.TextAnnot
import ch.desgin.basicsheets.annot.Tool
import ch.desgin.basicsheets.audio.AudioController
import ch.desgin.basicsheets.audio.Metronome
import ch.desgin.basicsheets.audio.TuningTone
import ch.desgin.basicsheets.data.Bookmark
import ch.desgin.basicsheets.data.Sheet
import ch.desgin.basicsheets.pdf.MatchBox
import ch.desgin.basicsheets.pdf.OutlineEntry
import ch.desgin.basicsheets.pdf.PdfDoc
import ch.desgin.basicsheets.pdf.PdfOutline
import ch.desgin.basicsheets.pdf.PdfTextIndex
import ch.desgin.basicsheets.pdf.SearchHit
import ch.desgin.basicsheets.ui.settings.LocalSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.pow

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ViewerScreen(
    sheetId: Int,
    onBack: () -> Unit,
    setlistId: Int = -1,
    onOpenSong: (Int) -> Unit = {},
) {
    val app = BasicSheetsApp.instance
    val scope = rememberCoroutineScope()
    val settings = LocalSettings.current
    val view = LocalView.current

    var sheet by remember { mutableStateOf<Sheet?>(null) }
    var doc by remember { mutableStateOf<PdfDoc?>(null) }
    var pageCount by remember { mutableIntStateOf(0) }
    var page by remember { mutableIntStateOf(0) }
    var mode by remember { mutableStateOf(DisplayMode.SINGLE) }
    var autoScroll by remember { mutableStateOf(false) }
    var autoTurn by remember { mutableStateOf(false) }
    var turnInterval by remember { mutableIntStateOf(20) }
    val focusRequester = remember { FocusRequester() }
    var linkPoints by remember { mutableStateOf<List<ViewerButton>>(emptyList()) }
    var editLinks by remember { mutableStateOf(false) }
    var showLinkList by remember { mutableStateOf(false) }
    var pendingLink by remember { mutableStateOf<Offset?>(null) }
    var linkTargetInput by remember { mutableStateOf("") }
    var linkColorPick by remember { mutableStateOf(LINK_COLORS.first()) }
    // Persisted crops, keyed by 1-based page; key 0 = a crop that applies to all pages (fallback).
    var pageCrops by remember { mutableStateOf<Map<Int, Rect>>(emptyMap()) }
    // Edit buffer for the crop panel (the page being adjusted); cropAllPages saves it for every page.
    var cropL by remember { mutableFloatStateOf(0f) }
    var cropT by remember { mutableFloatStateOf(0f) }
    var cropR by remember { mutableFloatStateOf(1f) }
    var cropB by remember { mutableFloatStateOf(1f) }
    var cropAllPages by remember { mutableStateOf(false) }
    var showCrop by remember { mutableStateOf(false) }

    val metro = remember { Metronome() }
    var showMetro by remember { mutableStateOf(false) }
    var showModeMenu by remember { mutableStateOf(false) }
    DisposableEffect(Unit) { onDispose { metro.release() } }

    // Annotation edit state
    var editMode by remember { mutableStateOf(false) }
    var hideAnnotations by remember { mutableStateOf(false) }   // clean-page study view (markers/highlights stay)
    var annotations by remember { mutableStateOf(PageAnnotations()) }
    var tool by remember { mutableStateOf(Tool.PEN) }
    var inkColor by remember { mutableStateOf(Color.Red) }
    var sizeLevel by remember { mutableIntStateOf(4) }
    var fill by remember { mutableStateOf(false) }
    var eraserBox by remember { mutableStateOf(false) }
    var stampType by remember { mutableStateOf("flat") }
    var showStamps by remember { mutableStateOf(false) }
    var pendingText by remember { mutableStateOf<Offset?>(null) }
    var textInput by remember { mutableStateOf("") }
    var annoVersion by remember { mutableIntStateOf(0) }   // bumped on edit exit → reading overlays reload
    val undoStack = remember { mutableStateListOf<PageAnnotations>() }
    val redoStack = remember { mutableStateListOf<PageAnnotations>() }

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
    var searchBoxes by remember { mutableStateOf<Map<Int, List<MatchBox>>>(emptyMap()) }
    val textIndex = remember { PdfTextIndex() }

    // Page-grid overview
    var showGrid by remember { mutableStateOf(false) }
    var annotatedPages by remember { mutableStateOf<Set<Int>>(emptySet()) }

    // Go-to-page: tap the bottom-bar page readout to jump to a page number.
    var showPageInput by remember { mutableStateOf(false) }
    var pageInput by remember { mutableStateOf("") }

    // Custom page order (0-based PDF indices in display order; natural when no override)
    var order by remember { mutableStateOf<List<Int>>(emptyList()) }
    var showPageOrder by remember { mutableStateOf(false) }
    val pdfPage = order.getOrElse(page) { page }
    val isTextSong = sheet?.let { PdfDoc.isText(it.filePath) } ?: false

    // Setlist playback context (song-to-song navigation)
    var setlistSheets by remember { mutableStateOf<List<Sheet>>(emptyList()) }
    val songIdx = if (setlistId >= 0) setlistSheets.indexOfFirst { it.id == sheetId } else -1

    // Backing track + tuning tone
    val context = LocalContext.current
    val pageExport = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("image/png")) { uri ->
        if (uri != null) scope.launch {
            val key = sheet?.filePath
            val anno = if (key != null) app.annotations.load(key, pdfPage) else PageAnnotations()
            val bmp = renderPageForExport(doc, pdfPage, anno)
            val ok = bmp?.let { b ->
                withContext(Dispatchers.IO) {
                    runCatching { context.contentResolver.openOutputStream(uri)?.use { b.compress(Bitmap.CompressFormat.PNG, 100, it) }; true }.getOrDefault(false)
                }
            } ?: false
            Toast.makeText(context, if (ok) "Seite gespeichert" else "Export fehlgeschlagen", Toast.LENGTH_SHORT).show()
        }
    }
    val audioCtl = remember { AudioController(context) }
    val tone = remember { TuningTone() }
    var showAudio by remember { mutableStateOf(false) }
    var tonePlaying by remember { mutableStateOf(false) }
    var toneMidi by remember { mutableIntStateOf(69) }   // A4 = 440 Hz
    val audioFile = sheet?.let { app.library.audioFileFor(it) }
    val audioPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch { sheet?.let { s -> app.library.attachAudio(context, uri, s)?.let { sheet = it } } }
    }
    DisposableEffect(Unit) { onDispose { audioCtl.release(); tone.release() } }
    DisposableEffect(settings.keepScreenOn) {
        view.keepScreenOn = settings.keepScreenOn
        onDispose { view.keepScreenOn = false }
    }

    // Remember the backing-track position per audio file so reopening resumes where you left off.
    fun audioPosKey(): String? = sheet?.audioPath?.takeIf { it.isNotBlank() }?.let { "audioPos:$it" }
    fun saveAudioPos() {
        val key = audioPosKey() ?: return
        val pos = audioCtl.positionMs
        if (pos > 1000) scope.launch { app.settings.set(key, pos.toString()) }
    }

    val renderPx = with(LocalDensity.current) { (LocalConfiguration.current.screenWidthDp.dp.toPx() * 2f).toInt() }

    LaunchedEffect(sheetId) {
        val s = app.library.byId(sheetId) ?: return@LaunchedEffect
        sheet = s
        app.library.markOpened(sheetId)   // record for the Zuletzt tab (doesn't touch `updated`)
        bookmarks = app.bookmarks.forFile(s.filePath)
        mode = DisplayMode.from(s.pageMode ?: settings.pageMode)
        metro.bpm = if (s.bpm > 0) s.bpm else 100
        metro.beatsPerBar = if (s.beats > 0) s.beats else 4
        // A multi-file song concatenates its parts; a plain song opens its single file (PDF or image).
        val parts = withContext(Dispatchers.IO) { runCatching { app.library.partFiles(s) }.getOrDefault(emptyList()) }
        val d = withContext(Dispatchers.IO) {
            when {
                PdfDoc.isText(s.filePath) -> null                 // text / ChordPro songs render as text, not pages
                parts.size > 1 -> PdfDoc.openFiles(parts)
                else -> PdfDoc.open(app.library.fileFor(s))
            }
        }
        doc = d
        pageCount = d?.pageCount ?: 0
        // A page-range song shows only [pageStart..pageEnd] of a shared PDF (e.g. one piece in a
        // collection); a custom page order, when set, takes precedence over the range.
        order = if (s.hasRange && s.pageOrder.isBlank() && pageCount > 0) {
            val a = (s.pageStart - 1).coerceIn(0, pageCount - 1)
            val b = (s.pageEnd - 1).coerceIn(a, pageCount - 1)
            (a..b).toList()
        } else {
            parsePageOrder(s.pageOrder, pageCount)
        }
        page = 0
        outline = PdfOutline.extract(app.library.fileFor(s))
    }
    DisposableEffect(doc) {
        val current = doc   // capture: close THIS doc on dispose, not whatever `doc` is later
        onDispose { current?.close() }
    }

    // Load the page's annotations when entering edit mode or turning pages while editing.
    LaunchedEffect(editMode, page, sheet?.filePath) {
        val key = sheet?.filePath
        if (editMode && !key.isNullOrEmpty()) {
            annotations = app.annotations.load(key, order.getOrElse(page) { page })
            undoStack.clear()
            redoStack.clear()
        }
    }

    // Debounced in-document search while the search sheet is open.
    LaunchedEffect(searchQuery, showSearch) {
        if (!showSearch) return@LaunchedEffect
        showMatchNav = false
        searchIdx = -1
        val q = searchQuery.trim()
        if (q.length < 2) { searchResults = emptyList(); searchBoxes = emptyMap(); searching = false; return@LaunchedEffect }
        searching = true
        delay(300)
        val s = sheet
        if (s != null) {
            // Guard the whole PDF-text path: image / multi-file songs have no extractable text, and
            // a parse failure must not crash the search coroutine — just show no results.
            runCatching {
                textIndex.ensure(app.library.fileFor(s), s.filePath)
                val hits = textIndex.search(q)
                searchResults = hits
                searchBoxes = textIndex.locateAll(q, hits.map { it.page }.toSet())
            }.onFailure { searchResults = emptyList(); searchBoxes = emptyMap() }
        }
        searching = false
    }

    LaunchedEffect(setlistId) {
        setlistSheets = if (setlistId >= 0) app.setlists.sheets(setlistId) else emptyList()
    }

    LaunchedEffect(sheet?.audioPath) {
        val f = sheet?.let { app.library.audioFileFor(it) }
        if (f != null) {
            audioCtl.load(f)
            val key = audioPosKey()
            val saved = if (key != null) app.settings.get(key, "0").toLongOrNull() ?: 0L else 0L
            if (saved > 1000) audioCtl.seekTo(saved)
        } else {
            audioCtl.clear()
        }
    }
    LaunchedEffect(audioCtl.isPlaying) {
        var ticks = 0
        while (audioCtl.isPlaying) {
            audioCtl.tick()
            delay(200)
            if (++ticks >= 15) { ticks = 0; saveAudioPos() }   // persist ~every 3s while playing
        }
        saveAudioPos()   // and once more on pause / stop
    }

    LaunchedEffect(showGrid, sheet?.filePath) {
        val key = sheet?.filePath
        if (showGrid && !key.isNullOrEmpty()) annotatedPages = app.annotations.annotatedPages(key)
    }
    LaunchedEffect(sheet?.filePath) {
        val key = sheet?.filePath
        linkPoints = if (!key.isNullOrEmpty()) runCatching { app.db.viewerButtons().forFile(key) }.getOrDefault(emptyList()) else emptyList()
    }
    LaunchedEffect(sheet?.id) {
        val rows = sheet?.id?.let { runCatching { app.db.crops().forSheet(it) }.getOrNull() } ?: emptyList()
        pageCrops = rows.associate { it.page to Rect(it.l.toFloat(), it.t.toFloat(), it.r.toFloat(), it.b.toFloat()) }
    }
    // While the crop panel is open, load the shown page's crop into the edit buffer (so paging
    // through pages lets you crop each one); page 0 is the all-pages fallback.
    LaunchedEffect(pdfPage, showCrop) {
        if (showCrop) {
            val cur = pageCrops[pdfPage + 1] ?: pageCrops[0] ?: FULL_CROP
            cropL = cur.left; cropT = cur.top; cropR = cur.right; cropB = cur.bottom
        }
    }

    val vibrator = remember(context) { context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator }
    fun doHaptic() {
        if (!settings.haptics) return
        val v = vibrator ?: return
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                v.vibrate(VibrationEffect.createOneShot(12L, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION") v.vibrate(12L)
            }
        }
    }
    // Manual turns (tap, nav buttons, hardware keys) pass haptic = true; auto-turn / measure-turn don't.
    fun turn(delta: Int, haptic: Boolean = false) {
        if (haptic) doHaptic()
        page = (page + delta).coerceIn(0, maxOf(0, order.size - 1))
    }
    fun chooseMode(m: DisplayMode) {
        mode = m
        autoScroll = false
        autoTurn = false
        sheet?.let { s -> scope.launch { app.library.update(s.copy(pageMode = m.key)) } }
    }
    fun setAutoSpeed(delta: Int) {
        val cur = (sheet?.scrollSpeed ?: 0).let { if (it > 0) it else 50 }
        val next = (cur + delta).coerceIn(10, 400)
        sheet?.let { s -> sheet = s.copy(scrollSpeed = next); scope.launch { app.library.update(s.copy(scrollSpeed = next)) } }
    }
    fun exitEdit() { editMode = false; annoVersion++ }
    fun saveAnn() {
        val key = sheet?.filePath ?: return
        val p = order.getOrElse(page) { page }
        val data = annotations
        scope.launch { app.annotations.save(key, p, data) }
    }
    fun pushUndo() {
        undoStack.add(annotations)
        redoStack.clear()
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
    fun eraseInBox(l: Float, t: Float, rr: Float, bb: Float) {
        val x0 = minOf(l, rr); val x1 = maxOf(l, rr); val y0 = minOf(t, bb); val y1 = maxOf(t, bb)
        fun inBox(x: Float, y: Float) = x in x0..x1 && y in y0..y1
        fun anyIn(pts: List<ch.desgin.basicsheets.annot.Pt>) = pts.any { inBox(it.x, it.y) }
        val before = annotations
        val next = annotations.copy(
            penStrokes = annotations.penStrokes.filterNot { anyIn(it.points) },
            markerData = annotations.markerData.filterNot { anyIn(it.points) },
            shapeData = annotations.shapeData.filterNot { inBox(it.x1, it.y1) || inBox(it.x2, it.y2) },
            hairpinData = annotations.hairpinData.filterNot { inBox(it.x1, it.y1) || inBox(it.x2, it.y2) },
            stampData = annotations.stampData.filterNot { inBox(it.x, it.y) },
            textData = annotations.textData.filterNot { inBox(it.x, it.y) },
        )
        if (next != before) { annotations = next; saveAnn() }
    }
    fun undoAnn() {
        if (undoStack.isNotEmpty()) { redoStack.add(annotations); annotations = undoStack.removeAt(undoStack.size - 1); saveAnn() }
    }
    fun redoAnn() {
        if (redoStack.isNotEmpty()) { undoStack.add(annotations); annotations = redoStack.removeAt(redoStack.size - 1); saveAnn() }
    }
    fun jumpToDisplay(target: Int) {
        page = target.coerceIn(0, maxOf(0, order.size - 1))
        jumpToken++
        showBookmarks = false
    }
    fun jumpToPdf(pdf: Int) {
        val d = order.indexOf(pdf)
        jumpToDisplay(if (d >= 0) d else 0)
    }
    fun addBookmark(label: String, color: String) {
        val key = sheet?.filePath ?: return
        val bm = Bookmark(
            id = java.util.UUID.randomUUID().toString(),
            pdfFile = key, sheetName = sheet?.name ?: key, page = pdfPage + 1, label = label, color = color,
        )
        scope.launch { app.bookmarks.put(bm); bookmarks = app.bookmarks.forFile(key) }
        addingBookmark = false
    }
    fun deleteBookmark(bm: Bookmark) {
        val key = sheet?.filePath ?: return
        scope.launch { app.bookmarks.remove(bm.id); bookmarks = app.bookmarks.forFile(key) }
    }
    fun saveLink(nx: Float, ny: Float, targetPage: Int, color: String) {
        val key = sheet?.filePath ?: return
        val vb = ViewerButton(
            id = java.util.UUID.randomUUID().toString(), action = "jump", pdfFile = key,
            sourcePage = pdfPage + 1, sourceNx = nx.toDouble(), sourceNy = ny.toDouble(), targetPage = targetPage,
            color = color,
        )
        scope.launch { app.db.viewerButtons().insert(vb); linkPoints = app.db.viewerButtons().forFile(key) }
    }
    fun deleteLink(vb: ViewerButton) {
        val key = sheet?.filePath ?: return
        scope.launch { app.db.viewerButtons().remove(vb.id); linkPoints = app.db.viewerButtons().forFile(key) }
    }
    fun jumpLink(vb: ViewerButton) { jumpToPdf((vb.targetPage - 1).coerceAtLeast(0)) }
    fun rotatePage() {
        val s = sheet ?: return
        val next = (s.rotation + 90) % 360
        sheet = s.copy(rotation = next)
        scope.launch { app.library.update(s.copy(rotation = next)) }
    }
    fun storedCropFor(pdf0: Int): Rect = pageCrops[pdf0 + 1] ?: pageCrops[0] ?: FULL_CROP
    fun openCrop() {
        if (mode != DisplayMode.SINGLE) chooseMode(DisplayMode.SINGLE)
        val cur = storedCropFor(pdfPage)
        cropL = cur.left; cropT = cur.top; cropR = cur.right; cropB = cur.bottom
        cropAllPages = false
        showCrop = true   // the buffer-init effect also refreshes cropL..cropB as you page through
    }
    fun saveCrop() {
        val id = sheet?.id ?: return
        val isCropped = cropL > 0.002f || cropT > 0.002f || cropR < 0.998f || cropB < 0.998f
        val rect = Rect(cropL, cropT, cropR, cropB)
        if (cropAllPages) {
            // One crop for the whole song: drop every per-page row and keep only the page-0 fallback.
            scope.launch {
                val existing = runCatching { app.db.crops().forSheet(id) }.getOrDefault(emptyList())
                for (c in existing) app.db.crops().clear(id, c.page)
                if (isCropped) app.db.crops().insert(Crop(sheetId = id, page = 0, l = cropL.toDouble(), t = cropT.toDouble(), r = cropR.toDouble(), b = cropB.toDouble()))
            }
            pageCrops = if (isCropped) mapOf(0 to rect) else emptyMap()
        } else {
            // This page only (1-based); leave the all-pages fallback and other pages untouched.
            val pg = pdfPage + 1
            scope.launch {
                app.db.crops().clear(id, pg)
                if (isCropped) app.db.crops().insert(Crop(sheetId = id, page = pg, l = cropL.toDouble(), t = cropT.toDouble(), r = cropR.toDouble(), b = cropB.toDouble()))
            }
            pageCrops = pageCrops.toMutableMap().apply { if (isCropped) put(pg, rect) else remove(pg) }
        }
        showCrop = false
    }
    fun matchNav(delta: Int) {
        if (searchResults.isEmpty()) return
        searchIdx = (searchIdx + delta).coerceIn(0, searchResults.size - 1)
        jumpToPdf(searchResults[searchIdx].page)
    }
    fun toggleTone() {
        if (tonePlaying) { tone.stop(); tonePlaying = false }
        else { tone.start(toneFreq(toneMidi)); tonePlaying = true }
    }
    fun shiftTone(d: Int) {
        toneMidi = (toneMidi + d).coerceIn(33, 96)
        if (tonePlaying) tone.setFreq(toneFreq(toneMidi))
    }

    // Grab focus for hardware page-turn keys (keyboards, Bluetooth page pedals), re-grabbing it
    // whenever we return to plain reading (so an open search/text field keeps its own keys).
    LaunchedEffect(showSearch, pendingText, editMode) {
        if (!showSearch && pendingText == null && !editMode) runCatching { focusRequester.requestFocus() }
    }
    // Metronome auto page-turn: advance the page every N measures (paged modes only, like the
    // timed auto-turn). The stored lambda reads the live mode/page/order at fire time.
    LaunchedEffect(Unit) {
        metro.onBarsElapsed = {
            if (mode != DisplayMode.SCROLL_V && mode != DisplayMode.SCROLL_H) {
                val step = if (mode == DisplayMode.TWO) 2 else 1
                if (page < order.size - 1) turn(step)
            }
        }
    }
    // Timed hands-free page turning in the paged modes (auto-scroll covers the scroll modes).
    LaunchedEffect(autoTurn, turnInterval, mode) {
        if (!autoTurn || mode == DisplayMode.SCROLL_V || mode == DisplayMode.SCROLL_H) return@LaunchedEffect
        val step = if (mode == DisplayMode.TWO) 2 else 1
        while (true) {
            delay(turnInterval * 1000L)
            if (page >= order.size - 1) { autoTurn = false; break }
            turn(step)
        }
    }

    Scaffold(
        modifier = Modifier
            .focusRequester(focusRequester)
            .focusable()
            .onKeyEvent { ev ->
                if (editMode) return@onKeyEvent false
                val isVol = ev.key == Key.VolumeDown || ev.key == Key.VolumeUp
                if (isVol && !settings.volumeKeysPaging) return@onKeyEvent false
                // Consume the volume key-up too (when paging is on) so the system volume UI stays hidden.
                if (ev.type != KeyEventType.KeyDown) return@onKeyEvent isVol
                val step = if (mode == DisplayMode.TWO) 2 else 1
                // User-bound extra keys (e.g. a Bluetooth pedal) take priority over the built-ins.
                val code = ev.key.nativeKeyCode
                if (settings.nextKeyCode != 0 && code == settings.nextKeyCode) { turn(step, haptic = true); return@onKeyEvent true }
                if (settings.prevKeyCode != 0 && code == settings.prevKeyCode) { turn(-step, haptic = true); return@onKeyEvent true }
                when (ev.key) {
                    Key.PageDown, Key.DirectionRight, Key.MediaNext, Key.Spacebar -> { turn(step, haptic = true); true }
                    Key.PageUp, Key.DirectionLeft, Key.MediaPrevious -> { turn(-step, haptic = true); true }
                    Key.VolumeDown -> { turn(if (settings.volumeUpForward) -step else step, haptic = true); true }
                    Key.VolumeUp -> { turn(if (settings.volumeUpForward) step else -step, haptic = true); true }
                    else -> false
                }
            },
        topBar = {
            TopAppBar(
                title = { Text(sheet?.name ?: "") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Zurück")
                    }
                },
                actions = {
                    if (!isTextSong && !editMode) {
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
                                HorizontalDivider()
                                DropdownMenuItem(
                                    text = { Text("Seitenübersicht") },
                                    onClick = { showModeMenu = false; showGrid = true },
                                )
                                DropdownMenuItem(
                                    text = { Text("Seitenreihenfolge") },
                                    onClick = { showModeMenu = false; showPageOrder = true },
                                )
                                DropdownMenuItem(
                                    text = { Text("Seite als Bild") },
                                    onClick = { showModeMenu = false; pageExport.launch("${(sheet?.name ?: "Seite").replace("/", "_")}_S${pdfPage + 1}.png") },
                                )
                                DropdownMenuItem(
                                    text = { Text(if (editLinks) "Sprungpunkte fertig" else "Sprungpunkte bearbeiten") },
                                    onClick = { showModeMenu = false; editLinks = !editLinks; if (editLinks && mode != DisplayMode.SINGLE) chooseMode(DisplayMode.SINGLE) },
                                )
                                DropdownMenuItem(
                                    text = { Text("Sprungpunkte verwalten") },
                                    onClick = { showModeMenu = false; showLinkList = true },
                                )
                                DropdownMenuItem(
                                    text = { Text("Zuschneiden") },
                                    onClick = { showModeMenu = false; openCrop() },
                                )
                                DropdownMenuItem(
                                    text = { Text("Drehen") },
                                    onClick = { showModeMenu = false; rotatePage() },
                                )
                                DropdownMenuItem(
                                    text = { Text(if (hideAnnotations) "Notizen anzeigen" else "Notizen ausblenden") },
                                    onClick = { showModeMenu = false; hideAnnotations = !hideAnnotations },
                                )
                            }
                        }
                        TextButton(
                            onClick = { showMetro = !showMetro; showAudio = false },
                            colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onPrimary),
                        ) {
                            Text(if (metro.running) "♩▶" else "♩")
                        }
                        IconButton(onClick = { showAudio = true; showMetro = false }) {
                            Icon(Icons.Filled.MusicNote, contentDescription = "Ton & Audio")
                        }
                        IconButton(onClick = { showSearch = true }) {
                            Icon(Icons.Filled.Search, contentDescription = "Suchen")
                        }
                        IconButton(onClick = { showBookmarks = true }) {
                            Icon(Icons.Filled.Bookmarks, contentDescription = "Lesezeichen")
                        }
                    }
                    if (!isTextSong) {
                        IconButton(onClick = { if (editMode) exitEdit() else editMode = true }) {
                            Icon(Icons.Filled.Edit, contentDescription = "Annotieren")
                        }
                    }
                },
                colors = appTopBarColors(),
            )
        },
        bottomBar = {
            val inSetlist = setlistId >= 0 && songIdx >= 0
            val showPageNav = (mode == DisplayMode.SINGLE || mode == DisplayMode.TWO) && order.size > 1
            if (!editMode && !isTextSong && (inSetlist || showPageNav)) {
                val step = if (mode == DisplayMode.TWO) 2 else 1
                val navBtn = ButtonDefaults.textButtonColors(
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    disabledContentColor = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.38f),
                )
                BottomAppBar(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                ) {
                    if (inSetlist) {
                        TextButton(onClick = { onOpenSong(setlistSheets[songIdx - 1].id) }, enabled = songIdx > 0, colors = navBtn) { Text("⏮") }
                    }
                    Spacer(Modifier.weight(1f))
                    if (showPageNav) {
                        TextButton(onClick = { turn(-step, haptic = true) }, enabled = page > 0, colors = navBtn) { Text("‹") }
                        if (settings.pageNumbers) {
                            Text(
                                "${page + 1} / ${order.size}",
                                Modifier
                                    .align(Alignment.CenterVertically)
                                    .clickable { pageInput = "${page + 1}"; showPageInput = true }
                                    .padding(horizontal = 10.dp, vertical = 6.dp),
                            )
                        }
                        TextButton(onClick = { turn(step, haptic = true) }, enabled = page < order.size - 1, colors = navBtn) { Text("›") }
                    } else if (inSetlist) {
                        Text("Stück ${songIdx + 1} / ${setlistSheets.size}", Modifier.align(Alignment.CenterVertically))
                    }
                    Spacer(Modifier.weight(1f))
                    if (inSetlist) {
                        TextButton(onClick = { onOpenSong(setlistSheets[songIdx + 1].id) }, enabled = songIdx < setlistSheets.size - 1, colors = navBtn) { Text("⏭") }
                    }
                }
            }
        },
    ) { pad ->
        Box(Modifier.fillMaxSize().background(viewerBgColor(settings.viewerBg)).padding(pad)) {
            if (editMode) {
                EditablePage(
                    doc = doc, page = pdfPage, renderPx = renderPx,
                    data = annotations, tool = tool, color = inkColor,
                    sizeFrac = sizeFracFor(tool, sizeLevel), fill = fill,
                    onBeginStroke = { pushUndo() },
                    onAddPen = { addPen(it) },
                    onAddMarker = { addMarker(it) },
                    onAddShape = { addShape(it) },
                    onAddHairpin = { addHairpin(it) },
                    onEraseAt = { x, y -> eraseAt(x, y) },
                    eraserBox = eraserBox,
                    onEraseBox = { l, t, r, b -> eraseInBox(l, t, r, b) },
                    onTapAt = { x, y -> onTapAt(x, y) },
                    palmReject = settings.palmRejection,
                    penButtonErase = settings.penButtonErase,
                )
                if (settings.floatingAnnotTools) {
                    AnnotationPalette(
                        tool = tool, color = inkColor, sizeLevel = sizeLevel, fill = fill, eraserBox = eraserBox,
                        onTool = { tool = it }, onColor = { inkColor = it }, onSize = { sizeLevel = it }, onFill = { fill = it }, onEraserBox = { eraserBox = it },
                        onOpenStamps = { showStamps = true }, onUndo = { undoAnn() }, onRedo = { redoAnn() }, onDone = { exitEdit() },
                        modifier = Modifier.align(Alignment.TopStart).padding(12.dp),
                    )
                } else {
                    Box(Modifier.align(Alignment.BottomCenter)) {
                        AnnotationToolbar(
                            tool = tool, color = inkColor, sizeLevel = sizeLevel, fill = fill, eraserBox = eraserBox, stampType = stampType,
                            onTool = { tool = it }, onColor = { inkColor = it }, onSize = { sizeLevel = it }, onFill = { fill = it }, onEraserBox = { eraserBox = it },
                            onOpenStamps = { showStamps = true }, onUndo = { undoAnn() }, onRedo = { redoAnn() }, onDone = { exitEdit() },
                        )
                    }
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
            } else if (isTextSong) {
                TextSongContent(sheet?.let { app.library.fileFor(it) }, Modifier.fillMaxSize())
            } else {
                val key = sheet?.filePath
                val hlPage = if (showMatchNav && searchIdx in searchResults.indices) searchResults[searchIdx].page else -1
                val hlBoxes = if (hlPage >= 0) searchBoxes[hlPage].orEmpty() else emptyList()
                // While "Zuschneiden" is open, the full-page crop-box editor replaces the normal view;
                // otherwise each mode applies the stored per-page crop.
                val singleCrop = storedCropFor(pdfPage)
                val cropFor: (Int) -> Rect = { pdf -> storedCropFor(pdf) }
                // "Notizen ausblenden": feed an empty annotation source so no marks draw (markers/highlights still do).
                val annoRepo = if (hideAnnotations) null else app.annotations
                if (showCrop) {
                    CropEditor(doc, pdfPage, renderPx, cropL, cropT, cropR, cropB) { l, t, r, b -> cropL = l; cropT = t; cropR = r; cropB = b }
                } else when (mode) {
                    DisplayMode.SINGLE -> SinglePageView(
                        doc, page, order, renderPx, annoRepo, key, annoVersion, hlPage, hlBoxes,
                        linkPoints.filter { it.sourcePage - 1 == pdfPage && it.sourceNx >= 0 },
                        editLinks,
                        onMarkerTap = { vb -> if (editLinks) deleteLink(vb) else jumpLink(vb) },
                        onEmptyTap = { nx, ny -> pendingLink = Offset(nx, ny); linkTargetInput = "${pdfPage + 1}" },
                        cropL = singleCrop.left, cropT = singleCrop.top, cropR = singleCrop.right, cropB = singleCrop.bottom,
                        rotation = sheet?.rotation ?: 0,
                    ) { turn(it, haptic = true) }
                    DisplayMode.TWO -> TwoPageView(
                        doc, page, order, renderPx, annoRepo, key, annoVersion, hlPage, hlBoxes,
                        cropFor, sheet?.rotation ?: 0,
                        linkPoints = linkPoints, onMarkerTap = { jumpLink(it) },
                    ) { turn(it, haptic = true) }
                    DisplayMode.HALF -> HalfTurnView(
                        doc, page, order, renderPx, (sheet?.halfSplit ?: 0.5).toFloat(), jumpToken,
                        annoRepo, key, annoVersion, hlPage, hlBoxes,
                        cropFor,
                        rotation = sheet?.rotation ?: 0,
                        linkPoints = linkPoints, onMarkerTap = { jumpLink(it) },
                    ) { s -> sheet?.let { sh -> scope.launch { app.library.update(sh.copy(halfSplit = s.toDouble())) } } }
                    DisplayMode.SCROLL_V -> VerticalScrollView(doc, order, renderPx, page, annoRepo, key, annoVersion, jumpToken, hlPage, hlBoxes, autoScroll, sheet?.scrollSpeed ?: 0, cropFor, sheet?.rotation ?: 0, linkPoints = linkPoints, onMarkerTap = { jumpLink(it) })
                    DisplayMode.SCROLL_H -> HorizontalScrollView(doc, order, renderPx, page, annoRepo, key, annoVersion, jumpToken, hlPage, hlBoxes, autoScroll, sheet?.scrollSpeed ?: 0, cropFor, sheet?.rotation ?: 0, linkPoints = linkPoints, onMarkerTap = { jumpLink(it) })
                }
                // Reading-comfort overlays over the page; drawn before the panels so chrome stays bright,
                // and with no pointer input so page-turn taps pass straight through.
                if (settings.warmTint) {
                    Box(Modifier.fillMaxSize().background(Color(0xFFFF8F00).copy(alpha = 0.16f)))
                }
                if (settings.dimLevel > 0f) {
                    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = settings.dimLevel)))
                }
                if (showMetro) {
                    Box(Modifier.align(Alignment.BottomCenter)) {
                        MetronomePanel(
                            metro,
                            onClose = { showMetro = false },
                            onTempoChange = {
                                sheet?.let { s ->
                                    val upd = s.copy(bpm = metro.bpm, beats = metro.beatsPerBar)
                                    sheet = upd
                                    scope.launch { app.library.update(upd) }
                                }
                            },
                        )
                    }
                }
                if (mode == DisplayMode.SCROLL_V || mode == DisplayMode.SCROLL_H) {
                    AutoScrollPill(
                        playing = autoScroll,
                        speed = (sheet?.scrollSpeed ?: 0).let { if (it > 0) it else 50 },
                        onToggle = { autoScroll = !autoScroll },
                        onSpeed = { setAutoSpeed(it) },
                        modifier = Modifier.align(Alignment.BottomEnd).padding(12.dp),
                    )
                }
                if (mode == DisplayMode.SINGLE || mode == DisplayMode.TWO || mode == DisplayMode.HALF) {
                    AutoScrollPill(
                        playing = autoTurn,
                        speed = turnInterval,
                        onToggle = { autoTurn = !autoTurn },
                        onSpeed = { turnInterval = (turnInterval + it).coerceIn(5, 120) },
                        modifier = Modifier.align(Alignment.BottomEnd).padding(12.dp),
                    )
                }
                if (editLinks && mode == DisplayMode.SINGLE) {
                    Surface(
                        Modifier.align(Alignment.TopCenter).padding(8.dp),
                        shape = RoundedCornerShape(20.dp),
                        tonalElevation = 4.dp,
                        shadowElevation = 6.dp,
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Tippen: Sprungpunkt setzen/löschen", Modifier.padding(start = 12.dp), style = MaterialTheme.typography.labelLarge)
                            TextButton(onClick = { editLinks = false }) { Text("Fertig") }
                        }
                    }
                }
                if (showCrop && mode == DisplayMode.SINGLE) {
                    Surface(
                        Modifier.align(Alignment.BottomCenter).fillMaxWidth(),
                        tonalElevation = 6.dp,
                        shadowElevation = 8.dp,
                    ) {
                        Column(Modifier.padding(12.dp)) {
                            Text(
                                if (cropAllPages) "Zuschneiden – alle Seiten" else "Zuschneiden – Seite ${pdfPage + 1}",
                                style = MaterialTheme.typography.titleSmall,
                            )
                            CropSlider("Links", cropL, 0f, (cropR - 0.1f).coerceAtLeast(0f)) { cropL = it }
                            CropSlider("Oben", cropT, 0f, (cropB - 0.1f).coerceAtLeast(0f)) { cropT = it }
                            CropSlider("Rechts", cropR, (cropL + 0.1f).coerceAtMost(1f), 1f) { cropR = it }
                            CropSlider("Unten", cropB, (cropT + 0.1f).coerceAtMost(1f), 1f) { cropB = it }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(checked = cropAllPages, onCheckedChange = { cropAllPages = it })
                                Text("Für alle Seiten übernehmen", style = MaterialTheme.typography.bodyMedium)
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                TextButton(onClick = { cropL = 0f; cropT = 0f; cropR = 1f; cropB = 1f }) { Text("Zurücksetzen") }
                                Spacer(Modifier.weight(1f))
                                TextButton(onClick = { showCrop = false }) { Text("Abbrechen") }
                                TextButton(onClick = { saveCrop() }) { Text("Speichern") }
                            }
                        }
                    }
                }
            }

            if (showBookmarks) {
                BookmarkSheet(
                    outline = outline,
                    bookmarks = bookmarks,
                    onJump = { jumpToPdf(it) },
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
            if (showLinkList) {
                LinkPointSheet(
                    linkPoints = linkPoints,
                    onGoToSource = { showLinkList = false; jumpToPdf((it.sourcePage - 1).coerceAtLeast(0)) },
                    onDelete = { deleteLink(it) },
                    onEdit = { showLinkList = false; editLinks = true; if (mode != DisplayMode.SINGLE) chooseMode(DisplayMode.SINGLE) },
                    onDismiss = { showLinkList = false },
                )
            }
            pendingLink?.let { pos ->
                AlertDialog(
                    onDismissRequest = { pendingLink = null },
                    confirmButton = {
                        TextButton(onClick = {
                            val t = linkTargetInput.toIntOrNull()
                            if (t != null && t >= 1) saveLink(pos.x, pos.y, t, linkColorPick)
                            pendingLink = null
                        }) { Text("OK") }
                    },
                    dismissButton = { TextButton(onClick = { pendingLink = null }) { Text("Abbrechen") } },
                    title = { Text("Sprungpunkt") },
                    text = {
                        Column {
                            OutlinedTextField(
                                value = linkTargetInput,
                                onValueChange = { linkTargetInput = it.filter(Char::isDigit) },
                                label = { Text("Zielseite") },
                                singleLine = true,
                            )
                            Spacer(Modifier.height(16.dp))
                            Text("Farbe", style = MaterialTheme.typography.labelLarge)
                            Spacer(Modifier.height(8.dp))
                            LinkColorDots(linkColorPick) { linkColorPick = it }
                        }
                    },
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
                    onJump = { hit, i -> searchIdx = i; showMatchNav = true; showSearch = false; jumpToPdf(hit.page) },
                    onDismiss = { showSearch = false },
                )
            }
            if (showAudio && !editMode) {
                Box(Modifier.align(Alignment.BottomCenter)) {
                    AudioTuningPanel(
                        tonePlaying = tonePlaying,
                        toneLabel = noteLabel(toneMidi),
                        onToneToggle = { toggleTone() },
                        onToneShift = { shiftTone(it) },
                        controller = audioCtl,
                        hasAudio = audioFile != null,
                        onAttachAudio = { audioPicker.launch(arrayOf("audio/*")) },
                        onRemoveAudio = { scope.launch { sheet?.let { s -> sheet = app.library.removeAudio(s) } } },
                        onClose = { showAudio = false },
                    )
                }
            }
            if (showPageInput) {
                AlertDialog(
                    onDismissRequest = { showPageInput = false },
                    confirmButton = {
                        TextButton(onClick = {
                            val n = pageInput.toIntOrNull()
                            if (n != null && order.isNotEmpty()) jumpToDisplay((n - 1).coerceIn(0, order.size - 1))
                            showPageInput = false
                        }) { Text("OK") }
                    },
                    dismissButton = { TextButton(onClick = { showPageInput = false }) { Text("Abbrechen") } },
                    title = { Text("Seite") },
                    text = {
                        OutlinedTextField(
                            value = pageInput,
                            onValueChange = { pageInput = it.filter(Char::isDigit) },
                            label = { Text("Seite (1–${order.size})") },
                            singleLine = true,
                        )
                    },
                )
            }
            if (showGrid) {
                Dialog(
                    onDismissRequest = { showGrid = false },
                    properties = DialogProperties(usePlatformDefaultWidth = false),
                ) {
                    PageGrid(
                        doc = doc,
                        pages = order,
                        currentDisplay = page,
                        thumbPx = 320,
                        bookmarkColors = bookmarks.associate { it.page - 1 to it.color },
                        annotatedPages = annotatedPages,
                        onJump = { showGrid = false; jumpToDisplay(it) },
                        onClose = { showGrid = false },
                    )
                }
            }
            if (showPageOrder) {
                Dialog(
                    onDismissRequest = { showPageOrder = false },
                    properties = DialogProperties(usePlatformDefaultWidth = false),
                ) {
                    PageOrderEditor(
                        doc = doc,
                        initialOrder = order,
                        pageCount = pageCount,
                        onSave = { newOrder ->
                            order = newOrder
                            page = page.coerceIn(0, maxOf(0, newOrder.size - 1))
                            jumpToken++
                            sheet?.let { s ->
                                val updated = s.copy(pageOrder = encodePageOrder(newOrder, pageCount))
                                sheet = updated
                                scope.launch { app.library.update(updated) }
                            }
                            showPageOrder = false
                        },
                        onDismiss = { showPageOrder = false },
                    )
                }
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

private val NOTE_NAMES = arrayOf("C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B")

/** Equal-tempered frequency of a MIDI note (A4 = 69 = 440 Hz). */
private fun toneFreq(midi: Int): Double = 440.0 * 2.0.pow((midi - 69) / 12.0)

private fun noteLabel(midi: Int): String = NOTE_NAMES[midi % 12] + (midi / 12 - 1)

@Composable
private fun AutoScrollPill(
    playing: Boolean,
    speed: Int,
    onToggle: () -> Unit,
    onSpeed: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(modifier, shape = RoundedCornerShape(24.dp), tonalElevation = 4.dp, shadowElevation = 6.dp) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 4.dp)) {
            IconButton(onClick = onToggle) {
                Icon(
                    if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    contentDescription = if (playing) "Auto-Scroll pausieren" else "Auto-Scroll starten",
                )
            }
            TextButton(onClick = { onSpeed(-10) }) { Text("–") }
            Text("$speed", style = MaterialTheme.typography.labelLarge)
            TextButton(onClick = { onSpeed(10) }) { Text("+") }
        }
    }
}

@Composable
private fun CropSlider(label: String, value: Float, min: Float, max: Float, onChange: (Float) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(0.3f), style = MaterialTheme.typography.bodySmall)
        Slider(
            value = value.coerceIn(min, max),
            onValueChange = { onChange(it.coerceIn(min, max)) },
            valueRange = 0f..1f,
            modifier = Modifier.weight(0.7f),
        )
    }
}

private fun viewerBgColor(bg: String): Color = when (bg) {
    "white" -> Color(0xFFFFFFFF)
    "gray" -> Color(0xFF4A4A4A)
    else -> Color(0xFF121212)
}

/** Parse a per-song page order (CSV of 1-based PDF pages) to 0-based display indices; natural if empty/invalid. */
private fun parsePageOrder(csv: String, pageCount: Int): List<Int> {
    val natural = (0 until pageCount).toList()
    if (csv.isBlank() || pageCount <= 0) return natural
    val parsed = csv.split(',').mapNotNull { it.trim().toIntOrNull() }.map { it - 1 }.filter { it in 0 until pageCount }
    return parsed.ifEmpty { natural }
}

/** Encode a display order (0-based) back to the stored CSV; empty string means natural order. */
private fun encodePageOrder(order: List<Int>, pageCount: Int): String {
    val natural = (0 until pageCount).toList()
    return if (order == natural) "" else order.joinToString(",") { (it + 1).toString() }
}
