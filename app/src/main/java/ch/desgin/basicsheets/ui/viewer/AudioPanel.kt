package ch.desgin.basicsheets.ui.viewer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ch.desgin.basicsheets.audio.AudioController

/** Bottom panel: the tuning tone and (when a track is attached) the backing-track player. */
@Composable
fun AudioTuningPanel(
    tonePlaying: Boolean,
    toneLabel: String,
    onToneToggle: () -> Unit,
    onToneShift: (Int) -> Unit,
    controller: AudioController,
    hasAudio: Boolean,
    onAttachAudio: () -> Unit,
    onRemoveAudio: () -> Unit,
    onClose: () -> Unit,
) {
    Surface(tonalElevation = 3.dp, shadowElevation = 8.dp, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Ton & Audio", style = MaterialTheme.typography.titleMedium)
                IconButton(onClick = onClose) { Icon(Icons.Filled.Close, contentDescription = "Schließen") }
            }

            // Tuning tone
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Stimmton", Modifier.weight(1f))
                OutlinedButton(onClick = onToneToggle) { Text((if (tonePlaying) "⏸ " else "▶ ") + toneLabel) }
                OutlinedButton(onClick = { onToneShift(-1) }) { Text("−") }
                OutlinedButton(onClick = { onToneShift(1) }) { Text("+") }
            }

            HorizontalDivider()

            // Backing track
            if (!hasAudio) {
                OutlinedButton(onClick = onAttachAudio) {
                    Icon(Icons.Filled.Add, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("Audio hinzufügen")
                }
            } else {
                val pos = controller.positionMs
                val dur = controller.durationMs
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(onClick = { controller.skip(-10_000) }) { Text("«10") }
                    IconButton(onClick = { controller.toggle() }) {
                        Icon(
                            if (controller.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                            contentDescription = "Wiedergabe",
                        )
                    }
                    TextButton(onClick = { controller.skip(10_000) }) { Text("10»") }
                    Spacer(Modifier.weight(1f))
                    Text("${fmtTime(pos)} / ${fmtTime(dur)}", style = MaterialTheme.typography.labelMedium)
                }
                Slider(
                    value = pos.toFloat(),
                    onValueChange = { controller.seekTo(it.toLong()) },
                    valueRange = 0f..maxOf(1L, dur).toFloat(),
                )
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { controller.cycleSpeed() }) { Text(speedLabel(controller.speed)) }
                    OutlinedButton(onClick = { controller.cycleLoop() }) {
                        Text(when { controller.loopActive -> "Loop A–B"; controller.loopArmed -> "Loop A…"; else -> "Loop" })
                    }
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = onRemoveAudio) { Text("Entfernen") }
                }
            }
        }
    }
}

private fun fmtTime(ms: Long): String {
    if (ms <= 0L) return "0:00"
    val s = ms / 1000L
    return "${s / 60}:${(s % 60).toString().padStart(2, '0')}"
}

private fun speedLabel(speed: Float): String = if (speed == 1f) "1×" else "$speed×"
