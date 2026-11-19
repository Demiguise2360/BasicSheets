package ch.desgin.basicsheets.ui.viewer

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import android.graphics.Bitmap
import ch.desgin.basicsheets.pdf.PdfDoc
import ch.desgin.basicsheets.annot.Hairpin
import ch.desgin.basicsheets.annot.MarkerStroke
import ch.desgin.basicsheets.annot.PageAnnotations
import ch.desgin.basicsheets.annot.PenStroke
import ch.desgin.basicsheets.annot.Pt
import ch.desgin.basicsheets.annot.ShapeAnnot
import ch.desgin.basicsheets.annot.Stamp
import ch.desgin.basicsheets.annot.TextAnnot
import ch.desgin.basicsheets.pdf.MatchBox
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * Shared annotation rendering. Coordinates are normalized [0,1] of the page; `w`/`h` are the
 * pixel size of the surface the page occupies, so the same marks render at any size/device.
 * Used by both the editor overlay and the read-only reading modes.
 */

/** Stamp glyphs — same symbols as the MAUI app (STAMP_SYM). */
val STAMP_SYM: Map<String, String> = linkedMapOf(
    "flat" to "♭", "sharp" to "♯", "natural" to "♮",
    "double-flat" to "𝄫", "double-sharp" to "𝄪",
    "fermata" to "𝄐", "trill" to "tr",
    "segno" to "𝄋", "coda" to "𝄌",
    "accent" to ">", "marcato" to "^", "staccato" to "•", "tenuto" to "–",
    "down-bow" to "⊓", "up-bow" to "V", "breath" to "’", "caesura" to "//",
    "nav-dc" to "D.C.", "nav-ds" to "D.S.", "nav-fine" to "Fine",
    "ped-down" to "Ped.", "ped-up" to "✱",
    "dyn-ppp" to "ppp", "dyn-pp" to "pp", "dyn-p" to "p", "dyn-mp" to "mp", "dyn-mf" to "mf",
    "dyn-f" to "f", "dyn-ff" to "ff", "dyn-fff" to "fff", "dyn-sf" to "sf", "dyn-sfz" to "sfz", "dyn-fp" to "fp",
)

/** The stamp ids, in palette order. */
val STAMP_TOOLS: List<String> = STAMP_SYM.keys.toList()

private fun pointPath(points: List<Pt>, w: Float, h: Float): Path =
    Path().apply {
        if (points.isEmpty()) return@apply
        moveTo(points[0].x * w, points[0].y * h)
        if (points.size < 3) {
            for (i in 1 until points.size) lineTo(points[i].x * w, points[i].y * h)
            return@apply
        }
        // Quadratic Béziers through segment midpoints → smooth curves, not faceted polylines.
        for (i in 1 until points.size - 1) {
            val cx = points[i].x * w; val cy = points[i].y * h
            val mx = (points[i].x + points[i + 1].x) / 2f * w
            val my = (points[i].y + points[i + 1].y) / 2f * h
            quadraticTo(cx, cy, mx, my)
        }
        val last = points[points.size - 1]
        lineTo(last.x * w, last.y * h)
    }

fun DrawScope.drawPenStroke(s: PenStroke, w: Float, h: Float) {
    if (s.points.size < 2) return
    val baseW = max(1f, s.size * w)
    if (s.points.any { it.p != 1f }) {
        // Pressure stroke (stylus): per-segment width from pen pressure; no smoothing.
        for (i in 0 until s.points.size - 1) {
            val a = s.points[i]; val b = s.points[i + 1]
            drawLine(
                Color(s.color), Offset(a.x * w, a.y * h), Offset(b.x * w, b.y * h),
                strokeWidth = baseW * (0.35f + 0.9f * (a.p + b.p) / 2f), cap = StrokeCap.Round,
            )
        }
    } else {
        drawPath(
            pointPath(s.points, w, h), Color(s.color),
            style = Stroke(width = baseW, cap = StrokeCap.Round, join = StrokeJoin.Round),
        )
    }
}

fun DrawScope.drawMarkerStroke(m: MarkerStroke, w: Float, h: Float) {
    if (m.points.size < 2) return
    drawPath(
        pointPath(m.points, w, h), Color(m.color).copy(alpha = 0.35f),
        style = Stroke(width = max(1f, m.size * w), cap = StrokeCap.Round, join = StrokeJoin.Round),
    )
}

fun DrawScope.drawShapeAnnot(s: ShapeAnnot, w: Float, h: Float, alpha: Float = 1f) {
    val col = Color(s.color)
    val lw = max(1.5f, s.size * w * 0.9f)
    val x1 = s.x1 * w; val y1 = s.y1 * h; val x2 = s.x2 * w; val y2 = s.y2 * h
    when (s.kind) {
        "rect" -> {
            val rx = min(x1, x2); val ry = min(y1, y2)
            val rw = abs(x2 - x1); val rh = abs(y2 - y1)
            if (s.fill) drawRect(col.copy(alpha = alpha * 0.25f), topLeft = Offset(rx, ry), size = Size(rw, rh))
            drawRect(col.copy(alpha = alpha), topLeft = Offset(rx, ry), size = Size(rw, rh), style = Stroke(width = lw))
        }
        "ellipse" -> {
            val cx = (x1 + x2) / 2f; val cy = (y1 + y2) / 2f
            val rw = max(1f, abs(x2 - x1) / 2f); val rh = max(1f, abs(y2 - y1) / 2f)
            val topLeft = Offset(cx - rw, cy - rh); val size = Size(rw * 2f, rh * 2f)
            if (s.fill) drawOval(col.copy(alpha = alpha * 0.25f), topLeft = topLeft, size = size)
            drawOval(col.copy(alpha = alpha), topLeft = topLeft, size = size, style = Stroke(width = lw))
        }
        else -> {   // line or arrow
            drawLine(col.copy(alpha = alpha), Offset(x1, y1), Offset(x2, y2), strokeWidth = lw, cap = StrokeCap.Round)
            if (s.kind == "arrow") {
                val ang = atan2(y2 - y1, x2 - x1)
                val head = max(10f, s.size * w * 3.2f)
                val a = Math.PI.toFloat() / 7f
                drawLine(col.copy(alpha = alpha), Offset(x2, y2), Offset(x2 - head * cos(ang - a), y2 - head * sin(ang - a)), strokeWidth = lw, cap = StrokeCap.Round)
                drawLine(col.copy(alpha = alpha), Offset(x2, y2), Offset(x2 - head * cos(ang + a), y2 - head * sin(ang + a)), strokeWidth = lw, cap = StrokeCap.Round)
            }
        }
    }
}

