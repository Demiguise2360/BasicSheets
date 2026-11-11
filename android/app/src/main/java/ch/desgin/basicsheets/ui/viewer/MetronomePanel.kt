package ch.desgin.basicsheets.ui.viewer

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import ch.desgin.basicsheets.audio.Metronome
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MetronomePanel(metro: Metronome, onClose: () -> Unit) {
    val taps = remember { mutableStateListOf<Long>() }

    Surface(tonalElevation = 3.dp, shadowElevation = 8.dp, modifier = Modifier.fillMaxWidth()) {
        Column(
            Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Metronom", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    for (i in 0 until metro.beatsPerBar) {
                        val on = metro.running && metro.beat == i
                        val color = when {
                            on && i == 0 -> MaterialTheme.colorScheme.primary
                            on -> MaterialTheme.colorScheme.secondary
                            else -> MaterialTheme.colorScheme.surfaceVariant
                        }
                        Box(Modifier.size(if (i == 0) 12.dp else 10.dp).clip(CircleShape).background(color))
                    }
                }
                IconButton(onClick = onClose) { Icon(Icons.Filled.Close, contentDescription = "Schließen") }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilledIconButton(onClick = { metro.toggle() }) {
                    Icon(
                        if (metro.running) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        contentDescription = if (metro.running) "Stop" else "Start",
                    )
                }
                Text("♩ = ${metro.bpm}", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
                OutlinedIconButton(onClick = { metro.setBpm(metro.bpm - 5) }) {
                    Icon(Icons.Filled.Remove, contentDescription = "Langsamer")
                }
                OutlinedIconButton(onClick = { metro.setBpm(metro.bpm + 5) }) {
                    Icon(Icons.Filled.Add, contentDescription = "Schneller")
                }
                OutlinedButton(onClick = {
                    val now = System.currentTimeMillis()
                    if (taps.isNotEmpty() && now - taps.last() > 2000) taps.clear()
                    taps.add(now)
                    while (taps.size > 6) taps.removeAt(0)
                    if (taps.size >= 2) {
                        val avg = (taps.last() - taps.first()).toDouble() / (taps.size - 1)
                        if (avg > 0) metro.setBpm((60000.0 / avg).roundToInt())
                    }
                }) { Text("Tap") }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("Takt", style = MaterialTheme.typography.bodyMedium)
                for (b in listOf(2, 3, 4, 6)) {
                    FilterChip(selected = metro.beatsPerBar == b, onClick = { metro.beatsPerBar = b }, label = { Text("$b") })
                }
            }
        }
    }
}
