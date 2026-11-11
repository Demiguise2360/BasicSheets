package ch.desgin.basicsheets.data

/**
 * Thin accessor over the settings_kv table. The MAUI app rebuilds the full Setting definitions
 * (name/description/type/options) in code and persists only the chosen value; the complete
 * catalogue + the Settings UI arrive in the Settings phase. For now this exposes get/set with a
 * map of known defaults.
 */
class SettingsRepository(private val db: BasicSheetsDatabase) {

    suspend fun get(name: String, default: String = ""): String =
        db.settings().value(name) ?: DEFAULTS[name] ?: default

    suspend fun set(name: String, value: String) = db.settings().put(SettingRow(name, value))

    suspend fun bool(name: String, default: Boolean): Boolean =
        when (db.settings().value(name) ?: DEFAULTS[name]) {
            null -> default
            "false" -> false
            else -> true
        }

    suspend fun int(name: String, default: Int): Int =
        (db.settings().value(name) ?: DEFAULTS[name])?.toIntOrNull() ?: default

    companion object {
        /** Known default values (mirrors SettingsService.cs; completed in the Settings phase). */
        val DEFAULTS: Map<String, String> = mapOf(
            "Sortierung" to "name",
            "Dunkles Design" to "false",
            "Start und End Hinweis" to "true",
            "Toolbar automatisch ausblenden" to "false",
            "Seitenübergang animieren" to "false",
            "Bildschirm wach halten" to "false",
            "Seiten automatisch umblättern" to "off",
            "Viewer-Hintergrund" to "dark",
            "Noten invertieren" to "false",
            "Seitenzahlen" to "true",
            "Stiftdicke" to "medium",
            "Stiftfarbe" to "black",
            "Standard-Notizwerkzeug" to "pen",
            "Letzte Seite merken" to "true",
            "Stift: Bearbeitungsmodus" to "true",
            "Stift: Seitentaste" to "hold",
            "Stift: Druckstärke" to "true",
            "Stift: Handballenerkennung" to "true",
            "Haptisches Feedback" to "true",
            "Metronom: Auto-Blättern" to "0",
        )
    }
}
