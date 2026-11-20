package ch.desgin.basicsheets.ui.viewer

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
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
import androidx.compose.ui.geometry.Rect
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

/** The whole page — no crop. A per-page crop lookup returns this when a page has no crop. */
val FULL_CROP = Rect(0f, 0f, 1f, 1f)

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

/** Crop [src] to the normalized l,t,r,b sub-rectangle; returns [src] on any failure. */
private fun cropBitmap(src: Bitmap, l: Float, t: Float, r: Float, b: Float): Bitmap {
    val x = (l * src.width).toInt().coerceIn(0, src.width - 1)
    val y = (t * src.height).toInt().coerceIn(0, src.height - 1)
    val w = ((r - l) * src.width).toInt().coerceIn(1, src.width - x)
    val h = ((b - t) * src.height).toInt().coerceIn(1, src.height - y)
    return runCatching { Bitmap.createBitmap(src, x, y, w, h) }.getOrDefault(src)
}

/** Crop [src] to l,t,r,b only when that rectangle is a real sub-page; else return [src] unchanged. */
private fun maybeCrop(src: Bitmap, l: Float, t: Float, r: Float, b: Float): Bitmap =
    if (l > 0.002f || t > 0.002f || r < 0.998f || b < 0.998f) cropBitmap(src, l, t, r, b) else src

/** Rotate [src] clockwise by [rotation] (0/90/180/270); returns [src] for 0 or on any failure. */
private fun rotateBitmap(src: Bitmap, rotation: Int): Bitmap {
    val r = ((rotation % 360) + 360) % 360
    if (r == 0) return src
    val m = android.graphics.Matrix().apply { postRotate(r.toFloat()) }
    return runCatching { Bitmap.createBitmap(src, 0, 0, src.width, src.height, m, true) }.getOrDefault(src)
}

/**
 * Invert a page's display rotation: map a tap at display-normalized ([dxn],[dyn]) in the rotated
 * display frame back to normalized coords in the un-rotated page. [rotation] is clockwise
 * 0/90/180/270 (the same sense as `Modifier.rotate`), so this undoes what the page cell applied.
 */
private fun unrotateNorm(dxn: Float, dyn: Float, rotation: Int): Pair<Float, Float> = when (rotation) {
    90 -> dyn to (1f - dxn)
    180 -> (1f - dxn) to (1f - dyn)
    270 -> (1f - dyn) to dxn
    else -> dxn to dyn
}

/** Parse a stored "#RRGGBB" jump-point colour; falls back to blue on any bad/empty value. */
fun linkColor(hex: String): Color =
    runCatching { Color(android.graphics.Color.parseColor(hex)) }.getOrDefault(Color(0xFF1565C0))

/** Draw the jump-point (Sprungpunkt) rings, each in its own colour, at their normalized positions. */
private fun DrawScope.drawLinkMarkers(links: List<ViewerButton>, w: Float, h: Float) {
    for (lp in links) {
        if (lp.sourceNx < 0) continue
        val center = Offset(lp.sourceNx.toFloat() * w, lp.sourceNy.toFloat() * h)
        val rad = w * 0.022f
        val col = linkColor(lp.color)
        drawCircle(col.copy(alpha = 0.22f), rad, center)
        drawCircle(col, rad, center, style = Stroke(width = w * 0.006f))
    }
}

/**
 * Draw a page's annotations + search highlight + jump-point markers (all full-page normalized
 * 0..1) onto a surface of size [w]x[h] that shows only the crop rectangle, remapping into the
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

/**
 * Like [drawCroppedOverlay] but for a page whose BITMAP was rotated clockwise by [rotation]: author
 * the overlay in the un-rotated cropped page frame, then rotate it to fill the rotated
 * [surfW]x[surfH] surface — so annotations/highlight/markers ride the rotation exactly as the page
 * pixels do. Used by half-turn mode, which rotates its page pixels rather than the whole cell.
 */
