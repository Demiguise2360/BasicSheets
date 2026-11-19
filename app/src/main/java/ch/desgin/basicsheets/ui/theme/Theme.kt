package ch.desgin.basicsheets.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.RowScope
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarColors
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

// Brand palette — petrol/teal (the Figma redesign): dark bars over a light content area.
val Petrol = Color(0xFF1B5E70)
val PetrolDark = Color(0xFF0F4350)
val PetrolLight = Color(0xFF3E8296)
val Ink = Color(0xFF16262B)

private val LightColors = lightColorScheme(
    primary = Petrol,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFBFE3EC),
    onPrimaryContainer = Color(0xFF06333D),
    secondary = Color(0xFF4A7682),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFCDE7EE),
    onSecondaryContainer = Color(0xFF06333D),
    tertiary = PetrolLight,
    onTertiary = Color.White,
    background = Color(0xFFF3F5F4),
    onBackground = Ink,
    surface = Color.White,
    onSurface = Ink,
    surfaceVariant = Color(0xFFE4EAEA),
    onSurfaceVariant = Color(0xFF53676C),
    outline = Color(0xFF8CA0A4),
    outlineVariant = Color(0xFFCFD8D9),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF53A0B2),
    onPrimary = Color(0xFF00272F),
    primaryContainer = Color(0xFF123F4B),
    onPrimaryContainer = Color(0xFFBFE3EC),
    secondary = Color(0xFF7FB0BD),
    onSecondary = Color(0xFF002B36),
    tertiary = Color(0xFF6FAEBE),
    background = Color(0xFF0E1416),
    onBackground = Color(0xFFE1E7E7),
    surface = Color(0xFF151D20),
    onSurface = Color(0xFFE1E7E7),
    surfaceVariant = Color(0xFF3A484C),
    onSurfaceVariant = Color(0xFFBEC9CB),
    outlineVariant = Color(0xFF34464B),
)

@Composable
fun BasicSheetsTheme(darkTheme: Boolean = false, content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = Typography(),
        content = content,
    )
}

/** The dark petrol top bar used across the app (white title/icons), matching the Figma. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun appTopBarColors(): TopAppBarColors = TopAppBarDefaults.topAppBarColors(
    containerColor = MaterialTheme.colorScheme.primary,
    scrolledContainerColor = MaterialTheme.colorScheme.primary,
    titleContentColor = MaterialTheme.colorScheme.onPrimary,
    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary,
    actionIconContentColor = MaterialTheme.colorScheme.onPrimary,
)

/**
 * The Figma brand gradient: a diagonal petrol sweep (dark top-left → lighter bottom-right) used
 * behind every top app bar and the bottom navigation bar. Built from the petrol brand tokens.
 */
val PetrolGradient: Brush = Brush.linearGradient(listOf(PetrolDark, Petrol, PetrolLight))

/** Transparent top-bar colours (white content) so the [PetrolGradient] behind the bar shows through. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun transparentTopBarColors(): TopAppBarColors = TopAppBarDefaults.topAppBarColors(
    containerColor = Color.Transparent,
    scrolledContainerColor = Color.Transparent,
    titleContentColor = Color.White,
    navigationIconContentColor = Color.White,
    actionIconContentColor = Color.White,
)

/** A petrol-gradient top app bar (the Figma header) — a transparent [TopAppBar] over [PetrolGradient]. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppTopBar(
    title: @Composable () -> Unit,
    navigationIcon: @Composable () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {},
) {
    Box(Modifier.background(PetrolGradient)) {
        TopAppBar(title = title, navigationIcon = navigationIcon, actions = actions, colors = transparentTopBarColors())
    }
}
