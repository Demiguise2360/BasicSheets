package ch.desgin.basicsheets.data

/** User bookmarks — named, colored markers on a page of a score (the `bookmarks` table). */
class BookmarkRepository(private val db: BasicSheetsDatabase) {

    suspend fun forFile(pdfFile: String): List<Bookmark> = db.bookmarks().forFile(pdfFile)

    /** Insert or update (REPLACE on the same id). */
    suspend fun put(bookmark: Bookmark) = db.bookmarks().insert(bookmark)

    suspend fun remove(id: String) = db.bookmarks().remove(id)
}
