package ch.desgin.basicsheets.ui.viewer

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
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
import kotlin.math.max

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
    onTapAt: (Float, Float) -> Unit,
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
                    onBeginStroke, onAddPen, onAddMarker, onAddShape, onAddHairpin, onEraseAt, onTapAt,
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
    onTapAt: (Float, Float) -> Unit,
    modifier: Modifier,
) {
    var livePts by remember { mutableStateOf<List<Offset>>(emptyList()) }
    var liveStart by remember { mutableStateOf<Offset?>(null) }
    var liveEnd by remember { mutableStateOf<Offset?>(null) }

    Canvas(
        modifier
            .pointerInput(tool) {
                detectTapGestures { pos ->
                    if (tool.isTap) onTapAt(pos.x / size.width, pos.y / size.height)
                }
            }
            .pointerInput(tool, color, sizeFrac, fill) {
                detectDragGestures(
                    onDragStart = { pos ->
                        if (!tool.isTap) onBeginStroke()
                        when {
                            tool.isStroke -> livePts = listOf(pos)
                            tool.isTwoPoint -> { liveStart = pos; liveEnd = pos }
                            tool == Tool.ERASER -> onEraseAt(pos.x / size.width, pos.y / size.height)
                            else -> {}
                        }
                    },
                    onDrag = { change, _ ->
                        when {
                            tool.isStroke -> livePts = livePts + change.position
                            tool.isTwoPoint -> liveEnd = change.position
                            tool == Tool.ERASER -> onEraseAt(change.position.x / size.width, change.position.y / size.height)
                            else -> {}
                        }
                    },
                    onDragEnd = {
                        val w = size.width.toFloat()
                        val h = size.height.toFloat()
                        val s = liveStart
                        val e = liveEnd
                        when {
                            tool == Tool.PEN && livePts.size > 1 ->
                                onAddPen(PenStroke(livePts.map { Pt(it.x / w, it.y / h) }, color.toArgb(), sizeFrac))
                            tool == Tool.MARKER && livePts.size > 1 ->
                                onAddMarker(MarkerStroke(livePts.map { Pt(it.x / w, it.y / h) }, color.toArgb(), sizeFrac))
                            tool.isTwoPoint && s != null && e != null ->
                                if (tool == Tool.CRESCENDO || tool == Tool.DECRESCENDO)
                                    onAddHairpin(Hairpin(s.x / w, s.y / h, e.x / w, e.y / h, color.toArgb(), sizeFrac, tool == Tool.CRESCENDO))
                                else
                                    onAddShape(ShapeAnnot(tool.shapeKind, s.x / w, s.y / h, e.x / w, e.y / h, color.toArgb(), sizeFrac, fill))
                        }
                        livePts = emptyList(); liveStart = null; liveEnd = null
                    },
                    onDragCancel = { livePts = emptyList(); liveStart = null; liveEnd = null },
                )
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
                    for (i in 1 until livePts.size) lineTo(livePts[i].x, livePts[i].y)
                }
                val c = if (tool == Tool.MARKER) color.copy(alpha = 0.35f) else color
                drawPath(path, c, style = Stroke(width = max(1f, sizeFrac * w), cap = StrokeCap.Round, join = StrokeJoin.Round))
            }
            tool.isTwoPoint && s != null && e != null ->
                if (tool == Tool.CRESCENDO || tool == Tool.DECRESCENDO)
                    drawHairpin(Hairpin(s.x / w, s.y / h, e.x / w, e.y / h, color.toArgb(), sizeFrac, tool == Tool.CRESCENDO), w, h)
                else
                    drawShapeAnnot(ShapeAnnot(tool.shapeKind, s.x / w, s.y / h, e.x / w, e.y / h, color.toArgb(), sizeFrac, fill), w, h, alpha = 0.75f)
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
    stampType: String,
    onTool: (Tool) -> Unit,
    onColor: (Color) -> Unit,
    onSize: (Int) -> Unit,
    onFill: (Boolean) -> Unit,
    onOpenStamps: () -> Unit,
    onUndo: () -> Unit,
    onDone: () -> Unit,
) {
    Surface(tonalElevation = 3.dp, shadowElevation = 8.dp, modifier = Modifier.fillMaxWidth()) {
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
                            .then(if (c == color) Modifier.border(2.dp, MaterialTheme.colorScheme.onSurface, CircleShape) else Modifier)
                            .clickable { onColor(c) },
                    )
                }
                Spacer(Modifier.weight(1f))
                TextButton(onClick = { onSize((sizeLevel - 1).coerceAtLeast(1)) }) { Text("–") }
                Text("$sizeLevel")
                TextButton(onClick = { onSize((sizeLevel + 1).coerceAtMost(10)) }) { Text("+") }
                if (tool == Tool.RECT || tool == Tool.ELLIPSE) {
                    FilterChip(selected = fill, onClick = { onFill(!fill) }, label = { Text("Füllen") })
                }
                TextButton(onClick = onUndo) { Text("↶") }
                TextButton(onClick = onDone) { Text("Fertig") }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ToolChip(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(selected = selected, onClick = onClick, label = { Text(label) })
}

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
