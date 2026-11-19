package ch.desgin.basicsheets.pdf

import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.interactive.documentnavigation.outline.PDOutlineNode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** One flattened entry of a PDF's embedded outline (table of contents). `page` is 0-based, -1 if none. */
data class OutlineEntry(val title: String, val page: Int, val level: Int)

/**
 * Reads a PDF's embedded outline with PDFBox (Android's PdfRenderer exposes no TOC). Returns a
 * flat, indent-leveled list; empty when the document has no outline or can't be parsed.
 */
object PdfOutline {

    suspend fun extract(file: File): List<OutlineEntry> = withContext(Dispatchers.IO) {
        runCatching {
            PDDocument.load(file).use { doc ->
                val root = doc.documentCatalog?.documentOutline ?: return@use emptyList()
                val out = ArrayList<OutlineEntry>()
                walk(doc, root, 0, out)
                out
            }
        }.getOrDefault(emptyList())
    }

    private fun walk(doc: PDDocument, node: PDOutlineNode, level: Int, out: MutableList<OutlineEntry>) {
        for (item in node.children()) {
            val page = runCatching { item.findDestinationPage(doc)?.let { doc.pages.indexOf(it) } ?: -1 }.getOrDefault(-1)
            val title = item.title?.trim().let { if (it.isNullOrEmpty()) "—" else it }
            out.add(OutlineEntry(title, page, level))
            walk(doc, item, level + 1, out)
        }
    }
}
