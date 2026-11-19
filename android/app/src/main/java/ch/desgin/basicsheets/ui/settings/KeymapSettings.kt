package ch.desgin.basicsheets.ui.settings

import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.nativeKeyCode
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/** A human-ish label for an Android key code; "—" when unset (0). */
fun keyName(code: Int): String {
    if (code == 0) return "—"
    return android.view.KeyEvent.keyCodeToString(code).removePrefix("KEYCODE_").replace('_', ' ')
}

/**
 * Dialog that captures the next hardware key press and returns its Android key code, for binding a
 * page-turn key (e.g. a Bluetooth pedal). Volume keys are excluded — they have their own setting.
 * "Löschen" clears the binding (code 0). Needs a real keyboard/pedal to exercise.
 */
@Composable
fun KeyCaptureDialog(
    title: String,
    currentCode: Int,
    onPick: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = { onPick(0) }) { Text("Löschen") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Abbrechen") } },
        title = { Text(title) },
        text = {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(90.dp)
                    .focusRequester(focus)
                    .focusable()
                    .onKeyEvent { ev ->
                        if (ev.type == KeyEventType.KeyDown && ev.key != Key.VolumeUp && ev.key != Key.VolumeDown) {
                            onPick(ev.key.nativeKeyCode)
                            true
                        } else {
                            false
                        }
                    },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    if (currentCode != 0) "Aktuell: ${keyName(currentCode)}\n\nNeue Taste drücken …" else "Taste drücken …",
                    textAlign = TextAlign.Center,
                )
            }
        },
    )
}
