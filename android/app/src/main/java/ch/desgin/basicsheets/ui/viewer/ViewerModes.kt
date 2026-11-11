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
import kotlin.math.roundToInt

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
        value = if (repo != null && !pdfKey.isNullOrEmpty())
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
        Image(bmp.asImageBitmap(), contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
        if (!anno.isEmpty()) {
            Canvas(Modifier.matchParentSize()) { drawPageAnnotations(anno, size.width, size.height) }
        }
    }
}

@Composable
fun SinglePageView(
    doc: PdfDoc?,
    page: Int,
    pageCount: Int,
    renderPx: Int,
    annoRepo: AnnotationRepository? = null,
    pdfKey: String? = null,
    annoVersion: Int = 0,
    onTurn: (Int) -> Unit,
) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    LaunchedEffect(page) { scale = 1f; offset = Offset.Zero }

    val transform = rememberTransformableState { zoom, pan, _ ->
        scale = (scale * zoom).coerceIn(1f, 5f)
        offset = if (scale > 1f) offset + pan else Offset.Zero
    }
    val bmp = pageBitmap(doc, page, renderPx)
    val anno = rememberPageAnnotations(annoRepo, pdfKey, page, annoVersion)

    Box(
        Modifier
            .fillMaxSize()
            .transformable(transform)
            .pointerInput(pageCount) {
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
                Image(bmp.asImageBitmap(), contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.FillWidth)
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
    pageCount: Int,
    renderPx: Int,
    annoRepo: AnnotationRepository? = null,
    pdfKey: String? = null,
    annoVersion: Int = 0,
    onTurn: (Int) -> Unit,
) {
    val half = renderPx / 2
    val left = pageBitmap(doc, page, half)
    val right = if (page + 1 < pageCount) pageBitmap(doc, page + 1, half) else null
    val leftAnno = rememberPageAnnotations(annoRepo, pdfKey, page, annoVersion)
    val rightAnno = rememberPageAnnotations(annoRepo, pdfKey, page + 1, annoVersion)

    Box(
        Modifier
            .fillMaxSize()
            .pointerInput(pageCount) {
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
    pageCount: Int,
    renderPx: Int,
    startPage: Int,
    annoRepo: AnnotationRepository? = null,
    pdfKey: String? = null,
    annoVersion: Int = 0,
    jumpToken: Int = 0,
) {
    val state = remember(jumpToken) {
        LazyListState(firstVisibleItemIndex = startPage.coerceIn(0, maxOf(0, pageCount - 1)))
    }
    LazyColumn(Modifier.fillMaxSize(), state = state) {
        items(pageCount) { i ->
            val bmp = pageBitmap(doc, i, renderPx)
            val anno = rememberPageAnnotations(annoRepo, pdfKey, i, annoVersion)
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
    pageCount: Int,
    renderPx: Int,
    startPage: Int,
    annoRepo: AnnotationRepository? = null,
    pdfKey: String? = null,
    annoVersion: Int = 0,
    jumpToken: Int = 0,
) {
    val state = remember(jumpToken) {
        LazyListState(firstVisibleItemIndex = startPage.coerceIn(0, maxOf(0, pageCount - 1)))
    }
    LazyRow(
        Modifier.fillMaxSize(),
        state = state,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(pageCount) { i ->
            val bmp = pageBitmap(doc, i, renderPx)
            val anno = rememberPageAnnotations(annoRepo, pdfKey, i, annoVersion)
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
 * with the blue bar and remembered per song (Sheet.halfSplit). PDF-only for now; annotation
 * compositing arrives with the remaining annotation tools.
 */
@Composable
fun HalfTurnView(
    doc: PdfDoc?,
    page: Int,
    pageCount: Int,
    renderPx: Int,
    initialSplit: Float,
    jumpToken: Int = 0,
    onSplitChange: (Float) -> Unit,
) {
    val total = maxOf(1, 2 * pageCount - 1)
    var slot by remember(jumpToken) { mutableIntStateOf((page * 2).coerceIn(0, total - 1)) }
    var split by remember { mutableFloatStateOf(initialSplit.coerceIn(0.15f, 0.85f)) }

    val composite = slot % 2 == 1
    val curIdx = slot / 2
    val nextIdx = curIdx + 1
    val cur = pageBitmap(doc, curIdx, renderPx)
    val next = if (composite && nextIdx < pageCount) pageBitmap(doc, nextIdx, renderPx) else null

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
                )
                // blue fold bar
                drawRect(Color(0xFF1E88E5), topLeft = Offset(dx, dy + topH * scale), size = Size(w * scale, bar * scale))
                // current page bottom → BOTTOM
                drawImage(
                    curImg,
                    srcOffset = IntOffset(0, aCut), srcSize = IntSize(wA, bottomH),
                    dstOffset = IntOffset((dx + xA * scale).roundToInt(), (dy + (topH + bar) * scale).roundToInt()),
                    dstSize = IntSize((wA * scale).roundToInt(), (bottomH * scale).roundToInt()),
                )
            } else {
                // Whole page + a faint fold cue at the split.
                drawImage(
                    curImg,
                    srcOffset = IntOffset(0, 0), srcSize = IntSize(wA, hA),
                    dstOffset = IntOffset(dx.roundToInt(), dy.roundToInt()),
                    dstSize = IntSize((wA * scale).roundToInt(), (hA * scale).roundToInt()),
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
