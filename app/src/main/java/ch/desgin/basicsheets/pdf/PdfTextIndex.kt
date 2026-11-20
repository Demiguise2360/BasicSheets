package ch.desgin.basicsheets.pdf

import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import com.tom_roush.pdfbox.text.TextPosition
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

/** A matched-text rectangle on a page, in normalized 0..1 page coordinates (origin top-left). */
data class MatchBox(val left: Float, val top: Float, val right: Float, val bottom: Float)

/**
 * Per-page plain-text index for in-document search (Android's PdfRenderer exposes no text).
 * Extracts once per file with PDFBox and keeps it cached; search is a diacritic- and
 * case-insensitive substring scan, mirroring the MAUI app's behavior. [locateAll] re-reads the
 * hit pages with position capture to resolve each match's on-page rectangle for the highlight.
 */
class PdfTextIndex {
    private var pages: List<String> = emptyList()
    private var key: String? = null
    private var file: File? = null

    /** Build (once) the per-page text for `file`, keyed by `fileKey`. Safe to call repeatedly. */
    suspend fun ensure(file: File, fileKey: String) = withContext(Dispatchers.IO) {
        this@PdfTextIndex.file = file
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

    /**
     * Resolve the on-page rectangles of every occurrence of `query` on each of `pagesWanted`
     * (0-based PDF page indices), by re-reading those pages with position capture. Returns only
     * pages with at least one hit. Call after [ensure]; runs off the main thread.
     */
    suspend fun locateAll(query: String, pagesWanted: Set<Int>): Map<Int, List<MatchBox>> =
        withContext(Dispatchers.IO) {
            val f = file ?: return@withContext emptyMap()
            val qNorm = normLen(query.trim())
            if (qNorm.length < 2 || pagesWanted.isEmpty()) return@withContext emptyMap()
            runCatching {
                PDDocument.load(f).use { doc ->
                    val n = doc.numberOfPages
                    val result = HashMap<Int, List<MatchBox>>()
                    for (p in pagesWanted) {
                        if (p < 0 || p >= n) continue
                        val stripper = PositionStripper(p + 1)
                        runCatching { stripper.getText(doc) }   // drives the parse; capture is a side effect
                        val boxes = stripper.matchBoxes(qNorm)
                        if (boxes.isNotEmpty()) result[p] = boxes
                    }
                    result
                }
            }.getOrDefault(emptyMap())
        }

    /** A single-page text stripper that records each character's normalized page rectangle. */
    private class PositionStripper(pageNum: Int) : PDFTextStripper() {
        init {
            sortByPosition = true
            startPage = pageNum
            endPage = pageNum
        }

        private val sb = StringBuilder()
        private val boxes = ArrayList<FloatArray?>()   // one per char in `sb`; null for separators

        override fun writeString(text: String, textPositions: List<TextPosition>) {
            for (tp in textPositions) {
                val u = tp.unicode ?: continue
                if (u.isEmpty()) continue
                val pw = tp.pageWidth
                val ph = tp.pageHeight
                // yDirAdj is the glyph's lower edge from the page top; the top is yDirAdj - heightDir.
                val box: FloatArray? = if (pw > 0f && ph > 0f) {
                    floatArrayOf(
                        (tp.xDirAdj / pw).coerceIn(0f, 1f),
                        ((tp.yDirAdj - tp.heightDir) / ph).coerceIn(0f, 1f),
                        ((tp.xDirAdj + tp.widthDirAdj) / pw).coerceIn(0f, 1f),
                        (tp.yDirAdj / ph).coerceIn(0f, 1f),
                    )
                } else null
                for (c in u) { sb.append(c); boxes.add(box) }
            }
        }

        override fun writeLineSeparator() { sb.append('\n'); boxes.add(null) }
        override fun writeWordSeparator() { sb.append(' '); boxes.add(null) }

        /** Rectangles of up to 60 occurrences of the already-normalized `qNorm`, grouped per line. */
        fun matchBoxes(qNorm: String): List<MatchBox> {
            if (qNorm.length < 2 || sb.isEmpty()) return emptyList()
            val text = buildString(sb.length) { for (c in sb) append(normDiaChar(c)) }
            val out = ArrayList<MatchBox>()
            var from = 0
            var cap = 0
            while (true) {
                val k = text.indexOf(qNorm, from)
                if (k < 0) break
                groupInto(k, k + qNorm.length, out)
                from = k + qNorm.length
                if (++cap >= 60) break
            }
            return out
        }

        /** Union the per-char boxes in [start,end) into one padded rect per text line. */
        private fun groupInto(start: Int, end: Int, out: MutableList<MatchBox>) {
            var l = 0f; var t = 0f; var r = 0f; var b = 0f
            var has = false
            fun flush() {
                if (!has) return
                val padY = (b - t) * 0.18f
                val padX = (b - t) * 0.08f
                out.add(
                    MatchBox(
                        (l - padX).coerceAtLeast(0f), (t - padY).coerceAtLeast(0f),
                        (r + padX).coerceAtMost(1f), (b + padY).coerceAtMost(1f),
                    ),
                )
                has = false
            }
            for (i in start until minOf(end, boxes.size)) {
                val box = boxes[i] ?: continue   // skip separators so a phrase stays one run
                if (!has) {
                    l = box[0]; t = box[1]; r = box[2]; b = box[3]; has = true
                } else if (box[1] < b && box[3] > t) {   // vertical overlap → same line
                    l = minOf(l, box[0]); t = minOf(t, box[1]); r = maxOf(r, box[2]); b = maxOf(b, box[3])
                } else {
                    flush(); l = box[0]; t = box[1]; r = box[2]; b = box[3]; has = true
                }
            }
            flush()
        }
    }

    companion object {
        private val WS = Regex("\\s+")
        private val COMBINING = Regex("\\p{Mn}+")

        /** Diacritic- and case-insensitive, length-preserving (ä→a …) so indices stay aligned. */
        private fun normDia(s: String): String =
            Normalizer.normalize(s, Normalizer.Form.NFD).replace(COMBINING, "").lowercase()

        /** Map one char to a single diacritic-stripped, lowercase char (whitespace → space). */
        private fun normDiaChar(c: Char): Char {
            if (c.isWhitespace()) return ' '
            val d = Normalizer.normalize(c.toString(), Normalizer.Form.NFD)
            val base = d.firstOrNull { it.category != CharCategory.NON_SPACING_MARK } ?: c
            return base.lowercaseChar()
        }

        /** Length-preserving per-char normalize, so a match index maps 1:1 to captured rects. */
        private fun normLen(s: String): String = buildString(s.length) { for (c in s) append(normDiaChar(c)) }
    }
}
