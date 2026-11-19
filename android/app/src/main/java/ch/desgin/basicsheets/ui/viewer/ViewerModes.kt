package ch.desgin.basicsheets.ui.viewer

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.scrollBy
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
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import ch.desgin.basicsheets.annot.PageAnnotations
import ch.desgin.basicsheets.data.AnnotationRepository
import ch.desgin.basicsheets.data.ViewerButton
import ch.desgin.basicsheets.pdf.MatchBox
import ch.desgin.basicsheets.pdf.PdfDoc
import ch.desgin.basicsheets.ui.settings.LocalSettings
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ensureActive
import kotlin.math.abs
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

/** Crop [src] to the normalized [l,t,r,b] sub-rectangle; returns [src] on any failure. */
private fun cropBitmap(src: Bitmap, l: Float, t: Float, r: Float, b: Float): Bitmap {
    val x = (l * src.width).toInt().coerceIn(0, src.width - 1)
    val y = (t * src.height).toInt().coerceIn(0, src.height - 1)
    val w = ((r - l) * src.width).toInt().coerceIn(1, src.width - x)
    val h = ((b - t) * src.height).toInt().coerceIn(1, src.height - y)
    return runCatching { Bitmap.createBitmap(src, x, y, w, h) }.getOrDefault(src)
}

/** Crop [src] to [l,t,r,b] only when that rectangle is a real sub-page; else return [src] unchanged. */
private fun maybeCrop(src: Bitmap, l: Float, t: Float, r: Float, b: Float): Bitmap =
    if (l > 0.002f || t > 0.002f || r < 0.998f || b < 0.998f) cropBitmap(src, l, t, r, b) else src

/** Draw the blue jump-point (Sprungpunkt) rings at their full-page-normalized source positions. */
private fun DrawScope.drawLinkMarkers(links: List<ViewerButton>, w: Float, h: Float) {
    for (lp in links) {
        if (lp.sourceNx < 0) continue
        val center = Offset(lp.sourceNx.toFloat() * w, lp.sourceNy.toFloat() * h)
        val rad = w * 0.022f
        drawCircle(Color(0xFF1565C0).copy(alpha = 0.22f), rad, center)
        drawCircle(Color(0xFF1565C0), rad, center, style = Stroke(width = w * 0.006f))
    }
}

/**
 * Draw a page's annotations + search highlight + jump-point markers (all full-page normalized
 * [0,1]) onto a surface of size [w]x[h] that shows only the crop rectangle, remapping into the
 * cropped frame (identity when uncropped). The caller has already translated/clipped to where the
 * page sits. Shared by every reading mode so the crop remap is defined once.
 */
private fun DrawScope.drawCroppedOverlay(
    anno: PageAnnotations,
    hl: List<MatchBox>,
    w: Float,
    h: Float,
    cropL: Float,
    cropT: Float,
    cropR: Float,
    cropB: Float,
    links: List<ViewerButton> = emptyList(),
) {
    if (cropL > 0.002f || cropT > 0.002f || cropR < 0.998f || cropB < 0.998f) {
        withTransform({
            translate(-cropL * w / (cropR - cropL), -cropT * h / (cropB - cropT))
            scale(1f / (cropR - cropL), 1f / (cropB - cropT), pivot = Offset.Zero)
        }) {
            if (hl.isNotEmpty()) drawSearchHighlight(hl, w, h)
            if (!anno.isEmpty()) drawPageAnnotations(anno, w, h)
            if (links.isNotEmpty()) drawLinkMarkers(links, w, h)
        }
    } else {
        if (hl.isNotEmpty()) drawSearchHighlight(hl, w, h)
        if (!anno.isEmpty()) drawPageAnnotations(anno, w, h)
        if (links.isNotEmpty()) drawLinkMarkers(links, w, h)
    }
}