fun DrawScope.drawHairpin(hp: Hairpin, w: Float, h: Float) {
    val col = Color(hp.color)
    val lw = max(1.5f, hp.size * w * 0.9f)
    val sp = hp.size * w * 2.2f   // vertical spread of the open end
    val x1 = hp.x1 * w; val y1 = hp.y1 * h; val x2 = hp.x2 * w; val y2 = hp.y2 * h
    if (hp.crescendo) {
        drawLine(col, Offset(x1, y1), Offset(x2, y2 - sp), strokeWidth = lw, cap = StrokeCap.Round)
        drawLine(col, Offset(x1, y1), Offset(x2, y2 + sp), strokeWidth = lw, cap = StrokeCap.Round)
    } else {
        drawLine(col, Offset(x1, y1 - sp), Offset(x2, y2), strokeWidth = lw, cap = StrokeCap.Round)
        drawLine(col, Offset(x1, y1 + sp), Offset(x2, y2), strokeWidth = lw, cap = StrokeCap.Round)
    }
}

fun DrawScope.drawStampAnnot(st: Stamp, w: Float, h: Float, drawAlpha: Float = 1f) {
    val sym = STAMP_SYM[st.type] ?: st.type
    val isTextMark = st.type.startsWith("dyn-") || st.type.startsWith("nav-") || st.type.startsWith("ped-")
    val paint = Paint().apply {
        isAntiAlias = true
        color = st.color
        this.alpha = (drawAlpha * 255f).toInt().coerceIn(0, 255)
        textSize = max(8f, st.size * w)
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create(Typeface.SERIF, if (isTextMark) Typeface.BOLD_ITALIC else Typeface.BOLD)
    }
    val cx = st.x * w
    val cy = st.y * h - (paint.ascent() + paint.descent()) / 2f   // vertically centered
    drawContext.canvas.nativeCanvas.drawText(sym, cx, cy, paint)
}

fun DrawScope.drawTextAnnot(t: TextAnnot, w: Float, h: Float) {
    if (t.text.isEmpty()) return
    val paint = Paint().apply {
        isAntiAlias = true
        color = t.color
        textSize = max(8f, t.fontSize * w)
        textAlign = Paint.Align.LEFT
    }
    val x = t.x * w
    val y = t.y * h - paint.ascent()   // top-aligned baseline
    drawContext.canvas.nativeCanvas.drawText(t.text, x, y, paint)
}

/**
 * Draw translucent highlight rectangles for in-document search matches, in normalized [0,1]
 * page coordinates. Painted under the annotations so pen/marker strokes stay on top.
 */
fun DrawScope.drawSearchHighlight(boxes: List<MatchBox>, w: Float, h: Float) {
    if (boxes.isEmpty()) return
    val color = Color(0x66FFD200)   // translucent yellow, matching the MAUI viewer's search tint
    for (b in boxes) {
        drawRect(
            color,
            topLeft = Offset(b.left * w, b.top * h),
            size = Size((b.right - b.left) * w, (b.bottom - b.top) * h),
        )
    }
}

/** Draw a whole page's saved annotations in reading order (marker under, text on top). */
fun DrawScope.drawPageAnnotations(data: PageAnnotations, w: Float, h: Float) {
    for (m in data.markerData) drawMarkerStroke(m, w, h)
    for (s in data.penStrokes) drawPenStroke(s, w, h)
    for (s in data.shapeData) drawShapeAnnot(s, w, h)
    for (hp in data.hairpinData) drawHairpin(hp, w, h)
    for (st in data.stampData) drawStampAnnot(st, w, h)
    for (t in data.textData) drawTextAnnot(t, w, h)
}

/**
 * Render a page to a bitmap with its saved annotations drawn on top (for "Seite als Bild" export).
 * Reuses [drawPageAnnotations] through a CanvasDrawScope over the rendered page bitmap; off-main via
 * [PdfDoc.render]. Returns null if the page can't be rendered.
 */
suspend fun renderPageForExport(doc: PdfDoc?, pdfPage: Int, anno: PageAnnotations, widthPx: Int = 1600): Bitmap? {
    val bmp = doc?.render(pdfPage, widthPx) ?: return null
    val canvas = Canvas(bmp.asImageBitmap())
    CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, canvas, Size(bmp.width.toFloat(), bmp.height.toFloat())) {
        drawPageAnnotations(anno, size.width, size.height)
    }
    return bmp
}
