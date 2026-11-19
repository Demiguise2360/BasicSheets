package ch.desgin.basicsheets.data

import kotlinx.coroutines.flow.Flow

/** Collections ("Sammlungen") — named, colored groupings of sheets (collections / sheet_collections). */
class CollectionRepository(private val db: BasicSheetsDatabase) {

    fun observeAll(): Flow<List<Collection>> = db.collections().observeAll()

    suspend fun get(id: Int): Collection? = db.collections().byId(id)

    suspend fun create(name: String, color: String): Long =
        db.collections().insert(Collection(name = name, color = color))

    suspend fun update(collection: Collection) = db.collections().update(collection)

    suspend fun delete(collection: Collection) {
        db.collections().clearLinks(collection.id)
        db.collections().delete(collection)
    }

    /** The sheets in a collection, by name. */
    suspend fun sheets(id: Int): List<Sheet> = db.collections().sheets(id)

    suspend fun count(id: Int): Int = db.collections().count(id)

    suspend fun addSheet(collectionId: Int, sheetId: Int) =
        db.collections().link(SheetCollectionLink(sheetId = sheetId, collectionId = collectionId))

    suspend fun removeSheet(collectionId: Int, sheetId: Int) =
        db.collections().unlink(sheetId, collectionId)

    suspend fun collectionsFor(sheetId: Int): List<Int> = db.collections().collectionIdsFor(sheetId)
}
