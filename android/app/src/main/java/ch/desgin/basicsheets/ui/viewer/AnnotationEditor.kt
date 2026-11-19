package ch.desgin.basicsheets.ui.viewer

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.PointerButtons
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.changedToDown
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ch.desgin.basicsheets.annot.Hairpin
import ch.desgin.basicsheets.annot.MarkerStroke
import ch.desgin.basicsheets.annot.PageAnnotations
import ch.desgin.basicsheets.annot.PenStroke
import ch.desgin.basicsheets.annot.Pt
import ch.desgin.basicsheets.annot.ShapeAnnot
import ch.desgin.basicsheets.annot.Tool
import ch.desgin.basicsheets.annot.isStroke
import ch.desgin.basicsheets.annot.isTap
import ch.desgin.basicsheets.annot.isTwoPoint
import ch.desgin.basicsheets.annot.shapeKind
import ch.desgin.basicsheets.pdf.PdfDoc
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * True when a stylus side button is held. Android reports the primary/secondary stylus buttons as
 * bits 5/6 of the button state; some pens report the side button as the secondary/tertiary button,
 * so we accept those too.
 */
private fun PointerButtons.stylusSidePressed(): Boolean =
    isPressed(5) || isPressed(6) || isSecondaryPressed || isTertiaryPressed

/** The current page shown fit-to-width with a draw overlay matching its bounds. */
@Composable
fun EditablePage(
    doc: PdfDoc?,
    page: Int,
    renderPx: Int,
    data: PageAnnotations,
    tool: Tool,
    color: Color,
    sizeFrac: Float,
    fill: Boolean,
    onBeginStroke: () -> Unit,
    onAddPen: (PenStroke) -> Unit,
    onAddMarker: (MarkerStroke) -> Unit,
    onAddShape: (ShapeAnnot) -> Unit,
    onAddHairpin: (Hairpin) -> Unit,
    onEraseAt: (Float, Float) -> Unit,
    eraserBox: Boolean,
    onEraseBox: (Float, Float, Float, Float) -> Unit,
    onTapAt: (Float, Float) -> Unit,
    palmReject: Boolean = false,
    penButtonErase: Boolean = false,
) {
    val bmp = pageBitmap(doc, page, renderPx)
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        if (bmp != null) {
            Box(Modifier.fillMaxWidth()) {
                Image(
                    bmp.asImageBitmap(),
                    contentDescription = null,
                    modifier = Modifier.fillMaxWidth(),
                    contentScale = ContentScale.FillWidth,
                )
                AnnotationCanvas(
                    data, tool, color, sizeFrac, fill,
                    onBeginStroke, onAddPen, onAddMarker, onAddShape, onAddHairpin, onEraseAt, eraserBox, onEraseBox, onTapAt,
                    palmReject, penButtonErase,
                    Modifier.matchParentSize(),
                )
            }
        } else {
            Text("Lädt …", color = MaterialTheme.colorScheme.outline)
        }
    }
}

