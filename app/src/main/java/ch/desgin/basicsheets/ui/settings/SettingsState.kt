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
const val KEY_ANNOT_PALETTE = "Schwebende Palette"
const val KEY_WARM = "Warmer Lesemodus"
const val KEY_DIM = "Abdunkeln"
const val KEY_VOLUME_KEYS = "Lautstärketasten blättern"
const val KEY_VOLUME_UP_FWD = "Lauter blättert vor"
const val KEY_NEXT_KEYCODE = "Taste nächste Seite"
const val KEY_PREV_KEYCODE = "Taste vorige Seite"
const val KEY_PALM_REJECT = "Handballenerkennung"
const val KEY_PEN_BUTTON_ERASE = "Stift-Taste radiert"
const val KEY_HAPTICS = "Haptisches Feedback"

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

    /** Use the floating round tool palette instead of the bottom bar when annotating. */
    val floatingAnnotTools: Boolean get() = bool(KEY_ANNOT_PALETTE, false)

    /** Warm (amber) reading tint over the page — less blue light. */
    val warmTint: Boolean get() = bool(KEY_WARM, false)
    /** Screen-dim overlay strength, stored as its key ("0".."0.75"). */
    val dimLevelKey: String get() = str(KEY_DIM, "0")
    val dimLevel: Float get() = dimLevelKey.toFloatOrNull() ?: 0f

    /** Hardware volume keys turn pages. */
    val volumeKeysPaging: Boolean get() = bool(KEY_VOLUME_KEYS, false)
    /** When true, volume-up = next / volume-down = previous (default is the reverse). */
    val volumeUpForward: Boolean get() = bool(KEY_VOLUME_UP_FWD, false)
    /** Extra user-bound hardware key (Android key code) for next page; 0 = none. */
    val nextKeyCode: Int get() = str(KEY_NEXT_KEYCODE, "0").toIntOrNull() ?: 0
    /** Extra user-bound hardware key (Android key code) for previous page; 0 = none. */
    val prevKeyCode: Int get() = str(KEY_PREV_KEYCODE, "0").toIntOrNull() ?: 0

    /** Draw with a stylus only — finger/palm touches are ignored while annotating (palm rejection). */
    val palmRejection: Boolean get() = bool(KEY_PALM_REJECT, false)
    /** A stylus side button erases instead of drawing, whatever tool is selected. */
    val penButtonErase: Boolean get() = bool(KEY_PEN_BUTTON_ERASE, false)

    /** Short vibration on a manual page turn (tap / nav button / hardware key). */
    val haptics: Boolean get() = bool(KEY_HAPTICS, true)
}

val LocalSettings = compositionLocalOf { SettingsSnapshot() }
