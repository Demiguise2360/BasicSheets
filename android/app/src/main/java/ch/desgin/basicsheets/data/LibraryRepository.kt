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
    fun observePractice(): Flow<List<Sheet>> = db.sheets().observePractice()
    suspend fun all(): List<Sheet> = db.sheets().all()
    suspend fun byId(id: Int): Sheet? = db.sheets().byId(id)

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
        // remove the copied file if nothing else references it
        if (db.sheets().byFile(sheet.filePath) == null) {
            runCatching { File(sheetsDir, sheet.filePath).delete() }
        }
    }

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
