package ch.desgin.basicsheets.ui.viewer

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ch.desgin.basicsheets.pdf.PdfDoc

/** Full-screen thumbnail overview; tap a page to jump. Thumbnails render lazily as cells appear. */
@Composable
fun PageGrid(
    doc: PdfDoc?,
    pages: List<Int>,            // ordered PDF page indices (display order)
    currentDisplay: Int,
    thumbPx: Int,
    bookmarkColors: Map<Int, String>,   // PDF page (0-based) → hex color
    annotatedPages: Set<Int>,            // PDF pages (0-based)
    onJump: (Int) -> Unit,               // display slot index
    onClose: () -> Unit,
) {
    Surface(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Seitenübersicht · ${pages.size} Seiten", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                TextButton(onClick = onClose) { Text("Schließen") }
            }
            HorizontalDivider()
            LazyVerticalGrid(
                columns = GridCells.Adaptive(110.dp),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(pages.size) { i ->
                    val pdf = pages[i]
                    PageGridCell(doc, pdf, i + 1, thumbPx, i == currentDisplay, bookmarkColors[pdf], pdf in annotatedPages) { onJump(i) }
                }
            }
        }
    }
}

@Composable
private fun PageGridCell(
    doc: PdfDoc?,
    pdfIndex: Int,
    label: Int,
    thumbPx: Int,
    isCurrent: Boolean,
    bookmarkColor: String?,
    annotated: Boolean,
    onClick: () -> Unit,
) {
    Column(Modifier.clickable(onClick = onClick), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier.fillMaxWidth().aspectRatio(0.75f)
                .border(
                    if (isCurrent) BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
                    else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    RoundedCornerShape(4.dp),
                )
                .clip(RoundedCornerShape(4.dp))
                .background(Color.White),
            contentAlignment = Alignment.Center,
        ) {
            val bmp = pageBitmap(doc, pdfIndex, thumbPx)
            if (bmp != null) {
                Image(bmp.asImageBitmap(), contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
            } else {
                Text("…", color = MaterialTheme.colorScheme.outline)
            }
            if (annotated) {
                Box(
                    Modifier.align(Alignment.TopStart).padding(3.dp).size(16.dp).clip(RoundedCornerShape(4.dp)).background(Color(0x9E282828)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("✎", color = Color.White, fontSize = 10.sp)
                }
            }
            if (bookmarkColor != null) {
                Box(Modifier.align(Alignment.TopEnd).padding(4.dp).size(11.dp).clip(CircleShape).background(hexColor(bookmarkColor)))
            }
        }
        Text(
            "$label",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
            color = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
        )
    }
}

private fun hexColor(hex: String): Color =
    runCatching { Color(android.graphics.Color.parseColor(hex)) }.getOrDefault(Color(0xFF387373))
