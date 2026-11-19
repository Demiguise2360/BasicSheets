package ch.desgin.basicsheets.data

import kotlinx.coroutines.flow.Flow

/** Setlists — ordered lists of songs to perform from (the setlists / setlist_items tables). */
class SetlistRepository(private val db: BasicSheetsDatabase) {

    fun observeAll(): Flow<List<Setlist>> = db.setlists().observeAll()

    suspend fun get(id: Int): Setlist? = db.setlists().byId(id)

    suspend fun create(name: String): Long = db.setlists().insert(Setlist(name = name))

    suspend fun rename(id: Int, name: String) {
        db.setlists().byId(id)?.let {
            db.setlists().update(it.copy(name = name, updated = System.currentTimeMillis()))
        }
    }

    suspend fun delete(setlist: Setlist) {
        db.setlists().clearItems(setlist.id)
        db.setlists().delete(setlist)
    }

    suspend fun items(id: Int): List<SetlistItem> = db.setlists().items(id)

    /** The setlist's sheets, in order. */
    suspend fun sheets(id: Int): List<Sheet> = db.setlists().sheets(id)

    suspend fun addSheet(setlistId: Int, sheetId: Int) {
        val next = (db.setlists().items(setlistId).maxOfOrNull { it.order } ?: -1) + 1
        db.setlists().insertItem(SetlistItem(setlistId = setlistId, sheetId = sheetId, order = next))
    }

    suspend fun removeItem(item: SetlistItem) = db.setlists().deleteItem(item)

    /** Swap a row with its neighbor (dir -1 = up, +1 = down). */
    suspend fun move(setlistId: Int, itemId: Int, dir: Int) {
        val items = db.setlists().items(setlistId)
        val idx = items.indexOfFirst { it.id == itemId }
        val swap = idx + dir
        if (idx < 0 || swap < 0 || swap >= items.size) return
        val a = items[idx]
        val b = items[swap]
        db.setlists().updateItem(a.copy(order = b.order))
        db.setlists().updateItem(b.copy(order = a.order))
    }
}
