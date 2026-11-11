package ch.desgin.basicsheets.data

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import kotlinx.coroutines.flow.Flow
import java.io.File

/** Library CRUD + PDF import (copies the picked file into app-private storage). */
class LibraryRepository(
    private val db: BasicSheetsDatabase,
    private val sheetsDir: File,
) {
    fun observeSheets(): Flow<List<Sheet>> = db.sheets().observeAll()
    suspend fun all(): List<Sheet> = db.sheets().all()
    suspend fun byId(id: Int): Sheet? = db.sheets().byId(id)
    suspend fun byFile(file: String): Sheet? = db.sheets().byFile(file)

    suspend fun update(sheet: Sheet) =
        db.sheets().update(sheet.copy(updated = System.currentTimeMillis()))

    suspend fun delete(sheet: Sheet) {
        db.sheets().delete(sheet)
        // remove the copied file if nothing else references it
        if (db.sheets().byFile(sheet.filePath) == null) {
            runCatching { File(sheetsDir, sheet.filePath).delete() }
        }
    }

    /** The on-disk file for a sheet's primary PDF. */
    fun fileFor(sheet: Sheet): File = File(sheetsDir, sheet.filePath)

    /** Copy a picked PDF into app storage and create a Sheet. Returns the new row id, or null. */
    suspend fun importPdf(context: Context, uri: Uri): Long? {
        val display = queryDisplayName(context, uri) ?: "Noten.pdf"
        val base = display.substringBeforeLast('.').ifBlank { "Noten" }
        val fileName = uniqueFileName(base)
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

    private fun uniqueFileName(base: String): String {
        val safe = base.replace(Regex("[^A-Za-z0-9 _.-]"), "_").trim().ifBlank { "Noten" }
        return "$safe-${System.currentTimeMillis()}.pdf"
    }

    private fun queryDisplayName(context: Context, uri: Uri): String? = runCatching {
        context.contentResolver.query(uri, null, null, null, null)?.use { c ->
            val idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (idx >= 0 && c.moveToFirst()) c.getString(idx) else null
        }
    }.getOrNull()
}
