package ch.desgin.basicsheets.data

import ch.desgin.basicsheets.annot.PageAnnotations
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** Per-page annotation persistence — the `annotations` Room table keyed by (pdfKey, page). */
class AnnotationRepository(private val db: BasicSheetsDatabase) {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = false }

    suspend fun load(pdfKey: String, page: Int): PageAnnotations =
        db.annotations().get(pdfKey, page)
            ?.let { runCatching { json.decodeFromString<PageAnnotations>(it) }.getOrNull() }
            ?: PageAnnotations()

    suspend fun save(pdfKey: String, page: Int, data: PageAnnotations) {
        if (data.isEmpty()) db.annotations().clear(pdfKey, page)
        else db.annotations().put(AnnotationRow(pdfKey, page, json.encodeToString(data)))
    }

    suspend fun annotatedPages(pdfKey: String): Set<Int> =
        db.annotations().annotatedPages(pdfKey).toSet()

    /** Serialize every page's annotations to a backup JSON string (song names included for restore remapping). */
    suspend fun exportAllJson(): String {
        val rows = db.annotations().all()
        val nameByFile = db.sheets().all().associate { it.filePath to it.name }
        val backup = AnnotBackup(
            exported = System.currentTimeMillis(),
            count = rows.size,
            data = rows.map { AnnotEntry(it.pdfKey, it.page, it.json, nameByFile[it.pdfKey] ?: "") },
        )
        return json.encodeToString(backup)
    }

    /**
     * Restore annotations from a backup JSON string. Each page is upserted under the PDF it still
     * belongs to: the original file key if that file is still present, else remapped to the current
     * song of the same name (so a re-imported PDF — which gets a new file name — keeps its notes).
     * Returns the number of pages written, or -1 on an unreadable file.
     */
    suspend fun importJson(text: String): Int {
        val backup = runCatching { json.decodeFromString<AnnotBackup>(text) }.getOrNull() ?: return -1
        if (backup.data.isEmpty()) return 0
        val sheets = db.sheets().all()
        val files = sheets.mapTo(HashSet()) { it.filePath }
        val fileByName = sheets.groupBy { it.name.trim().lowercase() }
        var n = 0
        for (e in backup.data) {
            if (e.json.isBlank()) continue
            val key = when {
                e.pdfKey in files -> e.pdfKey
                e.sheet.isNotBlank() -> fileByName[e.sheet.trim().lowercase()]?.firstOrNull()?.filePath ?: e.pdfKey
                else -> e.pdfKey
            }
            runCatching { db.annotations().put(AnnotationRow(key, e.page, e.json)) }.onSuccess { n++ }
        }
        return n
    }
}

@Serializable
private data class AnnotBackup(
    val app: String = "BasicSheets",
    val exported: Long = 0L,
    val count: Int = 0,
    val data: List<AnnotEntry> = emptyList(),
)

@Serializable
private data class AnnotEntry(val pdfKey: String, val page: Int, val json: String, val sheet: String = "")