@Composable
private fun AnnotationCanvas(
    data: PageAnnotations,
    tool: Tool,
    color: Color,
    sizeFrac: Float,
    fill: Boolean,
    onBeginStroke: () -> Unit,
    onAddPen: (PenStroke) -> Unit,
    onAddMarker: (MarkerStroke) -> Unit,
    onAddShape: (ShapeAnnot) -> Unit,
    onAddHairpin: (Hairpin) -> Unit,
    onEraseAt: (Float, Float) -> Unit,
    eraserBox: Boolean,
    onEraseBox: (Float, Float, Float, Float) -> Unit,
    onTapAt: (Float, Float) -> Unit,
    palmReject: Boolean,
    penButtonErase: Boolean,
    modifier: Modifier,
) {
    var livePts by remember { mutableStateOf<List<Offset>>(emptyList()) }
    var livePressures by remember { mutableStateOf<List<Float>>(emptyList()) }
    var liveStart by remember { mutableStateOf<Offset?>(null) }
    var liveEnd by remember { mutableStateOf<Offset?>(null) }

    Canvas(
        modifier
            .pointerInput(tool) {
                detectTapGestures { pos ->
                    if (tool.isTap) onTapAt(pos.x / size.width, pos.y / size.height)
                }
            }
            .pointerInput(tool, color, sizeFrac, fill, eraserBox, palmReject, penButtonErase) {
                // Raw pointer loop (not detectDragGestures) so we can read the tool type and the
                // stylus side-button state at touch-down — needed for palm rejection and the
                // pen-button eraser. Behaviour with both settings off matches the old drag gestures.
                awaitEachGesture {
                    var ev = awaitPointerEvent()
                    val down = ev.changes.firstOrNull { it.changedToDown() } ?: return@awaitEachGesture
                    val stylus = down.type == PointerType.Stylus
                    // Palm rejection (pen-only): ignore finger/touch strokes while the setting is on.
                    if (palmReject && !stylus) return@awaitEachGesture
                    // Pen side-button → erase regardless of the selected tool.
                    val sideErase = stylus && penButtonErase && ev.buttons.stylusSidePressed()
                    val effTool = if (sideErase) Tool.ERASER else tool
                    // Tap tools (stamp/text) are placed by the separate tap handler — leave them to it.
                    if (effTool.isTap) return@awaitEachGesture

                    val pid = down.id
                    onBeginStroke()
                    when {
                        effTool.isStroke -> { livePts = listOf(down.position); livePressures = listOf(1f) }
                        effTool.isTwoPoint -> { liveStart = down.position; liveEnd = down.position }
                        effTool == Tool.ERASER -> if (eraserBox) { liveStart = down.position; liveEnd = down.position } else onEraseAt(down.position.x / size.width, down.position.y / size.height)
                        else -> {}
                    }
                    down.consume()

                    var canceled = false
                    while (true) {
                        ev = awaitPointerEvent()
                        val ch = ev.changes.firstOrNull { it.id == pid }
                        if (ch == null) { canceled = true; break }
                        if (!ch.pressed) break   // pointer lifted
                        if (ch.positionChanged()) {
                            when {
                                effTool.isStroke -> {
                                    livePts = livePts + ch.position
                                    livePressures = livePressures + if (ch.type == PointerType.Stylus && ch.pressure in 0.02f..1f) ch.pressure else 1f
                                }
                                effTool.isTwoPoint -> liveEnd = ch.position
                                effTool == Tool.ERASER -> if (eraserBox) liveEnd = ch.position else onEraseAt(ch.position.x / size.width, ch.position.y / size.height)
                                else -> {}
                            }
                            ch.consume()
                        }
                    }

                    if (!canceled) {
                        val w = size.width.toFloat()
                        val h = size.height.toFloat()
                        val s = liveStart
                        val e = liveEnd
                        when {
                            effTool == Tool.PEN && livePts.size > 1 ->
                                onAddPen(PenStroke(livePts.mapIndexed { i, o -> Pt(o.x / w, o.y / h, livePressures.getOrElse(i) { 1f }) }, color.toArgb(), sizeFrac))
                            effTool == Tool.MARKER && livePts.size > 1 ->
                                onAddMarker(MarkerStroke(livePts.map { Pt(it.x / w, it.y / h) }, color.toArgb(), sizeFrac))
                            effTool.isTwoPoint && s != null && e != null -> {
                                val es = if (effTool == Tool.CRESCENDO || effTool == Tool.DECRESCENDO) e else snapShapeEnd(effTool, s, e)
                                if (effTool == Tool.CRESCENDO || effTool == Tool.DECRESCENDO)
                                    onAddHairpin(Hairpin(s.x / w, s.y / h, es.x / w, es.y / h, color.toArgb(), sizeFrac, effTool == Tool.CRESCENDO))
                                else
                                    onAddShape(ShapeAnnot(effTool.shapeKind, s.x / w, s.y / h, es.x / w, es.y / h, color.toArgb(), sizeFrac, fill))
                            }
                            effTool == Tool.ERASER && eraserBox && s != null && e != null ->
                                onEraseBox(s.x / w, s.y / h, e.x / w, e.y / h)
                        }
                    }
                    livePts = emptyList(); livePressures = emptyList(); liveStart = null; liveEnd = null
                }
            },
    ) {
        val w = size.width
        val h = size.height
        drawPageAnnotations(data, w, h)

        // Live preview of the in-progress mark.
        val s = liveStart
        val e = liveEnd
        when {
            tool.isStroke && livePts.size > 1 -> {
                val path = Path().apply {
                    moveTo(livePts[0].x, livePts[0].y)
                    if (livePts.size < 3) {
                        for (i in 1 until livePts.size) lineTo(livePts[i].x, livePts[i].y)
                    } else {
                        for (i in 1 until livePts.size - 1) {
                            quadraticTo(livePts[i].x, livePts[i].y, (livePts[i].x + livePts[i + 1].x) / 2f, (livePts[i].y + livePts[i + 1].y) / 2f)
                        }
                        lineTo(livePts.last().x, livePts.last().y)
                    }
                }
                val c = if (tool == Tool.MARKER) color.copy(alpha = 0.35f) else color
                drawPath(path, c, style = Stroke(width = max(1f, sizeFrac * w), cap = StrokeCap.Round, join = StrokeJoin.Round))
            }
            tool.isTwoPoint && s != null && e != null -> {
                val es = if (tool == Tool.CRESCENDO || tool == Tool.DECRESCENDO) e else snapShapeEnd(tool, s, e)
                if (tool == Tool.CRESCENDO || tool == Tool.DECRESCENDO)
                    drawHairpin(Hairpin(s.x / w, s.y / h, es.x / w, es.y / h, color.toArgb(), sizeFrac, tool == Tool.CRESCENDO), w, h)
                else
                    drawShapeAnnot(ShapeAnnot(tool.shapeKind, s.x / w, s.y / h, es.x / w, es.y / h, color.toArgb(), sizeFrac, fill), w, h, alpha = 0.75f)
            }
            tool == Tool.ERASER && eraserBox && s != null && e != null -> {
                val l = min(s.x, e.x); val t = min(s.y, e.y)
                val bw = abs(e.x - s.x); val bh = abs(e.y - s.y)
                drawRect(Color.Red.copy(alpha = 0.10f), topLeft = Offset(l, t), size = Size(bw, bh))
                drawRect(Color.Red, topLeft = Offset(l, t), size = Size(bw, bh), style = Stroke(width = 2f))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnnotationToolbar(
    tool: Tool,
    color: Color,
    sizeLevel: Int,
    fill: Boolean,
    eraserBox: Boolean,
    stampType: String,
    onTool: (Tool) -> Unit,
    onColor: (Color) -> Unit,
    onSize: (Int) -> Unit,
    onFill: (Boolean) -> Unit,
    onEraserBox: (Boolean) -> Unit,
    onOpenStamps: () -> Unit,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onDone: () -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary,
        shadowElevation = 8.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        val btn = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onPrimary)
        Column(Modifier.fillMaxWidth().padding(8.dp)) {
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                ToolChip("Stift", tool == Tool.PEN) { onTool(Tool.PEN) }
                ToolChip("Marker", tool == Tool.MARKER) { onTool(Tool.MARKER) }
                ToolChip("Radierer", tool == Tool.ERASER) { onTool(Tool.ERASER) }
                ToolChip("Text", tool == Tool.TEXT) { onTool(Tool.TEXT) }
                ToolChip(STAMP_SYM[stampType] ?: "Stempel", tool == Tool.STAMP, onOpenStamps)
                ToolChip("cresc <", tool == Tool.CRESCENDO) { onTool(Tool.CRESCENDO) }
                ToolChip("dim >", tool == Tool.DECRESCENDO) { onTool(Tool.DECRESCENDO) }
                ToolChip("Linie", tool == Tool.LINE) { onTool(Tool.LINE) }
                ToolChip("Pfeil", tool == Tool.ARROW) { onTool(Tool.ARROW) }
                ToolChip("Rechteck", tool == Tool.RECT) { onTool(Tool.RECT) }
                ToolChip("Ellipse", tool == Tool.ELLIPSE) { onTool(Tool.ELLIPSE) }
            }
            Spacer(Modifier.height(6.dp))
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                for (c in PEN_COLORS) {
                    Box(
                        Modifier.size(26.dp).clip(CircleShape).background(c)
                            .then(if (c == color) Modifier.border(2.dp, MaterialTheme.colorScheme.onPrimary, CircleShape) else Modifier)
                            .clickable { onColor(c) },
                    )
                }
                Spacer(Modifier.weight(1f))
                TextButton(onClick = { onSize((sizeLevel - 1).coerceAtLeast(1)) }, colors = btn) { Text("–") }
                Text("$sizeLevel")
                TextButton(onClick = { onSize((sizeLevel + 1).coerceAtMost(10)) }, colors = btn) { Text("+") }
                if (tool == Tool.RECT || tool == Tool.ELLIPSE) {
                    FilterChip(selected = fill, onClick = { onFill(!fill) }, label = { Text("Füllen") }, colors = petrolChipColors(), border = null)
                }
                if (tool == Tool.ERASER) {
                    FilterChip(selected = eraserBox, onClick = { onEraserBox(!eraserBox) }, label = { Text("Kasten") }, colors = petrolChipColors(), border = null)
                }
                TextButton(onClick = onUndo, colors = btn) { Text("↶") }
                TextButton(onClick = onRedo, colors = btn) { Text("↷") }
                TextButton(onClick = onDone, colors = btn) { Text("Fertig") }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ToolChip(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(selected = selected, onClick = onClick, label = { Text(label) }, colors = petrolChipColors(), border = null)
}

/** FilterChip colors that read on the petrol toolbar: white label; white fill + petrol label when selected. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun petrolChipColors() = FilterChipDefaults.filterChipColors(
    containerColor = Color.Transparent,
    labelColor = MaterialTheme.colorScheme.onPrimary,
    selectedContainerColor = MaterialTheme.colorScheme.onPrimary,
    selectedLabelColor = MaterialTheme.colorScheme.primary,
)

/** Grid of all musical stamps; picking one selects the stamp tool with that symbol. */
@Composable
fun StampPickerDialog(onPick: (String) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text("Schließen") } },
        title = { Text("Stempel") },
        text = {
            LazyVerticalGrid(columns = GridCells.Adaptive(60.dp), modifier = Modifier.fillMaxWidth().height(340.dp)) {
                items(STAMP_TOOLS) { id ->
                    Column(
                        Modifier.padding(4.dp).clip(RoundedCornerShape(8.dp)).clickable { onPick(id) }.width(56.dp).padding(6.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(STAMP_SYM[id] ?: id, fontSize = 20.sp)
                    }
                }
            }
        },
    )
}

/** Quick-pick ink colors (shared by all tools). */
val PEN_COLORS = listOf(
    Color.Black, Color.Red, Color(0xFF1E88E5), Color(0xFF2E7D32), Color(0xFFF59E0B), Color(0xFFFACC15),
)

/** Snap a shape's end point: line/arrow to H/V within ~8°, rect/ellipse to a square within ~12%. */
private fun snapShapeEnd(tool: Tool, s: Offset, e: Offset): Offset {
    val dx = e.x - s.x
    val dy = e.y - s.y
    val adx = abs(dx)
    val ady = abs(dy)
    return when (tool) {
        Tool.LINE, Tool.ARROW -> {
            val tan8 = 0.1405f
            when {
                adx > 10f && ady <= adx * tan8 -> Offset(e.x, s.y)   // snap horizontal
                ady > 10f && adx <= ady * tan8 -> Offset(s.x, e.y)   // snap vertical
                else -> e
            }
        }
        Tool.RECT, Tool.ELLIPSE -> {
            val big = max(adx, ady)
            val small = min(adx, ady)
            if (big > 10f && small >= big * 0.88f) {
                Offset(s.x + big * (if (dx < 0) -1f else 1f), s.y + big * (if (dy < 0) -1f else 1f))
            } else {
                e
            }
        }
        else -> e
    }
}
