package ch.desgin.basicsheets.ui.viewer

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowRightAlt
import androidx.compose.material.icons.filled.AutoFixNormal
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.CropSquare
import androidx.compose.material.icons.filled.DragIndicator
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FormatColorFill
import androidx.compose.material.icons.filled.HorizontalRule
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Title
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ch.desgin.basicsheets.annot.Tool
import kotlin.math.roundToInt

/**
 * Floating, draggable annotation tool palette — the MobileSheets-style round-button cluster
 * from the Figma design, offered as an alternative to [AnnotationToolbar] (chosen in settings).
 * Uses the exact same tool/colour/size/callback contract, so it is a drop-in swap for the bar.
 * It can be moved anywhere over the page (drag handle) and collapsed to a small puck.
 */
@Composable
fun AnnotationPalette(
    tool: Tool,
    color: Color,
    sizeLevel: Int,
    fill: Boolean,
    eraserBox: Boolean,
    onTool: (Tool) -> Unit,
    onColor: (Color) -> Unit,
    onSize: (Int) -> Unit,
    onFill: (Boolean) -> Unit,
    onEraserBox: (Boolean) -> Unit,
    onOpenStamps: () -> Unit,
    onUndo: () -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(true) }
    var colorOpen by remember { mutableStateOf(false) }
    var off by remember { mutableStateOf(Offset.Zero) }

    Surface(
        color = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary,
        shape = RoundedCornerShape(22.dp),
        shadowElevation = 10.dp,
        modifier = modifier
            .offset { IntOffset(off.x.roundToInt(), off.y.roundToInt()) }
            .widthIn(max = 224.dp),
    ) {
        Column(Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            // Drag handle + collapse toggle.
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .clip(CircleShape)
                        .pointerInput(Unit) {
                            detectDragGestures { change, dragAmount -> change.consume(); off += dragAmount }
                        }
                        .padding(4.dp),
                ) {
                    Icon(Icons.Filled.DragIndicator, contentDescription = "Verschieben", modifier = Modifier.size(22.dp))
                }
                Spacer(Modifier.width(4.dp))
                Text("Werkzeuge", style = MaterialTheme.typography.labelMedium)
                Spacer(Modifier.weight(1f))
                RoundBtn(onClick = { expanded = !expanded }) {
                    Icon(
                        if (expanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                        contentDescription = if (expanded) "Einklappen" else "Ausklappen",
                        modifier = Modifier.size(22.dp),
                    )
                }
            }

            if (expanded) {
                ToolRow(tool, onTool, onOpenStamps, Tool.PEN, Tool.MARKER, Tool.ERASER, Tool.TEXT)
                ToolRow(tool, onTool, onOpenStamps, Tool.STAMP, Tool.CRESCENDO, Tool.DECRESCENDO, Tool.LINE)
                ToolRow(tool, onTool, onOpenStamps, Tool.ARROW, Tool.RECT, Tool.ELLIPSE)

                Divider()

                // Current colour (opens the honeycomb) + brush size.
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    RoundBtn(selected = colorOpen, onClick = { colorOpen = !colorOpen }) {
                        Box(
                            Modifier.size(22.dp).clip(CircleShape).background(color)
                                .border(1.dp, MaterialTheme.colorScheme.onPrimary, CircleShape),
                        )
                    }
                    Spacer(Modifier.weight(1f))
                    RoundBtn(onClick = { onSize((sizeLevel - 1).coerceAtLeast(1)) }) { Glyph("–") }
                    Text("$sizeLevel", style = MaterialTheme.typography.titleMedium)
                    RoundBtn(onClick = { onSize((sizeLevel + 1).coerceAtMost(10)) }) { Glyph("+") }
                }

                if (colorOpen) ColorHoneycomb(color) { onColor(it); colorOpen = false }

                if (tool == Tool.RECT || tool == Tool.ELLIPSE) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        RoundBtn(selected = fill, onClick = { onFill(!fill) }) {
                            Icon(Icons.Filled.FormatColorFill, contentDescription = "Füllen", modifier = Modifier.size(20.dp))
                        }
                        Text("Füllen", style = MaterialTheme.typography.bodyMedium)
                    }
                }
                if (tool == Tool.ERASER) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        RoundBtn(selected = eraserBox, onClick = { onEraserBox(!eraserBox) }) {
                            Icon(Icons.Filled.CropSquare, contentDescription = "Kasten-Radierer", modifier = Modifier.size(20.dp))
                        }
                        Text("Kasten", style = MaterialTheme.typography.bodyMedium)
                    }
                }

                Divider()

                Row(verticalAlignment = Alignment.CenterVertically) {
                    RoundBtn(onClick = onUndo) {
                        Icon(Icons.Filled.Undo, contentDescription = "Rückgängig", modifier = Modifier.size(20.dp))
                    }
                    Spacer(Modifier.weight(1f))
                    Box(
                        Modifier.clip(RoundedCornerShape(50)).background(MaterialTheme.colorScheme.onPrimary)
                            .clickable(onClick = onDone).padding(horizontal = 16.dp, vertical = 7.dp),
                    ) {
                        Text("Fertig", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        }
    }
}

