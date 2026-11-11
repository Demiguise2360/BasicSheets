package ch.desgin.basicsheets.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        Sheet::class, Setlist::class, SetlistItem::class, Bookmark::class,
        ViewerButton::class, Collection::class, SheetCollectionLink::class,
        SheetFile::class, Crop::class, SettingRow::class, AnnotationRow::class,
    ],
    version = 1,
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
                ).build().also { instance = it }
            }
    }
}
