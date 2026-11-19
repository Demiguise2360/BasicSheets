package ch.desgin.basicsheets.data

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import ch.desgin.basicsheets.pdf.PdfDoc
import kotlinx.coroutines.flow.Flow
import java.io.File

/** Library CRUD + PDF import (copies the picked file into app-private storage). */
class LibraryRepository(
    private val db: BasicSheetsDatabase,
    private val sheetsDir: File,
) {
    fun observeSheets(): Flow<List<Sheet>> = db.sheets().observeAll()
    fun observePractice(): Flow<List<Sheet>> = db.sheets().observePractice()
    suspend fun all(): List<Sheet> = db.sheets().all()
    suspend fun byId(id: Int): Sheet? = db.sheets().byId(id)

    /** Record that a sheet was just opened (for the Zuletzt tab). */
    suspend fun markOpened(id: Int) = db.sheets().markOpened(id, System.currentTimeMillis())

    /** Mark / unmark a sheet "zum Üben" (shown in the Üben tab). */
    suspend fun setPractice(sheet: Sheet, on: Boolean) =
        db.sheets().update(
            sheet.copy(
                practiceMarkedAt = if (on) System.currentTimeMillis() else null,
                updated = System.currentTimeMillis(),
            )
        )
    suspend fun byFile(file: String): Sheet? = db.sheets().byFile(file)

    suspend fun update(sheet: Sheet) =
        db.sheets().update(sheet.copy(updated = System.currentTimeMillis()))

    suspend fun delete(sheet: Sheet) {
        db.sheets().delete(sheet)
        // Remove the song's part files (multi-file songs) and the primary file, each only if no
        // other sheet still points at it.
        val parts = runCatching { db.sheetFiles().forSheet(sheet.id) }.getOrDefault(emptyList())
        for (p in parts) {
            db.sheetFiles().delete(p)
            if (db.sheets().byFile(p.path) == null) runCatching { File(sheetsDir, p.path).delete() }
        }
        if (db.sheets().byFile(sheet.filePath) == null) {
            runCatching { File(sheetsDir, sheet.filePath).delete() }
        }
    }

    /** The on-disk part files of a multi-file song, in order; empty for a plain single-file song. */
    suspend fun partFiles(sheet: Sheet): List<File> =
        runCatching { db.sheetFiles().forSheet(sheet.id) }.getOrDefault(emptyList())
            .sortedBy { it.order }.map { File(sheetsDir, it.path) }

    /** The on-disk file for a sheet's primary PDF. */
    fun fileFor(sheet: Sheet): File = File(sheetsDir, sheet.filePath)

    /** The on-disk backing-track audio file for a sheet, or null if none attached. */
    fun audioFileFor(sheet: Sheet): File? =
        sheet.audioPath.takeIf { it.isNotBlank() }?.let { File(sheetsDir, it) }

    /** Copy a picked audio file into app storage and set it as the sheet's backing track. */
    suspend fun attachAudio(context: Context, uri: Uri, sheet: Sheet): Sheet? {
        val display = queryDisplayName(context, uri) ?: "audio.mp3"
        val ext = display.substringAfterLast('.', "mp3").ifBlank { "mp3" }
        val fileName = "audio-${System.currentTimeMillis()}.$ext"
        val dest = File(sheetsDir, fileName)
        val ok = runCatching {
            context.contentResolver.openInputStream(uri)?.use { input ->
                dest.outputStream().use { output -> input.copyTo(output) }
                true
            } ?: false
        }.getOrDefault(false)
        if (!ok) {
            runCatching { dest.delete() }
            return null
        }
        val updated = sheet.copy(audioPath = fileName, updated = System.currentTimeMillis())
        db.sheets().update(updated)
        return updated
    }

    /** Detach + delete the sheet's backing track. */
    suspend fun removeAudio(sheet: Sheet): Sheet {
        sheet.audioPath.takeIf { it.isNotBlank() }?.let { runCatching { File(sheetsDir, it).delete() } }
        val updated = sheet.copy(audioPath = "", updated = System.currentTimeMillis())
        db.sheets().update(updated)
        return updated
    }

    /** Copy a picked PDF or image into app storage and create a Sheet. Returns the new row id, or null. */
    suspend fun importFile(context: Context, uri: Uri): Long? {
        val display = queryDisplayName(context, uri) ?: "Noten.pdf"
        val base = display.substringBeforeLast('.').ifBlank { "Noten" }
        val ext = display.substringAfterLast('.', "pdf").lowercase().ifBlank { "pdf" }
        val fileName = uniqueFileName(base, ext)
        val dest = File(sheetsDir, fileName)
        val copied = runCatching {
            context.contentResolver.openInputStream(uri)?.use { input ->
                dest.outputStream().use { output -> input.copyTo(output) }
                true
            } ?: false
        }.getOrDefault(false)
        if (!copied) {
            runCatching { dest.delete() }
            return null
        }
        return db.sheets().insert(Sheet(name = base, filePath = fileName))
    }

    /**
     * Import several picked files as ONE multi-file song: copy each into app storage, create one
     * Sheet (named after the first file, its primary = the first copy), and a `sheet_files` row per
     * part in pick order. A single URI falls through to [importFile] (a plain song, no parts).
     */
    suspend fun importFiles(context: Context, uris: List<Uri>): Long? {
        if (uris.isEmpty()) return null
        if (uris.size == 1) return importFile(context, uris[0])
        val firstDisplay = queryDisplayName(context, uris[0]) ?: "Stück.pdf"
        val songName = firstDisplay.substringBeforeLast('.').ifBlank { "Stück" }
        val parts = ArrayList<SheetFile>()
        var primary: String? = null
        for ((i, uri) in uris.withIndex()) {
            val display = queryDisplayName(context, uri) ?: "Teil-${i + 1}.pdf"
            val ext = display.substringAfterLast('.', "pdf").lowercase().ifBlank { "pdf" }
            // The "-$i" keeps names unique even within one millisecond (same-ms import loop).
            val fileName = uniqueFileName(display.substringBeforeLast('.').ifBlank { "Teil" } + "-$i", ext)
            val dest = File(sheetsDir, fileName)
            val ok = runCatching {
                context.contentResolver.openInputStream(uri)?.use { input ->
                    dest.outputStream().use { output -> input.copyTo(output) }
                    true
                } ?: false
            }.getOrDefault(false)
            if (ok) {
                if (primary == null) primary = fileName
                parts.add(SheetFile(path = fileName, kind = if (PdfDoc.isImage(fileName)) "image" else "pdf", order = i, label = display))
            } else {
                runCatching { dest.delete() }
            }
        }
        val primaryFile = primary ?: return null
        val id = db.sheets().insert(Sheet(name = songName, filePath = primaryFile))
        for (sf in parts) db.sheetFiles().insert(sf.copy(sheetId = id.toInt()))
        return id
    }

    private fun uniqueFileName(base: String, ext: String = "pdf"): String {
        val safe = base.replace(Regex("[^A-Za-z0-9 _.-]"), "_").trim().ifBlank { "Noten" }
        val cleanExt = ext.replace(Regex("[^A-Za-z0-9]"), "").ifBlank { "pdf" }
        return "$safe-${System.currentTimeMillis()}.$cleanExt"
    }

    private fun queryDisplayName(context: Context, uri: Uri): String? = runCatching {
        context.contentResolver.query(uri, null, null, null, null)?.use { c ->
            val idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (idx >= 0 && c.moveToFirst()) c.getString(idx) else null
        }
    }.getOrNull()
}
