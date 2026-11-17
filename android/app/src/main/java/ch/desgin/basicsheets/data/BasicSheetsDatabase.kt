package ch.desgin.basicsheets.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        Sheet::class, Setlist::class, SetlistItem::class, Bookmark::class,
        ViewerButton::class, Collection::class, SheetCollectionLink::class,
        SheetFile::class, Crop::class, SettingRow::class, AnnotationRow::class,
    ],
    version = 3,
    exportSchema = false,
)
abstract class BasicSheetsDatabase : RoomDatabase() {
    abstract fun sheets(): SheetDao
    abstract fun setlists(): SetlistDao
    abstract fun bookmarks(): BookmarkDao
    abstract fun viewerButtons(): ViewerButtonDao
    abstract fun collections(): CollectionDao
    abstract fun crops(): CropDao
    abstract fun sheetFiles(): SheetFileDao
    abstract fun settings(): SettingDao
    abstract fun annotations(): AnnotationDao

    companion object {
        @Volatile private var instance: BasicSheetsDatabase? = null

        fun get(context: Context): BasicSheetsDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    BasicSheetsDatabase::class.java,
                    "basicsheets.db",
                ).addMigrations(MIGRATION_1_2, MIGRATION_2_3).build().also { instance = it }
            }

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE collections ADD COLUMN color TEXT")
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE sheets ADD COLUMN practiceMarkedAt INTEGER")
            }
        }
    }
}
