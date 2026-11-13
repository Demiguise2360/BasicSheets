package ch.desgin.basicsheets.pdf

import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.Normalizer

/** One search result: a page that contains the query, with a snippet and the match's position in it. */
data class SearchHit(
    val page: Int,          // 0-based
    val snippet: String,
    val count: Int,         // occurrences on this page
    val matchStart: Int,    // index of the match within `snippet`
    val matchLen: Int,
)

/**
 * Per-page plain-text index for in-document search (Android's PdfRenderer exposes no text).
 * Extracts once per file with PDFBox and keeps it cached; search is a diacritic- and
 * case-insensitive substring scan, mirroring the MAUI app's behavior.
 */
class PdfTextIndex {
    private var pages: List<String> = emptyList()
    private var key: String? = null

    /** Build (once) the per-page text for `file`, keyed by `fileKey`. Safe to call repeatedly. */
    suspend fun ensure(file: File, fileKey: String) = withContext(Dispatchers.IO) {
        if (key == fileKey && pages.isNotEmpty()) return@withContext
        val extracted = runCatching {
            PDDocument.load(file).use { doc ->
                val stripper = PDFTextStripper().apply { sortByPosition = true }
                (1..doc.numberOfPages).map { p ->
                    runCatching {
                        stripper.startPage = p
                        stripper.endPage = p
                        stripper.getText(doc).replace(WS, " ").trim()
                    }.getOrDefault("")
                }
            }
        }.getOrDefault(emptyList())
        pages = extracted
        key = fileKey
    }

    /** All pages containing `query` (≥ 2 chars), with a snippet each. Call after [ensure]. */
    fun search(query: String): List<SearchHit> {
        val q = normDia(query.trim())
        if (q.length < 2) return emptyList()
        val out = ArrayList<SearchHit>()
        for ((i, text) in pages.withIndex()) {
            val norm = normDia(text)                 // same length as text, so indices line up
            val idx = norm.indexOf(q)
            if (idx < 0) continue
            var count = 0
            var from = 0
            while (true) {
                val k = norm.indexOf(q, from)
                if (k < 0) break
                count++; from = k + q.length
            }
            // norm is normally the same length as text, but coerce in case normalization shifted it.
            val tLen = text.length
            val mEnd = (idx + q.length).coerceIn(0, tLen)
            val mStart = idx.coerceIn(0, mEnd)
            val start = maxOf(0, mStart - 30)
            val pre = ((if (start > 0) "…" else "") + text.substring(start, mStart)).trimStart()
            val mid = text.substring(mStart, mEnd)
            val postEnd = minOf(tLen, mEnd + 45)
            val post = (text.substring(mEnd, postEnd) + (if (postEnd < tLen) "…" else "")).trimEnd()
            out.add(SearchHit(i, pre + mid + post, count, pre.length, mid.length))
        }
        return out
    }

    companion object {
        private val WS = Regex("\\s+")
        private val COMBINING = Regex("\\p{Mn}+")

        /** Diacritic- and case-insensitive, length-preserving (ä→a …) so indices stay aligned. */
        private fun normDia(s: String): String =
            Normalizer.normalize(s, Normalizer.Form.NFD).replace(COMBINING, "").lowercase()
    }
}
