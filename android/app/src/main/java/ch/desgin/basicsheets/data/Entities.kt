package ch.desgin.basicsheets.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Room schema — a faithful port of the MAUI app's sqlite-net models under
 * BasicSheets.Shared/Model. Dates are stored as epoch millis.
 * This is a fresh database; migrating an existing MAUI library is a separate task (see PORTING.md).
 */

@Entity(tableName = "sheets", indices = [Index("filePath")])
data class Sheet(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String = "",
    val filePath: String = "",          // primary PDF file name in the app's sheets folder
    val composer: String = "",
    val part: String = "",              // instrument / voice
    val genre: String = "",
    val year: String = "",
    val difficulty: String = "",        // "" | beginner | intermediate | advanced | expert
    val tags: String = "",              // comma-separated
    val remarks: String = "",
    val rating: Int = 0,                // 0..5
    val keywords: String = "",
    val pageStart: Int = 0,             // 0 = whole file; >0 = song is pages [pageStart,pageEnd]
    val pageEnd: Int = 0,
    val transpose: Int = 0,             // semitones, ChordPro/text songs
    val rotation: Int = 0,              // 0 | 90 | 180 | 270
    val pageOrder: String = "",         // CSV of 1-based pages ("" = natural)
    val bpm: Int = 0,                   // per-song metronome tempo (0 = default 100)
    val beats: Int = 0,                 // per-song beats/measure (0 = default 4)
    val audioPath: String = "",         // backing-track file name ("" = none)
    val scrollSpeed: Int = 0,           // per-song auto-scroll px/s (0 = global default)
    val halfSplit: Double = 0.5,        // per-song half-page fold position (was localStorage in MAUI)
    val pageMode: String? = null,       // null = global; single | two | half-turn | scroll-v | scroll-h
    val created: Long = System.currentTimeMillis(),
    val updated: Long = System.currentTimeMillis(),
) {
    val hasRange: Boolean get() = pageStart > 0 && pageEnd >= pageStart
    fun information(): String =
        listOf(composer, part, genre, year).filter { it.isNotBlank() }.joinToString(" · ")
}

@Entity(tableName = "setlists")
data class Setlist(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String = "",
    val sortOrder: Int = 0,
    val created: Long = System.currentTimeMillis(),
    val updated: Long = System.currentTimeMillis(),
)

@Entity(tableName = "setlist_items", indices = [Index("setlistId")])
data class SetlistItem(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val setlistId: Int = 0,
    val sheetId: Int = 0,
    val order: Int = 0,
)

@Entity(tableName = "bookmarks", indices = [Index("pdfFile")])
data class Bookmark(
    @PrimaryKey val id: String,
    val pdfFile: String = "",
    val sheetName: String = "",
    val page: Int = 1,
    val label: String = "",
    val color: String = "#387373",
    val createdAt: Long = System.currentTimeMillis(),
)

/** Link points (tappable jump regions) + legacy floating buttons. */
@Entity(tableName = "viewer_buttons", indices = [Index("pdfFile")])
data class ViewerButton(
    @PrimaryKey val id: String,
    val label: String = "",
    val action: String = "jump",        // jump | first | last | bookmark | go_home | toggle_edit
    val param: String = "",
    val sortOrder: Int = 0,
    val pdfFile: String? = null,
    val sourcePage: Int = 0,            // 1-based page the point sits on
    val sourceNx: Double = -1.0,        // normalized x in [0,1], -1 = not placed
    val sourceNy: Double = -1.0,
    val color: String = "#387373",
    val targetPage: Int = 1,
    val targetNx: Double = -1.0,        // -1 = no visual destination marker
    val targetNy: Double = -1.0,
)

@Entity(tableName = "collections")
data class Collection(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String = "",
)

@Entity(tableName = "sheet_collections", indices = [Index("sheetId"), Index("collectionId")])
data class SheetCollectionLink(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val sheetId: Int = 0,
    val collectionId: Int = 0,
)

@Entity(tableName = "sheet_files", indices = [Index("sheetId")])
data class SheetFile(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val sheetId: Int = 0,
    val path: String = "",
    val kind: String = "pdf",           // pdf | image | text
    val pageStart: Int = 0,
    val pageEnd: Int = 0,
    val order: Int = 0,
    val label: String = "",
)

/** Crop rectangle (fractions 0..1) for one page of a song. */
@Entity(tableName = "crops", indices = [Index("sheetId")])
data class Crop(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val sheetId: Int = 0,
    val page: Int = 1,
    val l: Double = 0.0,
    val t: Double = 0.0,
    val r: Double = 1.0,
    val b: Double = 1.0,
)

/** Persisted name→value user setting (full definitions are rebuilt in code; see Defaults). */
@Entity(tableName = "settings_kv")
data class SettingRow(
    @PrimaryKey val name: String,
    val value: String = "",
)

/**
 * Per-page annotation blob. In the MAUI app these lived in the WebView's localStorage under
 * keys like bsv2_<pdfKey>_p<page>; here they are rows. The JSON shape is unchanged:
 * { penStrokes, markerData, stampData, textData, hairpinData, shapeData, w, h }.
 */
@Entity(tableName = "annotations", primaryKeys = ["pdfKey", "page"])
data class AnnotationRow(
    val pdfKey: String,
    val page: Int,
    val json: String,
)
