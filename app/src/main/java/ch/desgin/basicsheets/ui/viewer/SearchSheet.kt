package ch.desgin.basicsheets.ui.viewer

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import ch.desgin.basicsheets.pdf.SearchHit

/** Bottom sheet: a query field and the per-page results; tap a result to jump to its page. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchSheet(
    query: String,
    onQueryChange: (String) -> Unit,
    searching: Boolean,
    results: List<SearchHit>,
    onJump: (SearchHit, Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState()
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
            OutlinedTextField(
                value = query,
                onValueChange = onQueryChange,
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Im Dokument suchen …") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                singleLine = true,
            )
            Spacer(Modifier.height(8.dp))
            when {
                searching -> Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                query.trim().length < 2 -> HintText("Suchbegriff eingeben (mind. 2 Zeichen)")
                results.isEmpty() -> HintText("Keine Treffer für «${query.trim()}»")
                else -> {
                    val total = results.sumOf { it.count }
                    Text(
                        "$total Treffer auf ${results.size} Seiten",
                        Modifier.padding(vertical = 6.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline,
                    )
                    LazyColumn(Modifier.fillMaxWidth().heightIn(max = 420.dp)) {
                        itemsIndexed(results) { i, hit ->
                            Column(Modifier.fillMaxWidth().clickable { onJump(hit, i) }.padding(vertical = 8.dp)) {
                                Text(
                                    "Seite ${hit.page + 1}" + if (hit.count > 1) "  ·  ${hit.count}×" else "",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                                Text(snippetAnnotated(hit), style = MaterialTheme.typography.bodyMedium, maxLines = 2)
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun HintText(text: String) {
    Text(
        text,
        Modifier.fillMaxWidth().padding(20.dp),
        textAlign = TextAlign.Center,
        color = MaterialTheme.colorScheme.outline,
        style = MaterialTheme.typography.bodyMedium,
    )
}

private fun snippetAnnotated(hit: SearchHit): AnnotatedString = buildAnnotatedString {
    val s = hit.snippet
    val a = hit.matchStart.coerceIn(0, s.length)
    val b = (hit.matchStart + hit.matchLen).coerceIn(a, s.length)
    append(s.substring(0, a))
    withStyle(SpanStyle(fontWeight = FontWeight.Bold, background = Color(0x55FFD200))) { append(s.substring(a, b)) }
    append(s.substring(b))
}
