package ch.desgin.basicsheets.ui.viewer

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import ch.desgin.basicsheets.data.Bookmark
import ch.desgin.basicsheets.pdf.OutlineEntry

/** Bottom sheet with the PDF outline (if any) and the user's bookmarks; tap an item to jump. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookmarkSheet(
    outline: List<OutlineEntry>,
    bookmarks: List<Bookmark>,
    onJump: (Int) -> Unit,           // 0-based page
    onDelete: (Bookmark) -> Unit,
    onAddHere: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState()
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        LazyColumn(Modifier.fillMaxWidth().heightIn(max = 520.dp).padding(horizontal = 16.dp)) {
            if (outline.isNotEmpty()) {
                item { SectionHeader("Inhalt") }
                items(outline) { oe ->
                    val pad = Modifier.fillMaxWidth().padding(start = (oe.level * 16).dp, top = 10.dp, bottom = 10.dp)
                    if (oe.page >= 0) {
                        Text(oe.title, Modifier.fillMaxWidth().clickable { onJump(oe.page) }.then(pad), style = MaterialTheme.typography.bodyMedium)
                    } else {
                        Text(oe.title, pad, color = MaterialTheme.colorScheme.outline, style = MaterialTheme.typography.bodyMedium)
                    }
                }
                item { Spacer(Modifier.height(8.dp)) }
            }

            item { SectionHeader("Lesezeichen") }
            if (bookmarks.isEmpty()) {
                item {
                    Text(
                        "Noch keine Lesezeichen",
                        Modifier.padding(vertical = 10.dp),
                        color = MaterialTheme.colorScheme.outline,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            } else {
                items(bookmarks) { bm ->
                    Row(
                        Modifier.fillMaxWidth().clickable { onJump(bm.page - 1) }.padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(Modifier.size(14.dp).clip(CircleShape).background(hexColor(bm.color)))
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(bm.label.ifBlank { "Seite ${bm.page}" }, style = MaterialTheme.typography.bodyLarge)
                            Text("Seite ${bm.page}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                        }
                        IconButton(onClick = { onDelete(bm) }) { Icon(Icons.Filled.Delete, contentDescription = "Löschen") }
                    }
                }
            }

            item {
                TextButton(onClick = onAddHere, modifier = Modifier.padding(vertical = 8.dp)) {
                    Icon(Icons.Filled.Add, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("Lesezeichen hier setzen")
                }
            }
            item { Spacer(Modifier.height(16.dp)) }
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text,
        Modifier.padding(top = 12.dp, bottom = 6.dp),
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
    )
}

/** Add-bookmark dialog: a label and a color, for the given (1-based) page. */
@Composable
fun BookmarkAddDialog(page: Int, onSave: (String, String) -> Unit, onDismiss: () -> Unit) {
    var label by remember { mutableStateOf("") }
    var color by remember { mutableStateOf(BOOKMARK_COLORS.first()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = { onSave(label, color) }) { Text("Speichern") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Abbrechen") } },
        title = { Text("Lesezeichen – Seite $page") },
        text = {
            Column {
                OutlinedTextField(value = label, onValueChange = { label = it }, label = { Text("Bezeichnung") }, singleLine = true)
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    for (c in BOOKMARK_COLORS) {
                        Box(
                            Modifier.size(30.dp).clip(CircleShape).background(hexColor(c))
                                .then(if (c == color) Modifier.border(3.dp, MaterialTheme.colorScheme.onSurface, CircleShape) else Modifier)
                                .clickable { color = c },
                        )
                    }
                }
            }
        },
    )
}

val BOOKMARK_COLORS = listOf("#387373", "#E53935", "#1E88E5", "#F59E0B", "#8E24AA", "#43A047")

private fun hexColor(hex: String): Color =
    runCatching { Color(android.graphics.Color.parseColor(hex)) }.getOrDefault(Color(0xFF387373))
