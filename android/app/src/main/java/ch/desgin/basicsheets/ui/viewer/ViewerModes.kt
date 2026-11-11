package ch.desgin.basicsheets.ui.viewer

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import ch.desgin.basicsheets.pdf.PdfDoc

/** Display modes, keyed by the same strings the MAUI app stores in Sheet.pageMode. */
enum class DisplayMode(val key: String, val label: String) {
    SINGLE("single", "Einzelne Seite"),
    TWO("two", "Zwei Seiten"),
    SCROLL_V("scroll-v", "Vertikal scrollen"),
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

@Composable
private fun PageImage(bmp: Bitmap?, modifier: Modifier = Modifier) {
    if (bmp != null) {
        Image(bmp.asImageBitmap(), contentDescription = null, modifier = modifier, contentScale = ContentScale.FillWidth)
    }
}

@Composable
fun SinglePageView(doc: PdfDoc?, page: Int, pageCount: Int, renderPx: Int, onTurn: (Int) -> Unit) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    LaunchedEffect(page) { scale = 1f; offset = Offset.Zero }

    val transform = rememberTransformableState { zoom, pan, _ ->
        scale = (scale * zoom).coerceIn(1f, 5f)
        offset = if (scale > 1f) offset + pan else Offset.Zero
    }
    val bmp = pageBitmap(doc, page, renderPx)

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
            Image(
                bmp.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier.fillMaxWidth().graphicsLayer(
                    scaleX = scale, scaleY = scale, translationX = offset.x, translationY = offset.y,
                ),
                contentScale = ContentScale.FillWidth,
            )
        } else {
            Text("Lädt …", color = MaterialTheme.colorScheme.outline)
        }
    }
}

@Composable
fun TwoPageView(doc: PdfDoc?, page: Int, pageCount: Int, renderPx: Int, onTurn: (Int) -> Unit) {
    val half = renderPx / 2
    val left = pageBitmap(doc, page, half)
    val right = if (page + 1 < pageCount) pageBitmap(doc, page + 1, half) else null

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
            PageImage(left, Modifier.weight(1f))
            PageImage(right, Modifier.weight(1f))
        }
    }
}

@Composable
fun VerticalScrollView(doc: PdfDoc?, pageCount: Int, renderPx: Int, startPage: Int) {
    val state = rememberLazyListState(
        initialFirstVisibleItemIndex = startPage.coerceIn(0, maxOf(0, pageCount - 1)),
    )
    LazyColumn(Modifier.fillMaxSize(), state = state) {
        items(pageCount) { i ->
            val bmp = pageBitmap(doc, i, renderPx)
            if (bmp != null) {
                PageImage(bmp, Modifier.fillMaxWidth())
            } else {
                Box(Modifier.fillMaxWidth().height(240.dp), contentAlignment = Alignment.Center) {
                    Text("…", color = MaterialTheme.colorScheme.outline)
                }
            }
        }
    }
}
