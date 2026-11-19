package ch.desgin.basicsheets.ui.viewer

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import ch.desgin.basicsheets.pdf.PdfDoc

/**
 * Per-song page-order editor: reorder (up/down), hide (remove), duplicate or un-hide (add), and
 * reset to natural order. `initialOrder` and the saved result are 0-based PDF page indices in
 * display order.
 */
@Composable
fun PageOrderEditor(
    doc: PdfDoc?,
    initialOrder: List<Int>,
    pageCount: Int,
    onSave: (List<Int>) -> Unit,
    onDismiss: () -> Unit,
) {
    var work by remember { mutableStateOf(initialOrder) }
    var addOpen by remember { mutableStateOf(false) }

    Surface(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onDismiss) { Icon(Icons.Filled.Close, contentDescription = "Abbrechen") }
                Text("Seitenreihenfolge", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                TextButton(onClick = { work = (0 until pageCount).toList() }) { Text("Zurücksetzen") }
                TextButton(onClick = { onSave(work) }) { Text("Speichern") }
            }
            HorizontalDivider()
            LazyColumn(
                Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                itemsIndexed(work) { i, pdf ->
                    PageOrderRow(
                        doc, pdf, i, work.size,
                        onUp = { if (i > 0) work = work.swap(i, i - 1) },
                        onDown = { if (i < work.size - 1) work = work.swap(i, i + 1) },
                        onRemove = { if (work.size > 1) work = work.toMutableList().also { it.removeAt(i) } },
                    )
                }
            }
            HorizontalDivider()
            Box(Modifier.padding(12.dp)) {
                OutlinedButton(onClick = { addOpen = true }) {
                    Icon(Icons.Filled.Add, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("Seite hinzufügen")
                }
                DropdownMenu(expanded = addOpen, onDismissRequest = { addOpen = false }) {
                    for (p in 0 until pageCount) {
                        DropdownMenuItem(text = { Text("Seite ${p + 1}") }, onClick = { addOpen = false; work = work + p })
                    }
                }
            }
        }
    }
}

private fun List<Int>.swap(a: Int, b: Int): List<Int> =
    toMutableList().also { val t = it[a]; it[a] = it[b]; it[b] = t }

@Composable
private fun PageOrderRow(
    doc: PdfDoc?,
    pdf: Int,
    index: Int,
    total: Int,
    onUp: () -> Unit,
    onDown: () -> Unit,
    onRemove: () -> Unit,
) {
    ElevatedCard(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            val bmp = pageBitmap(doc, pdf, 160)
            Box(Modifier.width(44.dp).aspectRatio(0.75f).background(Color.White), contentAlignment = Alignment.Center) {
                if (bmp != null) {
                    Image(bmp.asImageBitmap(), contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
                } else {
                    Text("…", color = MaterialTheme.colorScheme.outline)
                }
            }
            Spacer(Modifier.width(12.dp))
            Text("Seite ${pdf + 1}", Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
            IconButton(onClick = onUp, enabled = index > 0) { Icon(Icons.Filled.KeyboardArrowUp, contentDescription = "Nach oben") }
            IconButton(onClick = onDown, enabled = index < total - 1) { Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "Nach unten") }
            IconButton(onClick = onRemove) { Icon(Icons.Filled.Close, contentDescription = "Entfernen") }
        }
    }
}
