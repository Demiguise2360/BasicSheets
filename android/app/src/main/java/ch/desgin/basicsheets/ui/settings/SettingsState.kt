package ch.desgin.basicsheets.ui.settings

import androidx.compose.runtime.compositionLocalOf
import ch.desgin.basicsheets.data.SettingsRepository

// Setting keys (same names the MAUI app stores in settings_kv).
const val KEY_PAGE_MODE = "Seitenansicht"
const val KEY_DARK = "Dunkles Design"
const val KEY_BG = "Viewer-Hintergrund"
const val KEY_INVERT = "Noten invertieren"
const val KEY_KEEP_ON = "Bildschirm wach halten"
const val KEY_PAGE_NUMBERS = "Seitenzahlen"
const val KEY_COMPOSER_INFO = "Komponisten-Infos"

/** An immutable view of the settings, with typed accessors; provided app-wide via [LocalSettings]. */
data class SettingsSnapshot(val map: Map<String, String> = emptyMap()) {
    private fun raw(key: String): String? = map[key] ?: SettingsRepository.DEFAULTS[key]
    fun str(key: String, def: String): String = raw(key) ?: def
    fun bool(key: String, def: Boolean): Boolean = when (raw(key)) {
        "true" -> true
        "false" -> false
        else -> def
    }

    val pageMode: String get() = str(KEY_PAGE_MODE, "single")
    val darkTheme: Boolean get() = bool(KEY_DARK, false)
    val viewerBg: String get() = str(KEY_BG, "dark")
    val invert: Boolean get() = bool(KEY_INVERT, false)
    val keepScreenOn: Boolean get() = bool(KEY_KEEP_ON, false)
    val pageNumbers: Boolean get() = bool(KEY_PAGE_NUMBERS, true)
    val composerInfo: Boolean get() = bool(KEY_COMPOSER_INFO, true)
}

val LocalSettings = compositionLocalOf { SettingsSnapshot() }
