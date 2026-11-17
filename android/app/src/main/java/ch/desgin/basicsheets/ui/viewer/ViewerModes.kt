package ch.desgin.basicsheets.ui.viewer

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import ch.desgin.basicsheets.annot.PageAnnotations
import ch.desgin.basicsheets.data.AnnotationRepository
import ch.desgin.basicsheets.pdf.PdfDoc
import ch.desgin.basicsheets.ui.settings.LocalSettings
import kotlin.math.roundToInt

/** Color-inverting filter for night mode, applied to page bitmaps. */
private val INVERT_FILTER = ColorFilter.colorMatrix(
    ColorMatrix(
        floatArrayOf(
            -1f, 0f, 0f, 0f, 255f,
            0f, -1f, 0f, 0f, 255f,
            0f, 0f, -1f, 0f, 255f,
            0f, 0f, 0f, 1f, 0f,
        ),
    ),
)

/** Display modes, keyed by the same strings the MAUI app stores in Sheet.pageMode. */
enum class DisplayMode(val key: String, val label: String) {
    SINGLE("single", "Einzelne Seite"),
    TWO("two", "Zwei Seiten"),
    HALF("half-turn", "Halbe Seite"),
    SCROLL_V("scroll-v", "Vertikal scrollen"),
    SCROLL_H("scroll-h", "Horizontal scrollen"),
    ;

    companion object {
        fun from(key: String?): DisplayMode = entries.firstOrNull { it.key == key } ?: SINGLE
    }
}

/*
 * The reading views navigate by DISPLAY slot, not PDF page: `pages` is the ordered list of 0-based
 * PDF page indices to show (natural order, or a custom reorder/hide/duplicate). `page`/`startPage`
 * index into it; the PDF page rendered for slot d is `pages[d]`.
 */

/** Lazily render a page to a bitmap, re-rendering when the page/width/doc changes. */
@Composable
fun pageBitmap(doc: PdfDoc?, index: Int, widthPx: Int): Bitmap? =
    produceState<Bitmap?>(null, doc, index, widthPx) {
        value = if (doc != null && widthPx > 0 && index >= 0) doc.render(index, widthPx) else null
    }.value

/** Load a page's saved annotations (empty when no repo/key); reloads when `version` bumps. */
@Composable
fun rememberPageAnnotations(repo: AnnotationRepository?, pdfKey: String?, page: Int, version: Int): PageAnnotations =
    produceState(PageAnnotations(), repo, pdfKey, page, version) {
        value = if (repo != null && !pdfKey.isNullOrEmpty() && page >= 0)
            runCatching { repo.load(pdfKey, page) }.getOrDefault(PageAnnotations())
        else PageAnnotations()
    }.value

/**
 * A page bitmap with its saved annotations drawn on top (read-only). The caller's `modifier`
 * sizes one axis (fillMaxWidth / fillMaxHeight / weight); the page aspect fixes the other.
 */
@Composable
private fun AnnotatedPageCell(bmp: Bitmap?, anno: PageAnnotations, modifier: Modifier) {
    if (bmp == null) return
    Box(modifier.aspectRatio(bmp.width.toFloat() / bmp.height.toFloat())) {
        Image(
            bmp.asImageBitmap(),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Fit,
            colorFilter = if (LocalSettings.current.invert) INVERT_FILTER else null,
        )
        if (!anno.isEmpty()) {
            Canvas(Modifier.matchParentSize()) { drawPageAnnotations(anno, size.width, size.height) }
        }
    }
}

@Composable
fun SinglePageView(
    doc: PdfDoc?,
    page: Int,
    pages: List<Int>,
    renderPx: Int,
    annoRepo: AnnotationRepository? = null,
    pdfKey: String? = null,
    annoVersion: Int = 0,
    onTurn: (Int) -> Unit,
) {
    val pdf = pages.getOrElse(page) { 0 }
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    LaunchedEffect(page) { scale = 1f; offset = Offset.Zero }

    val transform = rememberTransformableState { zoom, pan, _ ->
        scale = (scale * zoom).coerceIn(1f, 5f)
        offset = if (scale > 1f) offset + pan else Offset.Zero
    }
    val bmp = pageBitmap(doc, pdf, renderPx)
    val anno = rememberPageAnnotations(annoRepo, pdfKey, pdf, annoVersion)

    Box(
        Modifier
            .fillMaxSize()
            .transformable(transform)
            .pointerInput(pages.size) {
                detectTapGestures { pos ->
                    if (scale <= 1.05f) {
                        val third = size.width / 3f
                        if (pos.x < third) onTurn(-1) else if (pos.x > size.width - third) onTurn(1)
                    }
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        if (bmp != null) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(bmp.width.toFloat() / bmp.height.toFloat())
                    .graphicsLayer(scaleX = scale, scaleY = scale, translationX = offset.x, translationY = offset.y),
            ) {
                Image(
                    bmp.asImageBitmap(),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.FillWidth,
                    colorFilter = if (LocalSettings.current.invert) INVERT_FILTER else null,
                )
                if (!anno.isEmpty()) {
                    Canvas(Modifier.matchParentSize()) { drawPageAnnotations(anno, size.width, size.height) }
                }
            }
        } else {
            Text("Lädt …", color = MaterialTheme.colorScheme.outline)
        }
    }
}

