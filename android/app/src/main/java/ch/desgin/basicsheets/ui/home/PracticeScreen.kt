package ch.desgin.basicsheets.ui.home

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import ch.desgin.basicsheets.ui.theme.appTopBarColors

/** "Üben" tab — practice. Placeholder for now (practice marks arrive in a later chunk). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PracticeScreen() {
    Column(Modifier.fillMaxSize()) {
        TopAppBar(title = { Text("Üben") }, colors = appTopBarColors())
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Üben kommt bald.", color = MaterialTheme.colorScheme.outline)
        }
    }
}
