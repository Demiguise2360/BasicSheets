package ch.desgin.basicsheets

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import ch.desgin.basicsheets.ui.AppRoot
import ch.desgin.basicsheets.ui.settings.LocalSettings
import ch.desgin.basicsheets.ui.settings.SettingsSnapshot
import ch.desgin.basicsheets.ui.theme.BasicSheetsTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val map by BasicSheetsApp.instance.settings.observe().collectAsState(initial = emptyMap())
            val settings = SettingsSnapshot(map)
            BasicSheetsTheme(darkTheme = settings.darkTheme) {
                CompositionLocalProvider(LocalSettings provides settings) {
                    Surface(Modifier.fillMaxSize()) {
                        AppRoot()
                    }
                }
            }
        }
    }
}