@Composable
fun TwoPageView(
    doc: PdfDoc?,
    page: Int,
    pages: List<Int>,
    renderPx: Int,
    annoRepo: AnnotationRepository? = null,
    pdfKey: String? = null,
    annoVersion: Int = 0,
    onTurn: (Int) -> Unit,
) {
    val half = renderPx / 2
    val leftIdx = pages.getOrElse(page) { -1 }
    val rightIdx = pages.getOrElse(page + 1) { -1 }
    val left = pageBitmap(doc, leftIdx, half)
    val right = pageBitmap(doc, rightIdx, half)
    val leftAnno = rememberPageAnnotations(annoRepo, pdfKey, leftIdx, annoVersion)
    val rightAnno = rememberPageAnnotations(annoRepo, pdfKey, rightIdx, annoVersion)

    Box(
        Modifier
            .fillMaxSize()
            .pointerInput(pages.size) {
                detectTapGestures { pos ->
                    val third = size.width / 3f
                    if (pos.x < third) onTurn(-2) else if (pos.x > size.width - third) onTurn(2)
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
            AnnotatedPageCell(left, leftAnno, Modifier.weight(1f))
            AnnotatedPageCell(right, rightAnno, Modifier.weight(1f))
        }
    }
}

@Composable
fun VerticalScrollView(
    doc: PdfDoc?,
    pages: List<Int>,
    renderPx: Int,
    startPage: Int,
    annoRepo: AnnotationRepository? = null,
    pdfKey: String? = null,
    annoVersion: Int = 0,
    jumpToken: Int = 0,
) {
    val state = remember(jumpToken) {
        LazyListState(firstVisibleItemIndex = startPage.coerceIn(0, maxOf(0, pages.size - 1)))
    }
    LazyColumn(Modifier.fillMaxSize(), state = state) {
        items(pages.size) { i ->
            val pdf = pages[i]
            val bmp = pageBitmap(doc, pdf, renderPx)
            val anno = rememberPageAnnotations(annoRepo, pdfKey, pdf, annoVersion)
            if (bmp != null) {
                AnnotatedPageCell(bmp, anno, Modifier.fillMaxWidth())
            } else {
                Box(Modifier.fillMaxWidth().height(240.dp), contentAlignment = Alignment.Center) {
                    Text("…", color = MaterialTheme.colorScheme.outline)
                }
            }
        }
    }
}

/** All pages side by side, fit to the viewport height — scroll sideways (MAUI 'scroll-h'). */
@Composable
fun HorizontalScrollView(
    doc: PdfDoc?,
    pages: List<Int>,
    renderPx: Int,
    startPage: Int,
    annoRepo: AnnotationRepository? = null,
    pdfKey: String? = null,
    annoVersion: Int = 0,
    jumpToken: Int = 0,
) {
    val state = remember(jumpToken) {
        LazyListState(firstVisibleItemIndex = startPage.coerceIn(0, maxOf(0, pages.size - 1)))
    }
    LazyRow(
        Modifier.fillMaxSize(),
        state = state,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(pages.size) { i ->
            val pdf = pages[i]
            val bmp = pageBitmap(doc, pdf, renderPx)
            val anno = rememberPageAnnotations(annoRepo, pdfKey, pdf, annoVersion)
            if (bmp != null) {
                AnnotatedPageCell(bmp, anno, Modifier.fillMaxHeight())
            } else {
                Box(Modifier.fillMaxHeight().aspectRatio(0.7f), contentAlignment = Alignment.Center) {
                    Text("…", color = MaterialTheme.colorScheme.outline)
                }
            }
        }
    }
}

/**
 * MobileSheets-style half-page mode. Display slots alternate: even slots show a whole page (with a
 * faint fold indicator at the split); odd slots show a COMPOSITE — the next page's top half, a blue
 * fold bar, then the current page's bottom half — so the music flows across the page boundary and
 * each tap-turn advances half a page. The whole view fits the screen. The fold position is dragged
 * with the blue bar and remembered per song (Sheet.halfSplit).
 */
@Composable
fun HalfTurnView(
    doc: PdfDoc?,
    page: Int,
    pages: List<Int>,
    renderPx: Int,
    initialSplit: Float,
    jumpToken: Int = 0,
    onSplitChange: (Float) -> Unit,
) {
    val total = maxOf(1, 2 * pages.size - 1)
    var slot by remember(jumpToken) { mutableIntStateOf((page * 2).coerceIn(0, total - 1)) }
    var split by remember { mutableFloatStateOf(initialSplit.coerceIn(0.15f, 0.85f)) }

    val composite = slot % 2 == 1
    val curIdx = slot / 2
    val nextIdx = curIdx + 1
    val cur = pageBitmap(doc, pages.getOrElse(curIdx) { 0 }, renderPx)
    val next = if (composite && nextIdx < pages.size) pageBitmap(doc, pages[nextIdx], renderPx) else null

    val bar = 6   // blue fold-bar thickness, in composite (bitmap) px

    BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        if (cur == null) {
            Text("Lädt …", color = MaterialTheme.colorScheme.outline)
            return@BoxWithConstraints
        }
        val cw = constraints.maxWidth.toFloat()
        val ch = constraints.maxHeight.toFloat()
        val wA = cur.width
        val hA = cur.height

        val drawComposite = composite && next != null
        val wB = next?.width ?: wA
        val hB = next?.height ?: hA
        val topH = if (drawComposite) (hB * split).roundToInt() else 0
        val aCut = (hA * split).roundToInt()
        val bottomH = hA - aCut
        val w = if (drawComposite) maxOf(wA, wB) else wA
        val h = if (drawComposite) topH + bar + bottomH else hA
        val scale = minOf(cw / w, ch / h)
        val dx = (cw - w * scale) / 2f
        val dy = (ch - h * scale) / 2f
        val xA = (w - wA) / 2
        val xB = (w - wB) / 2

        // Let the drag read the latest geometry without restarting the gesture loop each frame.
        val liveDy = rememberUpdatedState(dy)
        val liveScale = rememberUpdatedState(scale)
        val liveHB = rememberUpdatedState(hB)

        val curImg = cur.asImageBitmap()
        val nextImg = next?.asImageBitmap()
        val invert = LocalSettings.current.invert
        val filter = if (invert) INVERT_FILTER else null

        Canvas(
            Modifier
                .fillMaxSize()
                .pointerInput(total) {
                    detectTapGestures { pos ->
                        val third = size.width / 3f
                        if (pos.x < third) slot = (slot - 1).coerceIn(0, total - 1)
                        else if (pos.x > size.width - third) slot = (slot + 1).coerceIn(0, total - 1)
                    }
                }
                .pointerInput(total, composite) {
                    if (composite) {
                        var sDy = 0f
                        var sScale = 1f
                        var sHB = 1
                        detectVerticalDragGestures(
                            onDragStart = { sDy = liveDy.value; sScale = liveScale.value; sHB = liveHB.value },
                            onVerticalDrag = { change, _ ->
                                val cy = (change.position.y - sDy) / sScale
                                split = (cy / sHB).coerceIn(0.15f, 0.85f)
                            },
                            onDragEnd = { onSplitChange(split) },
                        )
                    }
                },
        ) {
            if (drawComposite && nextImg != null) {
                // next page top → TOP
                drawImage(
                    nextImg,
                    srcOffset = IntOffset(0, 0), srcSize = IntSize(wB, topH),
                    dstOffset = IntOffset((dx + xB * scale).roundToInt(), dy.roundToInt()),
                    dstSize = IntSize((wB * scale).roundToInt(), (topH * scale).roundToInt()),
                    colorFilter = filter,
                )
                // blue fold bar
                drawRect(Color(0xFF1E88E5), topLeft = Offset(dx, dy + topH * scale), size = Size(w * scale, bar * scale))
                // current page bottom → BOTTOM
                drawImage(
                    curImg,
                    srcOffset = IntOffset(0, aCut), srcSize = IntSize(wA, bottomH),
                    dstOffset = IntOffset((dx + xA * scale).roundToInt(), (dy + (topH + bar) * scale).roundToInt()),
                    dstSize = IntSize((wA * scale).roundToInt(), (bottomH * scale).roundToInt()),
                    colorFilter = filter,
                )
            } else {
                // Whole page + a faint fold cue at the split.
                drawImage(
                    curImg,
                    srcOffset = IntOffset(0, 0), srcSize = IntSize(wA, hA),
                    dstOffset = IntOffset(dx.roundToInt(), dy.roundToInt()),
                    dstSize = IntSize((wA * scale).roundToInt(), (hA * scale).roundToInt()),
                    colorFilter = filter,
                )
                drawRect(
                    Color(0x4D1E88E5),
                    topLeft = Offset(dx, dy + (hA * split - bar / 2f) * scale),
                    size = Size(wA * scale, bar * scale),
                )
            }
        }
    }
}