private fun DrawScope.drawRotatedCroppedOverlay(
    anno: PageAnnotations,
    hl: List<MatchBox>,
    surfW: Float,
    surfH: Float,
    rotation: Int,
    cropL: Float,
    cropT: Float,
    cropR: Float,
    cropB: Float,
    links: List<ViewerButton> = emptyList(),
) {
    if (((rotation % 360) + 360) % 360 == 0) {
        drawCroppedOverlay(anno, hl, surfW, surfH, cropL, cropT, cropR, cropB, links)
        return
    }
    // Un-rotated page frame: for 90/270 its width/height are the rotated surface's swapped.
    val swap = rotation == 90 || rotation == 270
    val ucW = if (swap) surfH else surfW
    val ucH = if (swap) surfW else surfH
    withTransform({
        translate((surfW - ucW) / 2f, (surfH - ucH) / 2f)
        rotate(rotation.toFloat(), pivot = Offset(ucW / 2f, ucH / 2f))
    }) {
        drawCroppedOverlay(anno, hl, ucW, ucH, cropL, cropT, cropR, cropB, links)
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
            // tap into full-page-normalized space by undoing the page rotation (any of 0/90/180/270)
            // then the crop, matching where the markers are drawn.
            .then(
                if (linkPoints.isEmpty()) Modifier
                else Modifier.pointerInput(linkPoints, cropL, cropT, cropR, cropB, rotation) {
                    awaitEachGesture {
                        awaitFirstDown(requireUnconsumed = false)
                        val up = waitForUpOrCancellation() ?: return@awaitEachGesture
                        val sz = size
                        if (sz.width <= 0 || sz.height <= 0) return@awaitEachGesture
                        val (u, v) = unrotateNorm(up.position.x / sz.width, up.position.y / sz.height, rotation)
                        val fnx = cropL + u * (cropR - cropL)
                        val fny = cropT + v * (cropB - cropT)
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
            .pointerInput(Unit) {
                // Horizontal swipe turns the page (only when not zoomed; non-consuming, so pinch/pan/tap are untouched).
                awaitEachGesture {
                    val d0 = awaitFirstDown(requireUnconsumed = false)
                    var dx = 0f; var dy = 0f; var multi = false
                    while (true) {
                        val ev = awaitPointerEvent()
                        if (ev.changes.size > 1) multi = true
                        ev.changes.firstOrNull { it.id == d0.id }?.let { dx = it.position.x - d0.position.x; dy = it.position.y - d0.position.y }
                        if (ev.changes.all { !it.pressed }) break
                    }
                    if (!multi && scale <= 1.05f && abs(dx) > size.width * 0.18f && abs(dx) > abs(dy) * 1.5f) onTurn(if (dx < 0) 1 else -1)
                }
            }
            .pointerInput(editLinks, linkPoints, rotation, cropL, cropT, cropR, cropB) {
                detectTapGestures(
                    onDoubleTap = { pos ->
                        if (scale > 1.05f) {
                            scale = 1f; offset = Offset.Zero
                        } else {
                            val z = 2.5f
                            val center = Offset(size.width / 2f, size.height / 2f)
                            scale = z
                            offset = (center - pos) * z
                        }
                    },
                ) { pos ->
                    if (scale > 1.05f) return@detectTapGestures
                    val b = bmpState.value
                    if (b != null) {
                        // Map the full-screen tap into full-page-normalized 0..1. The page is
                        // fillMaxWidth and centred; its displayed aspect swaps for 90/270. Undo the
                        // rotation, then the crop, so the tap lands where the drawn markers are.
                        val swap = rotation == 90 || rotation == 270
                        val pageH = size.width.toFloat() *
                            if (swap) b.width.toFloat() / b.height else b.height.toFloat() / b.width
                        val pageTop = (size.height - pageH) / 2f
                        val dxn = pos.x / size.width.toFloat()
                        val dyn = (pos.y - pageTop) / pageH
                        if (dxn in 0f..1f && dyn in 0f..1f) {
                            val (u, v) = unrotateNorm(dxn, dyn, rotation)
                            val nx = cropL + u * (cropR - cropL)
                            val ny = cropT + v * (cropB - cropT)
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
    cropFor: (Int) -> Rect = { FULL_CROP },
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
    val lc = cropFor(leftIdx)
    val rc = cropFor(rightIdx)
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    LaunchedEffect(page) { scale = 1f; offset = Offset.Zero }
    val transform = rememberTransformableState { zoom, pan, _ ->
        scale = (scale * zoom).coerceIn(1f, 5f)
        offset = if (scale > 1f) offset + pan else Offset.Zero
    }

    Box(
        Modifier
            .fillMaxSize()
            .transformable(transform)
            .pointerInput(Unit) {
                // Horizontal swipe turns two pages (only when not zoomed; non-consuming).
                awaitEachGesture {
                    val d0 = awaitFirstDown(requireUnconsumed = false)
                    var dx = 0f; var dy = 0f; var multi = false
                    while (true) {
                        val ev = awaitPointerEvent()
                        if (ev.changes.size > 1) multi = true
                        ev.changes.firstOrNull { it.id == d0.id }?.let { dx = it.position.x - d0.position.x; dy = it.position.y - d0.position.y }
                        if (ev.changes.all { !it.pressed }) break
                    }
                    if (!multi && scale <= 1.05f && abs(dx) > size.width * 0.18f && abs(dx) > abs(dy) * 1.5f) onTurn(if (dx < 0) 2 else -2)
                }
            }
            .pointerInput(pages.size) {
                detectTapGestures(
                    onDoubleTap = { pos ->
                        if (scale > 1.05f) {
                            scale = 1f; offset = Offset.Zero
                        } else {
                            val z = 2.5f
                            val center = Offset(size.width / 2f, size.height / 2f)
                            scale = z
                            offset = (center - pos) * z
                        }
                    },
                ) { pos ->
                    if (scale > 1.05f) return@detectTapGestures
                    val third = size.width / 3f
                    if (pos.x < third) onTurn(-2) else if (pos.x > size.width - third) onTurn(2)
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .graphicsLayer(scaleX = scale, scaleY = scale, translationX = offset.x, translationY = offset.y),
            horizontalArrangement = Arrangement.Center,
        ) {
            AnnotatedPageCell(
                left, leftAnno, Modifier.weight(1f),
                if (leftIdx == highlightPage) highlightBoxes else emptyList(),
                lc.left, lc.top, lc.right, lc.bottom, rotation, leftLinks, onMarkerTap,
            )
            AnnotatedPageCell(
                right, rightAnno, Modifier.weight(1f),
                if (rightIdx == highlightPage) highlightBoxes else emptyList(),
                rc.left, rc.top, rc.right, rc.bottom, rotation, rightLinks, onMarkerTap,
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
    cropFor: (Int) -> Rect = { FULL_CROP },
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
            val c = cropFor(pdf)
            if (bmp != null) {
                AnnotatedPageCell(bmp, anno, Modifier.fillMaxWidth(), if (pdf == highlightPage) highlightBoxes else emptyList(), c.left, c.top, c.right, c.bottom, rotation, links, onMarkerTap)
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
    cropFor: (Int) -> Rect = { FULL_CROP },
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
            val c = cropFor(pdf)
            if (bmp != null) {
                AnnotatedPageCell(bmp, anno, Modifier.fillMaxHeight(), if (pdf == highlightPage) highlightBoxes else emptyList(), c.left, c.top, c.right, c.bottom, rotation, links, onMarkerTap)
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
    cropFor: (Int) -> Rect = { FULL_CROP },
    rotation: Int = 0,
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
    // Per-page crop: cur and next each keep their own crop rectangle.
    val curCrop = cropFor(pages.getOrElse(curIdx) { 0 })
    val nextCrop = if (composite && nextIdx < pages.size) cropFor(pages[nextIdx]) else FULL_CROP
    // Crop (un-rotated space) then rotate the pixels, so the half-split math below runs on the
    // corrected page — the fold bar stays horizontal across the music after a 90/270 rotation.
    val cur = remember(curFull, curCrop, rotation) {
        curFull?.let { rotateBitmap(maybeCrop(it, curCrop.left, curCrop.top, curCrop.right, curCrop.bottom), rotation) }
    }
    val next = remember(nextFull, nextCrop, rotation) {
        nextFull?.let { rotateBitmap(maybeCrop(it, nextCrop.left, nextCrop.top, nextCrop.right, nextCrop.bottom), rotation) }
    }
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
        // Each half maps a canvas tap to its (rotated) page frame, then un-rotates and un-crops to
        // full-page-normalized to match the stored marker.
        val liveHit = rememberUpdatedState<(Offset) -> ViewerButton?> hit@{ pos ->
            if (curLinks.isEmpty() && nextLinks.isEmpty()) return@hit null
            fun match(markers: List<ViewerButton>, crop: Rect, dxn: Float, dyn: Float): ViewerButton? {
                val (cu, cv) = unrotateNorm(dxn, dyn, rotation)
                if (cu !in 0f..1f || cv !in 0f..1f) return null
                val fnx = crop.left + cu * (crop.right - crop.left)
                val fny = crop.top + cv * (crop.bottom - crop.top)
                return markers.firstOrNull {
                    abs(it.sourceNx.toFloat() - fnx) < 0.045f && abs(it.sourceNy.toFloat() - fny) < 0.045f
                }
            }
            if (drawComposite) {
                // top half shows the next page's [0..split]; bottom half the current page's [split..1]
                val by = dy + (topH + bar) * scale
                if (pos.y < by) {
                    match(nextLinks, nextCrop, (pos.x - (dx + xB * scale)) / (wB * scale), (pos.y - dy) / (hB * scale))
                } else {
                    match(curLinks, curCrop, (pos.x - (dx + xA * scale)) / (wA * scale), (pos.y - by + aCut * scale) / (hA * scale))
                }
            } else {
                match(curLinks, curCrop, (pos.x - dx) / (wA * scale), (pos.y - dy) / (hA * scale))
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
                            drawRotatedCroppedOverlay(nextAnno, nextHl, wB * scale, hB * scale, rotation, nextCrop.left, nextCrop.top, nextCrop.right, nextCrop.bottom, nextLinks)
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
                            drawRotatedCroppedOverlay(curAnno, curHl, wA * scale, hA * scale, rotation, curCrop.left, curCrop.top, curCrop.right, curCrop.bottom, curLinks)
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
                        drawRotatedCroppedOverlay(curAnno, curHl, wA * scale, hA * scale, rotation, curCrop.left, curCrop.top, curCrop.right, curCrop.bottom, curLinks)
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

/** Which part of the crop rectangle a drag is manipulating. */
private enum class CropHandle { NONE, MOVE, L, T, R, B, TL, TR, BL, BR }

/** Pick the handle under [pos] (px) for the crop box [c] (normalized) on a [w]x[h] surface. */
private fun pickCropHandle(pos: Offset, c: Rect, w: Float, h: Float): CropHandle {
    val thrX = (48f / w).coerceIn(0.02f, 0.25f)
    val thrY = (48f / h).coerceIn(0.02f, 0.25f)
    val nx = pos.x / w
    val ny = pos.y / h
    val nearL = abs(nx - c.left) < thrX
    val nearR = abs(nx - c.right) < thrX
    val nearT = abs(ny - c.top) < thrY
    val nearB = abs(ny - c.bottom) < thrY
    val inRowY = ny > c.top - thrY && ny < c.bottom + thrY
    val inColX = nx > c.left - thrX && nx < c.right + thrX
    return when {
        nearL && nearT -> CropHandle.TL
        nearR && nearT -> CropHandle.TR
        nearL && nearB -> CropHandle.BL
        nearR && nearB -> CropHandle.BR
        nearL && inRowY -> CropHandle.L
        nearR && inRowY -> CropHandle.R
        nearT && inColX -> CropHandle.T
        nearB && inColX -> CropHandle.B
        nx > c.left && nx < c.right && ny > c.top && ny < c.bottom -> CropHandle.MOVE
        else -> CropHandle.NONE
    }
}

/** Apply a normalized drag delta to [start] for [handle]; min side 0.1, kept inside 0..1. */
private fun applyCropHandle(handle: CropHandle, start: Rect, dnx: Float, dny: Float): Rect {
    val minSize = 0.1f
    var l = start.left; var t = start.top; var r = start.right; var b = start.bottom
    when (handle) {
        CropHandle.MOVE -> {
            val wd = start.right - start.left
            val ht = start.bottom - start.top
            l = (start.left + dnx).coerceIn(0f, 1f - wd); t = (start.top + dny).coerceIn(0f, 1f - ht)
            r = l + wd; b = t + ht
        }
        CropHandle.L -> l = (start.left + dnx).coerceIn(0f, start.right - minSize)
        CropHandle.R -> r = (start.right + dnx).coerceIn(start.left + minSize, 1f)
        CropHandle.T -> t = (start.top + dny).coerceIn(0f, start.bottom - minSize)
        CropHandle.B -> b = (start.bottom + dny).coerceIn(start.top + minSize, 1f)
        CropHandle.TL -> { l = (start.left + dnx).coerceIn(0f, start.right - minSize); t = (start.top + dny).coerceIn(0f, start.bottom - minSize) }
        CropHandle.TR -> { r = (start.right + dnx).coerceIn(start.left + minSize, 1f); t = (start.top + dny).coerceIn(0f, start.bottom - minSize) }
        CropHandle.BL -> { l = (start.left + dnx).coerceIn(0f, start.right - minSize); b = (start.bottom + dny).coerceIn(start.top + minSize, 1f) }
        CropHandle.BR -> { r = (start.right + dnx).coerceIn(start.left + minSize, 1f); b = (start.bottom + dny).coerceIn(start.top + minSize, 1f) }
        CropHandle.NONE -> {}
    }
    return Rect(l, t, r, b)
}

/**
 * Full-page crop editor: shows the page (un-rotated, fit to width) with a draggable crop rectangle —
 * drag the interior to move, the edges/corners to resize. The area outside is dimmed. The box is the
 * normalized cropL..cropB; [onChange] reports edits. Shown in single mode while "Zuschneiden" is
 * open, alongside the sliders (which edit the same values).
 */
@Composable
fun CropEditor(
    doc: PdfDoc?,
    pdfIndex: Int,
    renderPx: Int,
    cropL: Float,
    cropT: Float,
    cropR: Float,
    cropB: Float,
    onChange: (Float, Float, Float, Float) -> Unit,
) {
    val bmp = pageBitmap(doc, pdfIndex, renderPx)
    val box = rememberUpdatedState(Rect(cropL, cropT, cropR, cropB))
    val onChangeState = rememberUpdatedState(onChange)
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        if (bmp == null) {
            Text("Lädt …", color = MaterialTheme.colorScheme.outline)
            return@Box
        }
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(bmp.width.toFloat() / bmp.height),
        ) {
            Image(
                bmp.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.FillWidth,
                colorFilter = if (LocalSettings.current.invert) INVERT_FILTER else null,
            )
            Canvas(
                Modifier
                    .matchParentSize()
                    .pointerInput(Unit) {
                        var handle = CropHandle.NONE
                        var start = Rect(0f, 0f, 1f, 1f)
                        var accX = 0f
                        var accY = 0f
                        detectDragGestures(
                            onDragStart = { pos ->
                                start = box.value
                                handle = pickCropHandle(pos, start, size.width.toFloat(), size.height.toFloat())
                                accX = 0f; accY = 0f
                            },
                            onDrag = { change, amt ->
                                if (handle != CropHandle.NONE) {
                                    change.consume()
                                    accX += amt.x / size.width
                                    accY += amt.y / size.height
                                    val nb = applyCropHandle(handle, start, accX, accY)
                                    onChangeState.value(nb.left, nb.top, nb.right, nb.bottom)
                                }
                            },
                        )
                    },
            ) {
                val w = size.width
                val h = size.height
                val c = box.value
                val l = c.left * w; val t = c.top * h; val r = c.right * w; val b = c.bottom * h
                // Dim everything outside the crop rectangle.
                val dim = Color.Black.copy(alpha = 0.45f)
                drawRect(dim, topLeft = Offset(0f, 0f), size = Size(w, t))
                drawRect(dim, topLeft = Offset(0f, b), size = Size(w, h - b))
                drawRect(dim, topLeft = Offset(0f, t), size = Size(l, b - t))
                drawRect(dim, topLeft = Offset(r, t), size = Size(w - r, b - t))
                // Border + corner handles.
                val accent = Color(0xFF80CBC4)
                drawRect(accent, topLeft = Offset(l, t), size = Size(r - l, b - t), style = Stroke(width = 3f))
                val hr = 9f
                for (p in listOf(Offset(l, t), Offset(r, t), Offset(l, b), Offset(r, b))) {
                    drawCircle(Color.White, hr, p)
                    drawCircle(accent, hr, p, style = Stroke(width = 3f))
                }
            }
        }
    }
}
