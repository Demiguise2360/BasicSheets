package ch.desgin.basicsheets.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Brand palette (the MAUI app's teal).
val Teal = Color(0xFF4D8A80)
val TealDark = Color(0xFF345E58)
val TealLight = Color(0xFF6BA89E)
val Ink = Color(0xFF1E2A28)

private val LightColors = lightColorScheme(
    primary = Teal,
    onPrimary = Color.White,
    secondary = TealDark,
    tertiary = TealLight,
    background = Color(0xFFF7F7F5),
    surface = Color.White,
)

@Composable
fun BasicSheetsTheme(content: @Composable () -> Unit) {
    // The library/settings chrome is light with teal accents (like "Meine Noten"); the PDF
    // viewer manages its own dark/night rendering later.
    MaterialTheme(
        colorScheme = LightColors,
        typography = Typography(),
        content = content,
    )
}
