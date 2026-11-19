package ch.desgin.basicsheets.ui.viewer

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ch.desgin.basicsheets.ui.settings.LocalSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Renders a plain-text or ChordPro song (.txt / .cho / .chordpro / …). ChordPro `[Chord]` markers
 * are lifted onto a monospace chord line above the lyric; `{title}` / `{subtitle}` / `{comment}`
 * directives are styled; structural directives (soc/eoc/sot/…) are ignored. Plain text renders as
 * monospace lines. Text colour follows the viewer background. Transposition is not offered (the
 * MAUI app didn't have it either). The whole song scrolls vertically.
 */
@Composable
fun TextSongContent(file: File?, modifier: Modifier = Modifier) {
    val content by produceState<String?>(null, file) {
        value = if (file != null) withContext(Dispatchers.IO) { runCatching { file.readText() }.getOrNull() ?: "" } else null
    }
    val bg = LocalSettings.current.viewerBg
    val fg = if (bg == "white") Color(0xFF111111) else Color(0xFFECECEC)
    val chordColor = if (bg == "white") Color(0xFF1565C0) else Color(0xFF80CBC4)
    val muted = fg.copy(alpha = 0.6f)

    val c = content
    if (c == null) {
        Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Lädt …", color = muted)
        }
        return
    }
    val lines = remember(c) { c.split("\n") }
    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
    ) {
        for (raw in lines) {
            val line = raw.trimEnd('\r')
            when {
                line.isBlank() -> Spacer(Modifier.height(10.dp))
                line.trim().startsWith("{") -> {
                    val (kind, value) = parseDirective(line.trim())
                    when (kind) {
                        "title" -> Text(value, color = fg, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                        "subtitle", "artist" -> Text(value, color = muted, style = MaterialTheme.typography.titleMedium)
                        "comment" -> Text(value, color = chordColor, style = MaterialTheme.typography.bodyMedium, fontStyle = FontStyle.Italic)
                        else -> {}   // structural directives (soc/eoc/sot/eot/…) ignored
                    }
                }
                line.contains('[') -> {
                    val (chords, lyric) = splitChords(line)
                    if (chords.isNotBlank()) {
                        Text(chords, color = chordColor, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 14.sp, lineHeight = 16.sp)
                    }
                    Text(lyric.ifEmpty { " " }, color = fg, fontFamily = FontFamily.Monospace, fontSize = 15.sp, lineHeight = 20.sp)
                }
                else -> Text(line, color = fg, fontFamily = FontFamily.Monospace, fontSize = 15.sp, lineHeight = 20.sp)
            }
        }
        Spacer(Modifier.height(48.dp))
    }
}

/** Parse a `{directive}` or `{directive: value}` line to a normalized (key, value) pair. */
private fun parseDirective(line: String): Pair<String, String> {
    val inner = line.removePrefix("{").removeSuffix("}")
    val colon = inner.indexOf(':')
    val rawKey = (if (colon >= 0) inner.substring(0, colon) else inner).trim().lowercase()
    val value = if (colon >= 0) inner.substring(colon + 1).trim() else ""
    val key = when (rawKey) {
        "t", "title" -> "title"
        "st", "subtitle" -> "subtitle"
        "artist" -> "artist"
        "c", "comment" -> "comment"
        else -> rawKey
    }
    return key to value
}

/** Split a ChordPro lyric line into an aligned chord line + the plain lyric line (monospace). */
private fun splitChords(line: String): Pair<String, String> {
    val lyric = StringBuilder()
    val chords = StringBuilder()
    var i = 0
    while (i < line.length) {
        val ch = line[i]
        if (ch == '[') {
            val end = line.indexOf(']', i + 1)
            if (end > i) {
                val chord = line.substring(i + 1, end)
                while (chords.length < lyric.length) chords.append(' ')
                chords.append(chord).append(' ')
                i = end + 1
                continue
            }
        }
        lyric.append(ch)
        i++
    }
    return chords.toString() to lyric.toString()
}
