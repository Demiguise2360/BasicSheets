package ch.desgin.basicsheets

import android.app.Application
import ch.desgin.basicsheets.data.BasicSheetsDatabase
import java.io.File

/** App singleton: owns the Room database and the folder that holds imported PDFs. */
class BasicSheetsApp : Application() {

    val db: BasicSheetsDatabase by lazy { BasicSheetsDatabase.get(this) }

    /** Where imported score files are copied to (app-private storage). */
    val sheetsDir: File by lazy { File(filesDir, "sheets").apply { mkdirs() } }

    override fun onCreate() {
        super.onCreate()
        instance = this
    }

    companion object {
        lateinit var instance: BasicSheetsApp
            private set
    }
}
