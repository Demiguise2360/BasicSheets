package ch.desgin.basicsheets.ui.viewer

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import ch.desgin.basicsheets.BasicSheetsApp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Viewer: renders a page via Android's PdfRenderer with pinch-zoom, pan, and tap-zone page turns
 * (tap the left/right third to go back/forward when not zoomed). The full MAUI viewer — swipe
 * turns, the display modes (two-page/half-page/scroll), fit modes, annotations — follows in the
 * next phases.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ViewerScreen(sheetId: Int, onBack: () -> Unit) {
    val app = BasicSheetsApp.instance
    var title by remember { mutableStateOf("") }
    var file by remember { mutableStateOf<File?>(null) }
    var pageCount by remember { mutableIntStateOf(0) }
    var page by remember { mutableIntStateOf(0) }
    var bitmap by remember { mutableStateOf<Bitmap?>(null) }
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }

    // Render at ~2x the screen width so zooming stays sharp.
    val renderPx = with(LocalDensity.current) { (LocalConfiguration.current.screenWidthDp.dp.toPx() * 2f).toInt() }

    LaunchedEffect(sheetId) {
        val s = app.library.byId(sheetId) ?: return@LaunchedEffect
        title = s.name
        val f = app.library.fileFor(s)
        file = f
        pageCount = withContext(Dispatchers.IO) { pageCountOf(f) }
        page = 0
    }
    LaunchedEffect(file, page, renderPx) {
        val f = file ?: return@LaunchedEffect
        if (pageCount <= 0) return@LaunchedEffect
        bitmap = withContext(Dispatchers.IO) { renderPage(f, page, renderPx) }
    }
    // Reset zoom/pan whenever the page changes.
    LaunchedEffect(page) { scale = 1f; offset = Offset.Zero }

    val transform = rememberTransformableState { zoomChange, panChange, _ ->
        scale = (scale * zoomChange).coerceIn(1f, 5f)
        offset = if (scale > 1f) offset + panChange else Offset.Zero
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Zurück")
                    }
                },
            )
        },
        bottomBar = {
            if (pageCount > 1) {
                BottomAppBar {
                    TextButton(onClick = { if (page > 0) page-- }, enabled = page > 0) { Text("‹") }
                    Spacer(Modifier.weight(1f))
                    Text("${page + 1} / $pageCount", Modifier.align(Alignment.CenterVertically))
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = { if (page < pageCount - 1) page++ }, enabled = page < pageCount - 1) { Text("›") }
                }
            }
        },
    ) { pad ->
        Box(
            Modifier
                .fillMaxSize()
                .padding(pad)
                .transformable(transform)
                .pointerInput(pageCount) {
                    detectTapGestures { pos ->
                        if (scale <= 1.05f) {                       // only turn pages when not zoomed in
                            val third = size.width / 3f
                            if (pos.x < third) { if (page > 0) page-- }
                            else if (pos.x > size.width - third) { if (page < pageCount - 1) page++ }
                        }
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            val bmp = bitmap
            if (bmp != null) {
                Image(
                    bmp.asImageBitmap(),
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .graphicsLayer(
                            scaleX = scale,
                            scaleY = scale,
                            translationX = offset.x,
                            translationY = offset.y,
                        ),
                    contentScale = ContentScale.FillWidth,
                )
            } else {
                Text("Lädt …", color = MaterialTheme.colorScheme.outline)
            }
        }
    }
}

private fun pageCountOf(file: File): Int = runCatching {
    ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
        PdfRenderer(pfd).use { it.pageCount }
    }
}.getOrDefault(0)

private fun renderPage(file: File, index: Int, widthPx: Int): Bitmap? = runCatching {
    ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
        PdfRenderer(pfd).use { renderer ->
            if (index < 0 || index >= renderer.pageCount) return null
            renderer.openPage(index).use { p ->
                val w = widthPx.coerceAtLeast(1)
                val h = (w.toFloat() * p.height / p.width).toInt().coerceAtLeast(1)
                val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                bmp.eraseColor(Color.WHITE)
                p.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                bmp
            }
        }
    }
}.getOrNull()
