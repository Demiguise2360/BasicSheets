package ch.desgin.basicsheets

import android.app.Application
import ch.desgin.basicsheets.data.AnnotationRepository
import ch.desgin.basicsheets.data.BasicSheetsDatabase
import ch.desgin.basicsheets.data.BookmarkRepository
import ch.desgin.basicsheets.data.LibraryRepository
import ch.desgin.basicsheets.data.SettingsRepository
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import java.io.File

/** App singleton: owns the Room database, the PDF folder, and the repositories. */
class BasicSheetsApp : Application() {

    val db: BasicSheetsDatabase by lazy { BasicSheetsDatabase.get(this) }

    /** Where imported score files are copied to (app-private storage). */
    val sheetsDir: File by lazy { File(filesDir, "sheets").apply { mkdirs() } }

    val library: LibraryRepository by lazy { LibraryRepository(db, sheetsDir) }
    val settings: SettingsRepository by lazy { SettingsRepository(db) }
    val annotations: AnnotationRepository by lazy { AnnotationRepository(db) }
    val bookmarks: BookmarkRepository by lazy { BookmarkRepository(db) }

    override fun onCreate() {
        super.onCreate()
        instance = this
        PDFBoxResourceLoader.init(applicationContext)   // init PDFBox before any outline/text parse
    }

    companion object {
        lateinit var instance: BasicSheetsApp
            private set
    }
}
