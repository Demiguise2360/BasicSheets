package ch.desgin.basicsheets.ui.viewer

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
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
import ch.desgin.basicsheets.annot.PageAnnotations
import ch.desgin.basicsheets.annot.PenStroke
import ch.desgin.basicsheets.annot.Pt
import ch.desgin.basicsheets.annot.Tool
import ch.desgin.basicsheets.pdf.PdfDoc

/** The current page shown fit-to-width with a draw overlay matching its bounds. */
@Composable
fun EditablePage(
    doc: PdfDoc?,
    page: Int,
    renderPx: Int,
    data: PageAnnotations,
    tool: Tool,
    color: Color,
    strokeWidth: Float,
    onBeginStroke: () -> Unit,
    onAddStroke: (PenStroke) -> Unit,
    onErase: (Float, Float) -> Unit,
) {
    val bmp = pageBitmap(doc, page, renderPx)
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        if (bmp != null) {
            Box(Modifier.fillMaxWidth()) {
                Image(bmp.asImageBitmap(), contentDescription = null, modifier = Modifier.fillMaxWidth(), contentScale = ContentScale.FillWidth)
                AnnotationCanvas(data, tool, color, strokeWidth, onBeginStroke, onAddStroke, onErase, Modifier.matchParentSize())
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
    strokeWidth: Float,
    onBeginStroke: () -> Unit,
    onAddStroke: (PenStroke) -> Unit,
    onErase: (Float, Float) -> Unit,
    modifier: Modifier,
) {
    var live by remember { mutableStateOf<List<Offset>>(emptyList()) }
    Canvas(
        modifier.pointerInput(tool) {
            detectDragGestures(
                onDragStart = { pos -> onBeginStroke(); if (tool == Tool.PEN) live = listOf(pos) },
                onDrag = { change, _ ->
                    if (tool == Tool.PEN) {
                        live = live + change.position
                    } else {
                        onErase(change.position.x / size.width.toFloat(), change.position.y / size.height.toFloat())
                    }
                },
                onDragEnd = {
                    if (tool == Tool.PEN && live.size > 1) {
                        val w = size.width.toFloat()
                        val h = size.height.toFloat()
                        onAddStroke(PenStroke(live.map { Pt(it.x / w, it.y / h) }, color.toArgb(), strokeWidth))
                    }
                    live = emptyList()
                },
                onDragCancel = { live = emptyList() },
            )
        },
    ) {
        val w = size.width
        val h = size.height
        for (s in data.penStrokes) {
            if (s.points.size < 2) continue
            val path = Path().apply {
                moveTo(s.points[0].x * w, s.points[0].y * h)
                for (i in 1 until s.points.size) lineTo(s.points[i].x * w, s.points[i].y * h)
            }
            drawPath(path, Color(s.color), style = Stroke(width = s.size * w, cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
        if (live.size > 1) {
            val path = Path().apply {
                moveTo(live[0].x, live[0].y)
                for (i in 1 until live.size) lineTo(live[i].x, live[i].y)
            }
            drawPath(path, color, style = Stroke(width = strokeWidth * w, cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnnotationToolbar(
    tool: Tool,
    color: Color,
    onTool: (Tool) -> Unit,
    onColor: (Color) -> Unit,
    onUndo: () -> Unit,
    onDone: () -> Unit,
) {
    Surface(tonalElevation = 3.dp, shadowElevation = 8.dp, modifier = Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilterChip(selected = tool == Tool.PEN, onClick = { onTool(Tool.PEN) }, label = { Text("Stift") })
            FilterChip(selected = tool == Tool.ERASER, onClick = { onTool(Tool.ERASER) }, label = { Text("Radierer") })
            Spacer(Modifier.weight(1f))
            for (c in PEN_COLORS) {
                Box(
                    Modifier.size(28.dp).clip(CircleShape).background(c)
                        .then(if (c == color) Modifier.border(2.dp, MaterialTheme.colorScheme.onSurface, CircleShape) else Modifier)
                        .clickable { onColor(c) },
                )
            }
            TextButton(onClick = onUndo) { Text("↶") }
            TextButton(onClick = onDone) { Text("Fertig") }
        }
    }
}

val PEN_COLORS = listOf(Color.Black, Color.Red, Color(0xFF1E88E5), Color(0xFF2E7D32))
