package ch.desgin.basicsheets.ui.library

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import ch.desgin.basicsheets.BasicSheetsApp
import ch.desgin.basicsheets.data.Sheet
import ch.desgin.basicsheets.ui.theme.appTopBarColors
import kotlinx.coroutines.launch

/** Difficulty values (MAUI's set) mapped to German labels. "" = none. */
private val DIFFICULTY = listOf(
    "" to "Keine",
    "beginner" to "Anfänger",
    "intermediate" to "Mittel",
    "advanced" to "Fortgeschritten",
    "expert" to "Experte",
)

/**
 * Song metadata editor — the "Bearbeiten" screen. Edits a sheet's title, composer, voice, genre,
 * year, difficulty, rating, tags, keywords and remarks, then saves back through the library repo
 * (the PDF, page order, annotations etc. are untouched). Reached from the library card's ⋮ menu.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SheetEditorScreen(sheetId: Int, onBack: () -> Unit) {
    val app = BasicSheetsApp.instance
    val scope = rememberCoroutineScope()

    var loaded by remember { mutableStateOf<Sheet?>(null) }
    var name by remember { mutableStateOf("") }
    var composer by remember { mutableStateOf("") }
    var part by remember { mutableStateOf("") }
    var genre by remember { mutableStateOf("") }
    var year by remember { mutableStateOf("") }
    var difficulty by remember { mutableStateOf("") }
    var rating by remember { mutableIntStateOf(0) }
    var tags by remember { mutableStateOf("") }
    var keywords by remember { mutableStateOf("") }
    var remarks by remember { mutableStateOf("") }
    var rangeStart by remember { mutableStateOf("") }
    var rangeEnd by remember { mutableStateOf("") }

    LaunchedEffect(sheetId) {
        val s = runCatching { app.library.byId(sheetId) }.getOrNull() ?: return@LaunchedEffect
        name = s.name; composer = s.composer; part = s.part; genre = s.genre; year = s.year
        difficulty = s.difficulty; rating = s.rating; tags = s.tags; keywords = s.keywords; remarks = s.remarks
        rangeStart = if (s.pageStart > 0) s.pageStart.toString() else ""
        rangeEnd = if (s.pageEnd > 0) s.pageEnd.toString() else ""
        loaded = s
    }

    fun save() {
        val s = loaded ?: return
        scope.launch {
            app.library.update(
                s.copy(
                    name = name.trim(), composer = composer.trim(), part = part.trim(),
                    genre = genre.trim(), year = year.trim(), difficulty = difficulty,
                    rating = rating, tags = tags.trim(), keywords = keywords.trim(), remarks = remarks.trim(),
                    pageStart = rangeStart.toIntOrNull()?.coerceAtLeast(0) ?: 0,
                    pageEnd = rangeEnd.toIntOrNull()?.coerceAtLeast(0) ?: 0,
                ),
            )
            onBack()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Bearbeiten") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Zurück") }
                },
                actions = {
                    TextButton(
                        onClick = { save() },
                        enabled = loaded != null && name.isNotBlank(),
                    ) { Text("Speichern", color = MaterialTheme.colorScheme.onPrimary) }
                },
                colors = appTopBarColors(),
            )
        },
    ) { pad ->
        if (loaded == null) {
            Column(Modifier.fillMaxSize().padding(pad), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }
        Column(
            Modifier.fillMaxSize().padding(pad).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Field("Titel", name, { name = it })
            Field("Komponist", composer, { composer = it })
            Field("Stimme", part, { part = it })
            Field("Genre", genre, { genre = it })
            Field("Jahr", year, { year = it }, keyboard = KeyboardType.Number)

            LabelText("Schwierigkeit")
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for ((value, label) in DIFFICULTY) {
                    FilterChip(selected = difficulty == value, onClick = { difficulty = value }, label = { Text(label) })
                }
            }

            LabelText("Bewertung")
            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                for (i in 1..5) {
                    IconButton(onClick = { rating = if (rating == i) 0 else i }, modifier = Modifier.size(40.dp)) {
                        Icon(
                            if (i <= rating) Icons.Filled.Star else Icons.Filled.StarBorder,
                            contentDescription = "$i Sterne",
                            tint = if (i <= rating) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                        )
                    }
                }
            }

            Field("Tags (mit Komma)", tags, { tags = it })
            Field("Stichwörter", keywords, { keywords = it })
            Field("Bemerkungen", remarks, { remarks = it }, singleLine = false, minLines = 3)

            LabelText("Seitenbereich (optional)")
            Text(
                "Nur diese Seiten der PDF zeigen – z. B. ein Stück in einer Sammel-PDF. Leer = ganze Datei.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline,
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Field("Von Seite", rangeStart, { rangeStart = it.filter(Char::isDigit) }, keyboard = KeyboardType.Number, modifier = Modifier.weight(1f))
                Field("Bis Seite", rangeEnd, { rangeEnd = it.filter(Char::isDigit) }, keyboard = KeyboardType.Number, modifier = Modifier.weight(1f))
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun Field(
    label: String,
    value: String,
    onChange: (String) -> Unit,
    singleLine: Boolean = true,
    minLines: Int = 1,
    keyboard: KeyboardType = KeyboardType.Text,
    modifier: Modifier = Modifier.fillMaxWidth(),
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        singleLine = singleLine,
        minLines = minLines,
        keyboardOptions = KeyboardOptions(keyboardType = keyboard),
        modifier = modifier,
    )
}

@Composable
private fun LabelText(text: String) {
    Text(text, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
}