/** Lay out content rotated 90°/270°, swapping width/height so the rotated page fits the parent. */
private fun Modifier.rotate90Fit(rotation: Int): Modifier = layout { measurable, constraints ->
    val placeable = measurable.measure(
        Constraints(
            minWidth = constraints.minHeight, maxWidth = constraints.maxHeight,
            minHeight = constraints.minWidth, maxHeight = constraints.maxWidth,
        ),
    )
    layout(placeable.height, placeable.width) {
        placeable.placeRelativeWithLayer(
            x = (placeable.height - placeable.width) / 2,
            y = (placeable.width - placeable.height) / 2,
        ) { rotationZ = rotation.toFloat() }
    }
}

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
private fun AnnotatedPageCell(
    bmp: Bitmap?,
    anno: PageAnnotations,
    modifier: Modifier,
    highlight: List<MatchBox> = emptyList(),
    cropL: Float = 0f,
    cropT: Float = 0f,
    cropR: Float = 1f,
    cropB: Float = 1f,
    rotation: Int = 0,
    linkPoints: List<ViewerButton> = emptyList(),
    onMarkerTap: (ViewerButton) -> Unit = {},
) {
    if (bmp == null) return
    // Apply the per-song crop (same rectangle every page) by trimming the bitmap; keyed memoize so
    // we re-crop only when the page bitmap or the crop rectangle changes. Identity-equal to [bmp]
    // when uncropped, so the common (uncropped) path is unchanged.
    val shown = remember(bmp, cropL, cropT, cropR, cropB) { maybeCrop(bmp, cropL, cropT, cropR, cropB) }
    val swap = rotation == 90 || rotation == 270
    val dispAspect = if (swap) shown.height.toFloat() / shown.width else shown.width.toFloat() / shown.height
    Box(
        modifier
            .aspectRatio(dispAspect)
            // Jump-point taps: hit-test only when the page has markers, and consume ONLY on a hit so
            // non-marker taps still fall through to the mode's page-turn / scroll handling. Maps the
            // tap into full-page-normalized space (crop inverse; 180° flip). 90/270 taps are mapped
            // unrotated — the known rotation-aware-tap gap — though markers still draw rotated.
            .then(
                if (linkPoints.isEmpty()) Modifier
                else Modifier.pointerInput(linkPoints, cropL, cropT, cropR, cropB, rotation) {
                    awaitEachGesture {
                        awaitFirstDown(requireUnconsumed = false)
                        val up = waitForUpOrCancellation() ?: return@awaitEachGesture
                        val sz = size
                        if (sz.width <= 0 || sz.height <= 0) return@awaitEachGesture
                        var dx = up.position.x / sz.width
                        var dy = up.position.y / sz.height
                        if (rotation == 180) { dx = 1f - dx; dy = 1f - dy }
                        val fnx = cropL + dx * (cropR - cropL)
                        val fny = cropT + dy * (cropB - cropT)
                        val hit = linkPoints.firstOrNull {
                            it.sourceNx >= 0 &&
                                abs(it.sourceNx.toFloat() - fnx) < 0.045f &&
                                abs(it.sourceNy.toFloat() - fny) < 0.045f
                        }
                        if (hit != null) { up.consume(); onMarkerTap(hit) }
                    }
                },
            ),
    ) {
        // Whole cell rotates (page + overlay together) so annotations ride along without a remap —
        // same model as SinglePageView; 90/270 swap width/height via rotate90Fit to keep fitting.
        Box(if (swap) Modifier.rotate90Fit(rotation) else Modifier.fillMaxSize().rotate(rotation.toFloat())) {
            Image(
                shown.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit,
                colorFilter = if (LocalSettings.current.invert) INVERT_FILTER else null,
            )
            if (!anno.isEmpty() || highlight.isNotEmpty() || linkPoints.isNotEmpty()) {
                Canvas(Modifier.matchParentSize()) {
                    drawCroppedOverlay(anno, highlight, size.width, size.height, cropL, cropT, cropR, cropB, linkPoints)
                }
            }
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
    highlightPage: Int = -1,
    highlightBoxes: List<MatchBox> = emptyList(),
    linkPoints: List<ViewerButton> = emptyList(),
    editLinks: Boolean = false,
    onMarkerTap: (ViewerButton) -> Unit = {},
    onEmptyTap: (Float, Float) -> Unit = { _, _ -> },
    cropL: Float = 0f,
    cropT: Float = 0f,
    cropR: Float = 1f,
    cropB: Float = 1f,
    rotation: Int = 0,
    onTurn: (Int) -> Unit,
) {
    val pdf = pages.getOrElse(page) { 0 }
    val hl = if (pdf == highlightPage) highlightBoxes else emptyList()
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    LaunchedEffect(page) { scale = 1f; offset = Offset.Zero }

    val transform = rememberTransformableState { zoom, pan, _ ->
        scale = (scale * zoom).coerceIn(1f, 5f)
        offset = if (scale > 1f) offset + pan else Offset.Zero
    }
    val full = pageBitmap(doc, pdf, renderPx)
    val cropped = full != null && (cropL > 0.002f || cropT > 0.002f || cropR < 0.998f || cropB < 0.998f)
    val bmp = if (cropped && full != null) remember(full, cropL, cropT, cropR, cropB) { cropBitmap(full, cropL, cropT, cropR, cropB) } else full
    val bmpState = rememberUpdatedState(bmp)
    val anno = rememberPageAnnotations(annoRepo, pdfKey, pdf, annoVersion)

    Box(
        Modifier
            .fillMaxSize()
            .transformable(transform)
            .pointerInput(editLinks, linkPoints) {
                detectTapGestures { pos ->
                    if (scale > 1.05f) return@detectTapGestures
                    val b = bmpState.value
                    if (b != null) {
                        // Map the full-screen tap into page-normalized [0,1] (page is fillMaxWidth, centred).
                        val pageH = size.width.toFloat() * b.height / b.width
                        val pageTop = (size.height - pageH) / 2f
                        val nx = pos.x / size.width.toFloat()
                        val ny = (pos.y - pageTop) / pageH
                        if (nx in 0f..1f && ny in 0f..1f) {
                            val hit = linkPoints.firstOrNull {
                                it.sourceNx >= 0 && abs(it.sourceNx.toFloat() - nx) < 0.045f && abs(it.sourceNy.toFloat() - ny) < 0.045f
                            }
                            if (hit != null) { onMarkerTap(hit); return@detectTapGestures }
                            if (editLinks) { onEmptyTap(nx, ny); return@detectTapGestures }
                        }
                    }
                    val third = size.width / 3f
                    if (pos.x < third) onTurn(-1) else if (pos.x > size.width - third) onTurn(1)
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        if (bmp != null) {
            val swap = rotation == 90 || rotation == 270
            val dispAspect = if (swap) bmp.height.toFloat() / bmp.width else bmp.width.toFloat() / bmp.height
            Box(
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(dispAspect)
                    .graphicsLayer(scaleX = scale, scaleY = scale, translationX = offset.x, translationY = offset.y),
            ) {
              Box(if (swap) Modifier.rotate90Fit(rotation) else Modifier.fillMaxSize().rotate(rotation.toFloat())) {
                Image(
                    bmp.asImageBitmap(),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.FillWidth,
                    colorFilter = if (LocalSettings.current.invert) INVERT_FILTER else null,
                )
                if (!anno.isEmpty() || hl.isNotEmpty() || linkPoints.isNotEmpty()) {
                    Canvas(Modifier.matchParentSize()) {
                        // Annotations/highlight/markers are full-page normalized; when cropped, map
                        // them into the cropped display (identity when not cropped).
                        withTransform({
                            translate(-cropL * size.width / (cropR - cropL), -cropT * size.height / (cropB - cropT))
                            scale(1f / (cropR - cropL), 1f / (cropB - cropT), pivot = Offset.Zero)
                        }) {
                            if (hl.isNotEmpty()) drawSearchHighlight(hl, size.width, size.height)
                            if (!anno.isEmpty()) drawPageAnnotations(anno, size.width, size.height)
                            if (linkPoints.isNotEmpty()) drawLinkMarkers(linkPoints, size.width, size.height)
                        }
                    }
                }
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
    highlightPage: Int = -1,
    highlightBoxes: List<MatchBox> = emptyList(),
    cropL: Float = 0f,
    cropT: Float = 0f,
    cropR: Float = 1f,
    cropB: Float = 1f,
    rotation: Int = 0,
    linkPoints: List<ViewerButton> = emptyList(),
    onMarkerTap: (ViewerButton) -> Unit = {},
    onTurn: (Int) -> Unit,
) {
    val half = renderPx / 2
    val leftIdx = pages.getOrElse(page) { -1 }
    val rightIdx = pages.getOrElse(page + 1) { -1 }
    val left = pageBitmap(doc, leftIdx, half)
    val right = pageBitmap(doc, rightIdx, half)
    val leftAnno = rememberPageAnnotations(annoRepo, pdfKey, leftIdx, annoVersion)
    val rightAnno = rememberPageAnnotations(annoRepo, pdfKey, rightIdx, annoVersion)
    val leftLinks = linkPoints.filter { it.sourcePage - 1 == leftIdx && it.sourceNx >= 0 }
    val rightLinks = linkPoints.filter { it.sourcePage - 1 == rightIdx && it.sourceNx >= 0 }

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
            AnnotatedPageCell(
                left, leftAnno, Modifier.weight(1f),
                if (leftIdx == highlightPage) highlightBoxes else emptyList(),
                cropL, cropT, cropR, cropB, rotation, leftLinks, onMarkerTap,
            )
            AnnotatedPageCell(
                right, rightAnno, Modifier.weight(1f),
                if (rightIdx == highlightPage) highlightBoxes else emptyList(),
                cropL, cropT, cropR, cropB, rotation, rightLinks, onMarkerTap,
            )
        }
    }
}

/** Drives [state] forward at `scrollSpeed` px/s while [autoScroll] is on; survives user scroll nudges. */
@Composable
private fun AutoScrollEffect(state: LazyListState, autoScroll: Boolean, scrollSpeed: Int) {
    val speed = if (scrollSpeed > 0) scrollSpeed.toFloat() else 50f
    LaunchedEffect(autoScroll, speed, state) {
        if (!autoScroll) return@LaunchedEffect
        var last = 0L
        while (true) {
            val now = withFrameNanos { it }
            if (last != 0L && state.canScrollForward) {
                try {
                    state.scrollBy(speed * (now - last) / 1_000_000_000f)
                } catch (e: CancellationException) {
                    ensureActive()   // real cancellation → rethrow; a user scroll nudge → keep going
                }
            }
            last = now
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
    highlightPage: Int = -1,
    highlightBoxes: List<MatchBox> = emptyList(),
    autoScroll: Boolean = false,
    scrollSpeed: Int = 0,
    cropL: Float = 0f,
    cropT: Float = 0f,
    cropR: Float = 1f,
    cropB: Float = 1f,
    rotation: Int = 0,
    linkPoints: List<ViewerButton> = emptyList(),
    onMarkerTap: (ViewerButton) -> Unit = {},
) {
    val state = remember(jumpToken) {
        LazyListState(firstVisibleItemIndex = startPage.coerceIn(0, maxOf(0, pages.size - 1)))
    }
    AutoScrollEffect(state, autoScroll, scrollSpeed)
    LazyColumn(Modifier.fillMaxSize(), state = state) {
        items(pages.size) { i ->
            val pdf = pages[i]
            val bmp = pageBitmap(doc, pdf, renderPx)
            val anno = rememberPageAnnotations(annoRepo, pdfKey, pdf, annoVersion)
            val links = remember(linkPoints, pdf) { linkPoints.filter { it.sourcePage - 1 == pdf && it.sourceNx >= 0 } }
            if (bmp != null) {
                AnnotatedPageCell(bmp, anno, Modifier.fillMaxWidth(), if (pdf == highlightPage) highlightBoxes else emptyList(), cropL, cropT, cropR, cropB, rotation, links, onMarkerTap)
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
    highlightPage: Int = -1,
    highlightBoxes: List<MatchBox> = emptyList(),
    autoScroll: Boolean = false,
    scrollSpeed: Int = 0,
    cropL: Float = 0f,
    cropT: Float = 0f,
    cropR: Float = 1f,
    cropB: Float = 1f,
    rotation: Int = 0,
    linkPoints: List<ViewerButton> = emptyList(),
    onMarkerTap: (ViewerButton) -> Unit = {},
) {
    val state = remember(jumpToken) {
        LazyListState(firstVisibleItemIndex = startPage.coerceIn(0, maxOf(0, pages.size - 1)))
    }
    AutoScrollEffect(state, autoScroll, scrollSpeed)
    LazyRow(
        Modifier.fillMaxSize(),
        state = state,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(pages.size) { i ->
            val pdf = pages[i]
            val bmp = pageBitmap(doc, pdf, renderPx)
            val anno = rememberPageAnnotations(annoRepo, pdfKey, pdf, annoVersion)
            val links = remember(linkPoints, pdf) { linkPoints.filter { it.sourcePage - 1 == pdf && it.sourceNx >= 0 } }
            if (bmp != null) {
                AnnotatedPageCell(bmp, anno, Modifier.fillMaxHeight(), if (pdf == highlightPage) highlightBoxes else emptyList(), cropL, cropT, cropR, cropB, rotation, links, onMarkerTap)
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
    annoRepo: AnnotationRepository? = null,
    pdfKey: String? = null,
    annoVersion: Int = 0,
    highlightPage: Int = -1,
    highlightBoxes: List<MatchBox> = emptyList(),
    cropL: Float = 0f,
    cropT: Float = 0f,
    cropR: Float = 1f,
    cropB: Float = 1f,
    linkPoints: List<ViewerButton> = emptyList(),
    onMarkerTap: (ViewerButton) -> Unit = {},
    onSplitChange: (Float) -> Unit,
) {
    val total = maxOf(1, 2 * pages.size - 1)
    var slot by remember(jumpToken) { mutableIntStateOf((page * 2).coerceIn(0, total - 1)) }
    var split by remember { mutableFloatStateOf(initialSplit.coerceIn(0.15f, 0.85f)) }

    val composite = slot % 2 == 1
    val curIdx = slot / 2
    val nextIdx = curIdx + 1
    val curFull = pageBitmap(doc, pages.getOrElse(curIdx) { 0 }, renderPx)
    val nextFull = if (composite && nextIdx < pages.size) pageBitmap(doc, pages[nextIdx], renderPx) else null
    val cur = remember(curFull, cropL, cropT, cropR, cropB) { curFull?.let { maybeCrop(it, cropL, cropT, cropR, cropB) } }
    val next = remember(nextFull, cropL, cropT, cropR, cropB) { nextFull?.let { maybeCrop(it, cropL, cropT, cropR, cropB) } }
    val curAnno = rememberPageAnnotations(annoRepo, pdfKey, pages.getOrElse(curIdx) { -1 }, annoVersion)
    val nextAnno = rememberPageAnnotations(
        annoRepo, pdfKey, if (composite && nextIdx < pages.size) pages[nextIdx] else -1, annoVersion,
    )
    val curPdf = pages.getOrElse(curIdx) { -1 }
    val nextPdf = if (composite && nextIdx < pages.size) pages[nextIdx] else -1
    val curHl = if (curPdf == highlightPage) highlightBoxes else emptyList()
    val nextHl = if (nextPdf == highlightPage) highlightBoxes else emptyList()
    val curLinks = if (linkPoints.isEmpty()) emptyList() else linkPoints.filter { it.sourcePage - 1 == curPdf && it.sourceNx >= 0 }
    val nextLinks = if (linkPoints.isEmpty()) emptyList() else linkPoints.filter { it.sourcePage - 1 == nextPdf && it.sourceNx >= 0 }

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

        // Jump-point hit test over the composite layout, always reading the current geometry/split.
        // Each half maps a canvas tap to its page's crop-display frame, then to full-page-normalized
        // (crop inverse) to match the stored marker. No rotation here — half-turn is unrotated.
        val liveHit = rememberUpdatedState<(Offset) -> ViewerButton?> hit@{ pos ->
            if (curLinks.isEmpty() && nextLinks.isEmpty()) return@hit null
            fun match(markers: List<ViewerButton>, dxn: Float, dyn: Float): ViewerButton? {
                if (dxn !in 0f..1f || dyn !in 0f..1f) return null
                val fnx = cropL + dxn * (cropR - cropL)
                val fny = cropT + dyn * (cropB - cropT)
                return markers.firstOrNull {
                    abs(it.sourceNx.toFloat() - fnx) < 0.045f && abs(it.sourceNy.toFloat() - fny) < 0.045f
                }
            }
            if (drawComposite) {
                // top half shows the next page's [0..split]; bottom half the current page's [split..1]
                val by = dy + (topH + bar) * scale
                if (pos.y < by) {
                    match(nextLinks, (pos.x - (dx + xB * scale)) / (wB * scale), (pos.y - dy) / (hB * scale))
                } else {
                    match(curLinks, (pos.x - (dx + xA * scale)) / (wA * scale), (pos.y - by + aCut * scale) / (hA * scale))
                }
            } else {
                match(curLinks, (pos.x - dx) / (wA * scale), (pos.y - dy) / (hA * scale))
            }
        }

        val curImg = cur.asImageBitmap()
        val nextImg = next?.asImageBitmap()
        val invert = LocalSettings.current.invert
        val filter = if (invert) INVERT_FILTER else null

        Canvas(
            Modifier
                .fillMaxSize()
                .pointerInput(total) {
                    detectTapGestures { pos ->
                        liveHit.value(pos)?.let { onMarkerTap(it); return@detectTapGestures }
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
                // next page's search highlight + annotations + markers, clipped to the visible top half
                if (!nextAnno.isEmpty() || nextHl.isNotEmpty() || nextLinks.isNotEmpty()) {
                    val tx = dx + xB * scale
                    clipRect(tx, dy, tx + wB * scale, dy + topH * scale) {
                        translate(tx, dy) {
                            drawCroppedOverlay(nextAnno, nextHl, wB * scale, hB * scale, cropL, cropT, cropR, cropB, nextLinks)
                        }
                    }
                }
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
                // current page's search highlight + annotations + markers, clipped to the visible bottom half
                if (!curAnno.isEmpty() || curHl.isNotEmpty() || curLinks.isNotEmpty()) {
                    val bx = dx + xA * scale
                    val by = dy + (topH + bar) * scale
                    clipRect(bx, by, bx + wA * scale, by + bottomH * scale) {
                        translate(bx, by - aCut * scale) {
                            drawCroppedOverlay(curAnno, curHl, wA * scale, hA * scale, cropL, cropT, cropR, cropB, curLinks)
                        }
                    }
                }
            } else {
                // Whole page + a faint fold cue at the split.
                drawImage(
                    curImg,
                    srcOffset = IntOffset(0, 0), srcSize = IntSize(wA, hA),
                    dstOffset = IntOffset(dx.roundToInt(), dy.roundToInt()),
                    dstSize = IntSize((wA * scale).roundToInt(), (hA * scale).roundToInt()),
                    colorFilter = filter,
                )
                if (!curAnno.isEmpty() || curHl.isNotEmpty() || curLinks.isNotEmpty()) {
                    translate(dx, dy) {
                        drawCroppedOverlay(curAnno, curHl, wA * scale, hA * scale, cropL, cropT, cropR, cropB, curLinks)
                    }
                }
                drawRect(
                    Color(0x4D1E88E5),
                    topLeft = Offset(dx, dy + (hA * split - bar / 2f) * scale),
                    size = Size(wA * scale, bar * scale),
                )
            }
        }
    }
}
