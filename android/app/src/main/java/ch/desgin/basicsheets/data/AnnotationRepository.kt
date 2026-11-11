package ch.desgin.basicsheets.data

import ch.desgin.basicsheets.annot.PageAnnotations
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
}
