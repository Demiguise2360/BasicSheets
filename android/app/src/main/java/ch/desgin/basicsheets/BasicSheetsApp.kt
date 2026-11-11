package ch.desgin.basicsheets

import android.app.Application
import ch.desgin.basicsheets.data.BasicSheetsDatabase
import ch.desgin.basicsheets.data.LibraryRepository
import ch.desgin.basicsheets.data.SettingsRepository
import java.io.File

/** App singleton: owns the Room database, the PDF folder, and the repositories. */
class BasicSheetsApp : Application() {

    val db: BasicSheetsDatabase by lazy { BasicSheetsDatabase.get(this) }

    /** Where imported score files are copied to (app-private storage). */
    val sheetsDir: File by lazy { File(filesDir, "sheets").apply { mkdirs() } }

    val library: LibraryRepository by lazy { LibraryRepository(db, sheetsDir) }
    val settings: SettingsRepository by lazy { SettingsRepository(db) }

    override fun onCreate() {
        super.onCreate()
        instance = this
    }

    companion object {
        lateinit var instance: BasicSheetsApp
            private set
    }
}
