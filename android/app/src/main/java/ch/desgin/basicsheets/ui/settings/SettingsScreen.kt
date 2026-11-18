package ch.desgin.basicsheets.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.LocalContext
import ch.desgin.basicsheets.BasicSheetsApp
import ch.desgin.basicsheets.ui.theme.appTopBarColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen() {
    val app = BasicSheetsApp.instance
    val scope = rememberCoroutineScope()
    val settings = LocalSettings.current
    val context = LocalContext.current
    fun set(key: String, value: String) { scope.launch { app.settings.set(key, value) } }
    val backupSave = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) scope.launch {
            val text = app.annotations.exportAllJson()
            val ok = withContext(Dispatchers.IO) {
                runCatching { context.contentResolver.openOutputStream(uri)?.use { it.write(text.toByteArray()) }; true }.getOrDefault(false)
            }
            Toast.makeText(context, if (ok) "Notizen gesichert" else "Sichern fehlgeschlagen", Toast.LENGTH_SHORT).show()
        }
    }
    val backupLoad = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            val text = withContext(Dispatchers.IO) {
                runCatching { context.contentResolver.openInputStream(uri)?.use { it.readBytes().decodeToString() } }.getOrNull()
            }
            val n = if (text != null) app.annotations.importJson(text) else -1
            Toast.makeText(context, if (n >= 0) "$n Seiten geladen" else "Laden fehlgeschlagen", Toast.LENGTH_SHORT).show()
        }
    }

    Column(Modifier.fillMaxSize()) {
        TopAppBar(title = { Text("Einstellungen") }, colors = appTopBarColors())
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            SectionHeader("Anzeige")
            SelectRow("Seitenansicht", "Standardansicht für Noten ohne eigene Einstellung", PAGE_MODE_OPTIONS, settings.pageMode) { set(KEY_PAGE_MODE, it) }
            ToggleRow("Dunkles Design", "App im dunklen Farbschema", settings.darkTheme) { set(KEY_DARK, it.toString()) }

            SectionHeader("Lesen")
            SelectRow("Viewer-Hintergrund", "Farbe hinter den Noten", BG_OPTIONS, settings.viewerBg) { set(KEY_BG, it) }
            ToggleRow("Noten invertieren", "Nachtmodus: kehrt die Notenfarben um", settings.invert) { set(KEY_INVERT, it.toString()) }
            ToggleRow("Bildschirm wach halten", "Display bleibt beim Lesen an", settings.keepScreenOn) { set(KEY_KEEP_ON, it.toString()) }
            ToggleRow("Seitenzahlen", "Seitennummer unten im Viewer anzeigen", settings.pageNumbers) { set(KEY_PAGE_NUMBERS, it.toString()) }
            ToggleRow("Warmer Lesemodus", "Wärmerer Farbton über den Noten", settings.warmTint) { set(KEY_WARM, it.toString()) }
            SelectRow("Abdunkeln", "Dunkler Schleier für dunkle Räume", DIM_OPTIONS, settings.dimLevelKey) { set(KEY_DIM, it) }

            SectionHeader("Notizen")
            ToggleRow("Schwebende Werkzeug-Palette", "Runde, verschiebbare Palette statt der unteren Leiste beim Annotieren", settings.floatingAnnotTools) { set(KEY_ANNOT_PALETTE, it.toString()) }
            ActionRow("Notizen sichern", "Alle Anmerkungen in eine Datei exportieren") { backupSave.launch("BasicSheets-Notizen.json") }
            ActionRow("Notizen laden", "Anmerkungen aus einer Sicherung importieren (überschreibt)") { backupLoad.launch(arrayOf("application/json")) }

            SectionHeader("Komponisten")
            ToggleRow("Komponisten-Infos", "Porträt und Wikipedia-Text aus dem Internet laden", settings.composerInfo) { set(KEY_COMPOSER_INFO, it.toString()) }
        }
    }
}

private val PAGE_MODE_OPTIONS = listOf(
    "single" to "Einzelne Seite",
    "two" to "Zwei Seiten",
    "half-turn" to "Halbe Seite",
    "scroll-v" to "Vertikal scrollen",
    "scroll-h" to "Horizontal scrollen",
)
private val BG_OPTIONS = listOf("dark" to "Dunkel", "gray" to "Grau", "white" to "Hell")
private val DIM_OPTIONS = listOf("0" to "Aus", "0.25" to "Leicht", "0.5" to "Mittel", "0.75" to "Stark")

@Composable
private fun SectionHeader(text: String) {
    Text(
        text,
        Modifier.padding(start = 16.dp, top = 18.dp, bottom = 4.dp),
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
    )
}

@Composable
private fun ToggleRow(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable { onChange(!checked) }.padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun ActionRow(title: String, subtitle: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SelectRow(
    title: String,
    subtitle: String,
    options: List<Pair<String, String>>,
    current: String,
    onSelect: (String) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    Box {
        Row(
            Modifier.fillMaxWidth().clickable { open = true }.padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.bodyLarge)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
            }
            Text(
                options.firstOrNull { it.first == current }?.second ?: current,
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            options.forEach { (value, label) ->
                DropdownMenuItem(
                    text = { Text(label) },
                    onClick = { open = false; onSelect(value) },
                    leadingIcon = { if (value == current) Icon(Icons.Filled.Check, contentDescription = null) },
                )
            }
        }
    }
}
