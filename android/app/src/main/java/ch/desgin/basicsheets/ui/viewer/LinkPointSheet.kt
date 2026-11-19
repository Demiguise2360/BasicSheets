package ch.desgin.basicsheets.ui.viewer

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import ch.desgin.basicsheets.data.ViewerButton

/**
 * Bottom sheet listing every placed jump point (Sprungpunkt) for the song. Tap a row to go to the
 * point's source page (where its marker sits); delete per row; or jump into edit mode to add more.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LinkPointSheet(
    linkPoints: List<ViewerButton>,
    onGoToSource: (ViewerButton) -> Unit,   // jumps to the 1-based sourcePage
    onDelete: (ViewerButton) -> Unit,
    onEdit: () -> Unit,
    onDismiss: () -> Unit,
) {
    val points = linkPoints
        .filter { it.sourceNx >= 0 }
        .sortedWith(compareBy({ it.sourcePage }, { it.sourceNy }, { it.sourceNx }))
    val sheetState = rememberModalBottomSheetState()
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        LazyColumn(Modifier.fillMaxWidth().heightIn(max = 520.dp).padding(horizontal = 16.dp)) {
            item {
                Text(
                    "Sprungpunkte",
                    Modifier.padding(top = 12.dp, bottom = 6.dp),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            if (points.isEmpty()) {
                item {
                    Text(
                        "Noch keine Sprungpunkte",
                        Modifier.padding(vertical = 10.dp),
                        color = MaterialTheme.colorScheme.outline,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            } else {
                items(points, key = { it.id }) { vb ->
                    Row(
                        Modifier.fillMaxWidth().clickable { onGoToSource(vb) }.padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(Modifier.size(14.dp).clip(CircleShape).background(Color(0xFF1565C0)))
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            if (vb.label.isBlank()) {
                                Text("Seite ${vb.sourcePage} → Seite ${vb.targetPage}", style = MaterialTheme.typography.bodyLarge)
                            } else {
                                Text(vb.label, style = MaterialTheme.typography.bodyLarge)
                                Text(
                                    "Seite ${vb.sourcePage} → Seite ${vb.targetPage}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.outline,
                                )
                            }
                        }
                        IconButton(onClick = { onDelete(vb) }) { Icon(Icons.Filled.Delete, contentDescription = "Löschen") }
                    }
                }
            }

            item {
                TextButton(onClick = onEdit, modifier = Modifier.padding(vertical = 8.dp)) {
                    Icon(Icons.Filled.Edit, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("Sprungpunkte bearbeiten")
                }
            }
            item { Spacer(Modifier.height(16.dp)) }
        }
    }
}
