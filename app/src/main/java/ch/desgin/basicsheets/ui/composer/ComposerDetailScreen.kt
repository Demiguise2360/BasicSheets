package ch.desgin.basicsheets.ui.composer

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ch.desgin.basicsheets.BasicSheetsApp
import ch.desgin.basicsheets.data.Sheet
import ch.desgin.basicsheets.net.ComposerInfo
import ch.desgin.basicsheets.net.Wikipedia
import ch.desgin.basicsheets.ui.settings.LocalSettings
import ch.desgin.basicsheets.ui.theme.appTopBarColors

/**
 * A composer's page: a portrait + short Wikipedia blurb (loaded from the internet, only when the
 * user keeps "Komponisten-Infos" on in settings), a link to the full Wikipedia article, and the
 * list of that composer's pieces in the library.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ComposerDetailScreen(composer: String, onOpenSheet: (Int) -> Unit, onBack: () -> Unit) {
    val app = BasicSheetsApp.instance
    val context = LocalContext.current
    val settings = LocalSettings.current

    var pieces by remember { mutableStateOf<List<Sheet>>(emptyList()) }
    var info by remember { mutableStateOf<ComposerInfo?>(null) }
    var bitmap by remember { mutableStateOf<Bitmap?>(null) }
    var loading by remember { mutableStateOf(false) }

    LaunchedEffect(composer) {
        pieces = runCatching {
            app.library.all().filter { it.composer.trim().equals(composer.trim(), ignoreCase = true) }
        }.getOrDefault(emptyList())
    }
    LaunchedEffect(composer, settings.composerInfo) {
        info = null
        bitmap = null
        if (settings.composerInfo) {
            loading = true
            val fetched = Wikipedia.summary(composer)
            info = fetched
            fetched?.imageUrl?.let { bitmap = Wikipedia.image(it) }
            loading = false
        } else {
            loading = false
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(composer, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Zurück") }
                },
                colors = appTopBarColors(),
            )
        },
    ) { pad ->
        LazyColumn(
            Modifier.fillMaxSize().padding(pad),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Row(verticalAlignment = Alignment.Top) {
                    Box(
                        Modifier.size(96.dp).clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                        contentAlignment = Alignment.Center,
                    ) {
                        val bmp = bitmap
                        if (bmp != null) {
                            Image(
                                bitmap = bmp.asImageBitmap(),
                                contentDescription = null,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop,
                            )
                        } else {
                            Icon(
                                Icons.Filled.Person,
                                contentDescription = null,
                                modifier = Modifier.size(44.dp),
                                tint = MaterialTheme.colorScheme.outline,
                            )
                        }
                    }
                    Spacer(Modifier.width(16.dp))
                    Column(Modifier.weight(1f)) {
                        Text(composer, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Text(
                            "${pieces.size} ${if (pieces.size == 1) "Stück" else "Stücke"}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.outline,
                        )
                        val link = info?.pageUrl
                        if (link != null) {
                            TextButton(onClick = { openUrl(context, link) }, contentPadding = PaddingValues(0.dp)) {
                                Text("Wikipedia ↗")
                            }
                        }
                    }
                }
            }

            if (settings.composerInfo) {
                item {
                    val i = info
                    when {
                        loading -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                            Text("Lädt Wikipedia …", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                        }
                        i != null && i.extract.isNotBlank() -> Text(i.extract, style = MaterialTheme.typography.bodyMedium)
                        else -> Text("Keine Wikipedia-Infos gefunden.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                    }
                }
            }

            item {
                Column {
                    HorizontalDivider()
                    Text(
                        "Stücke",
                        Modifier.padding(top = 10.dp),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }

            items(pieces, key = { it.id }) { s ->
                ElevatedCard(Modifier.fillMaxWidth().clickable { onOpenSheet(s.id) }) {
                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(s.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            val sub = listOf(s.part, s.genre).filter { it.isNotBlank() }.joinToString(" · ")
                            if (sub.isNotBlank()) {
                                Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun openUrl(context: Context, url: String) {
    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
}