/** One row of up to four tool buttons. */
@Composable
private fun ToolRow(cur: Tool, onTool: (Tool) -> Unit, onOpenStamps: () -> Unit, vararg tools: Tool) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        for (t in tools) ToolBtn(t, cur, onTool, onOpenStamps)
    }
}

@Composable
private fun ToolBtn(t: Tool, cur: Tool, onTool: (Tool) -> Unit, onOpenStamps: () -> Unit) {
    RoundBtn(
        selected = t == cur,
        onClick = { if (t == Tool.STAMP) onOpenStamps() else onTool(t) },
    ) {
        when (t) {
            Tool.PEN -> ToolIcon(Icons.Filled.Edit, "Stift")
            Tool.MARKER -> ToolIcon(Icons.Filled.Brush, "Marker")
            Tool.ERASER -> ToolIcon(Icons.Filled.AutoFixNormal, "Radierer")
            Tool.TEXT -> ToolIcon(Icons.Filled.Title, "Text")
            Tool.STAMP -> ToolIcon(Icons.Filled.MusicNote, "Stempel")
            Tool.CRESCENDO -> Glyph("<")
            Tool.DECRESCENDO -> Glyph(">")
            Tool.LINE -> ToolIcon(Icons.Filled.HorizontalRule, "Linie")
            Tool.ARROW -> ToolIcon(Icons.AutoMirrored.Filled.ArrowRightAlt, "Pfeil")
            Tool.RECT -> ToolIcon(Icons.Filled.CropSquare, "Rechteck")
            Tool.ELLIPSE -> ToolIcon(Icons.Filled.RadioButtonUnchecked, "Ellipse")
        }
    }
}

@Composable
private fun ToolIcon(icon: ImageVector, label: String) {
    Icon(icon, contentDescription = label, modifier = Modifier.size(22.dp))
}

@Composable
private fun Glyph(s: String) {
    Text(s, fontWeight = FontWeight.Bold, fontSize = 18.sp)
}

/** A 42dp round button; [selected] inverts it (white fill, petrol content) like the toolbar chips. */
@Composable
private fun RoundBtn(selected: Boolean = false, onClick: () -> Unit, content: @Composable () -> Unit) {
    val onP = MaterialTheme.colorScheme.onPrimary
    val bg = if (selected) onP else onP.copy(alpha = 0.16f)
    val fg = if (selected) MaterialTheme.colorScheme.primary else onP
    Box(
        Modifier.size(42.dp).clip(CircleShape).background(bg).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        CompositionLocalProvider(LocalContentColor provides fg) { content() }
    }
}

/** Honeycomb cluster of the shared ink colours (Frame 17). Second row nestled half a cell over. */
@Composable
private fun ColorHoneycomb(current: Color, onPick: (Color) -> Unit) {
    val top = PEN_COLORS.take(3)
    val bottom = PEN_COLORS.drop(3)
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for (c in top) Swatch(c, c == current, onPick)
        }
        Row(Modifier.offset(x = 21.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for (c in bottom) Swatch(c, c == current, onPick)
        }
    }
}

@Composable
private fun Swatch(c: Color, selected: Boolean, onPick: (Color) -> Unit) {
    val ring = MaterialTheme.colorScheme.onPrimary
    Box(
        Modifier.size(34.dp).clip(CircleShape).background(c)
            .border(if (selected) 3.dp else 1.dp, if (selected) ring else ring.copy(alpha = 0.5f), CircleShape)
            .clickable { onPick(c) },
    )
}

/** Thin full-width rule tinted for the petrol surface. */
@Composable
private fun Divider() {
    Box(
        Modifier.fillMaxWidth().height(1.dp)
            .background(MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.25f)),
    )
}
