package ch.desgin.basicsheets.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface SheetDao {
    @Query("SELECT * FROM sheets ORDER BY name COLLATE NOCASE")
    fun observeAll(): Flow<List<Sheet>>

    @Query("SELECT * FROM sheets ORDER BY name COLLATE NOCASE")
    suspend fun all(): List<Sheet>

    @Query("SELECT * FROM sheets WHERE id = :id")
    suspend fun byId(id: Int): Sheet?

    @Query("SELECT * FROM sheets WHERE filePath = :file LIMIT 1")
    suspend fun byFile(file: String): Sheet?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(sheet: Sheet): Long

    @Update suspend fun update(sheet: Sheet)
    @Delete suspend fun delete(sheet: Sheet)
}

@Dao
interface SetlistDao {
    @Query("SELECT * FROM setlists ORDER BY sortOrder, name COLLATE NOCASE")
    fun observeAll(): Flow<List<Setlist>>

    @Query("SELECT * FROM setlists ORDER BY sortOrder, name COLLATE NOCASE")
    suspend fun all(): List<Setlist>

    @Query("SELECT * FROM setlists WHERE id = :id")
    suspend fun byId(id: Int): Setlist?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(setlist: Setlist): Long
    @Update suspend fun update(setlist: Setlist)
    @Delete suspend fun delete(setlist: Setlist)

    @Query("SELECT * FROM setlist_items WHERE setlistId = :setlistId ORDER BY `order`")
    suspend fun items(setlistId: Int): List<SetlistItem>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertItem(item: SetlistItem): Long
    @Update suspend fun updateItem(item: SetlistItem)
    @Delete suspend fun deleteItem(item: SetlistItem)

    @Query("DELETE FROM setlist_items WHERE setlistId = :setlistId")
    suspend fun clearItems(setlistId: Int)

    /** Sheets of a setlist, in order. */
    @Query(
        "SELECT s.* FROM sheets s JOIN setlist_items i ON i.sheetId = s.id " +
            "WHERE i.setlistId = :setlistId ORDER BY i.`order`"
    )
    suspend fun sheets(setlistId: Int): List<Sheet>
}

@Dao
interface BookmarkDao {
    @Query("SELECT * FROM bookmarks WHERE pdfFile = :file ORDER BY page")
    suspend fun forFile(file: String): List<Bookmark>

    @Query("SELECT * FROM bookmarks ORDER BY sheetName COLLATE NOCASE, page")
    suspend fun all(): List<Bookmark>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(bookmark: Bookmark)
    @Query("DELETE FROM bookmarks WHERE id = :id")
    suspend fun remove(id: String)
}

@Dao
interface ViewerButtonDao {
    @Query("SELECT * FROM viewer_buttons WHERE pdfFile = :file")
    suspend fun forFile(file: String): List<ViewerButton>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(button: ViewerButton)
    @Update suspend fun update(button: ViewerButton)
    @Query("DELETE FROM viewer_buttons WHERE id = :id")
    suspend fun remove(id: String)
}

@Dao
interface CollectionDao {
    @Query("SELECT * FROM collections ORDER BY name COLLATE NOCASE")
    suspend fun all(): List<Collection>
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(collection: Collection): Long
    @Delete suspend fun delete(collection: Collection)

    @Query("SELECT collectionId FROM sheet_collections WHERE sheetId = :sheetId")
    suspend fun collectionIdsFor(sheetId: Int): List<Int>
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun link(link: SheetCollectionLink)
    @Query("DELETE FROM sheet_collections WHERE sheetId = :sheetId AND collectionId = :collectionId")
    suspend fun unlink(sheetId: Int, collectionId: Int)
}

@Dao
interface CropDao {
    @Query("SELECT * FROM crops WHERE sheetId = :sheetId")
    suspend fun forSheet(sheetId: Int): List<Crop>
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(crop: Crop)
    @Delete suspend fun delete(crop: Crop)
    @Query("DELETE FROM crops WHERE sheetId = :sheetId AND page = :page")
    suspend fun clear(sheetId: Int, page: Int)
}

@Dao
interface SheetFileDao {
    @Query("SELECT * FROM sheet_files WHERE sheetId = :sheetId ORDER BY `order`")
    suspend fun forSheet(sheetId: Int): List<SheetFile>
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(file: SheetFile): Long
    @Delete suspend fun delete(file: SheetFile)
}

@Dao
interface SettingDao {
    @Query("SELECT * FROM settings_kv")
    suspend fun all(): List<SettingRow>
    @Query("SELECT value FROM settings_kv WHERE name = :name LIMIT 1")
    suspend fun value(name: String): String?
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun put(row: SettingRow)
}

@Dao
interface AnnotationDao {
    @Query("SELECT json FROM annotations WHERE pdfKey = :pdfKey AND page = :page LIMIT 1")
    suspend fun get(pdfKey: String, page: Int): String?

    @Query("SELECT page FROM annotations WHERE pdfKey = :pdfKey")
    suspend fun annotatedPages(pdfKey: String): List<Int>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun put(row: AnnotationRow)

    @Query("DELETE FROM annotations WHERE pdfKey = :pdfKey AND page = :page")
    suspend fun clear(pdfKey: String, page: Int)
}
