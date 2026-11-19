package ch.desgin.basicsheets.pdf

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.Closeable
import java.io.File

/**
 * A sheet-music document the viewer renders page by page. Backed by one or more sources — PDF files
 * (via Android's PdfRenderer) and/or single images — concatenated into one page sequence, so an
 * image song, a PDF, and a multi-file song all expose the same [pageCount] + [render] API.
 * PdfRenderer allows one open page at a time and isn't thread-safe, so each PDF source serializes
 * its renders behind a mutex and runs off the main thread.
 */
class PdfDoc private constructor(private val sources: List<Source>) : Closeable {

    val pageCount: Int = sources.sumOf { it.pageCount }

    /** Render the [index]-th page (across all sources) to a bitmap [widthPx] wide, or null. */
    suspend fun render(index: Int, widthPx: Int): Bitmap? {
        if (index < 0 || widthPx <= 0 || index >= pageCount) return null
        var local = index
        for (src in sources) {
            if (local < src.pageCount) return src.render(local, widthPx)
            local -= src.pageCount
        }
        return null
    }

    override fun close() { for (s in sources) runCatching { s.close() } }

    // --- page sources ----------------------------------------------------------

    private interface Source : Closeable {
        val pageCount: Int
        suspend fun render(index: Int, widthPx: Int): Bitmap?
        override fun close() {}
    }

    /** One PDF file; its own renderer + lock (PdfRenderer is single-page, non-thread-safe). */
    private class PdfSource(
        private val pfd: ParcelFileDescriptor,
        private val renderer: PdfRenderer,
    ) : Source {
        override val pageCount: Int get() = renderer.pageCount
        private val lock = Mutex()
        override suspend fun render(index: Int, widthPx: Int): Bitmap? = lock.withLock {
            withContext(Dispatchers.IO) {
                // A page may be requested just as the doc closes (navigation) — return null instead
                // of crashing with "Document already closed".
                runCatching {
                    if (index < 0 || widthPx <= 0 || index >= renderer.pageCount) return@runCatching null
                    renderer.openPage(index).use { page ->
                        val w = widthPx
                        val h = (w.toFloat() * page.height / page.width).toInt().coerceAtLeast(1)
                        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                        bmp.eraseColor(Color.WHITE)
                        page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        bmp
                    }
                }.getOrNull()
            }
        }
        override fun close() {
            runCatching { renderer.close() }
            runCatching { pfd.close() }
        }
    }

    /** One image file = one page; decoded (down-sampled) and scaled to the requested width. */
    private class ImageSource(private val file: File) : Source {
        override val pageCount: Int get() = 1
        override suspend fun render(index: Int, widthPx: Int): Bitmap? {
            if (index != 0 || widthPx <= 0) return null
            return withContext(Dispatchers.IO) {
                runCatching {
                    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    BitmapFactory.decodeFile(file.absolutePath, bounds)
                    val srcW = bounds.outWidth
                    val srcH = bounds.outHeight
                    if (srcW <= 0 || srcH <= 0) return@runCatching null
                    // Down-sample so the decode stays near the target width, then scale exactly.
                    var sample = 1
                    while (srcW / (sample * 2) >= widthPx) sample *= 2
                    val opts = BitmapFactory.Options().apply { inSampleSize = sample }
                    val decoded = BitmapFactory.decodeFile(file.absolutePath, opts) ?: return@runCatching null
                    val h = (widthPx.toFloat() * decoded.height / decoded.width).toInt().coerceAtLeast(1)
                    val scaled = Bitmap.createScaledBitmap(decoded, widthPx, h, true)
                    if (scaled != decoded) decoded.recycle()
                    scaled
                }.getOrNull()
            }
        }
    }

    companion object {
        private val IMAGE_EXTS = setOf("png", "jpg", "jpeg", "webp", "gif", "bmp")

        /** True when a file name looks like a supported image (so it's opened as an image page). */
        fun isImage(name: String): Boolean =
            name.substringAfterLast('.', "").lowercase() in IMAGE_EXTS

        /** Open a single file, choosing an image or PDF source by its extension. */
        fun open(file: File): PdfDoc? = runCatching {
            if (isImage(file.name)) PdfDoc(listOf(ImageSource(file))) else openPdf(file)
        }.getOrNull()

        private fun openPdf(file: File): PdfDoc? = runCatching {
            val pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
            PdfDoc(listOf(PdfSource(pfd, PdfRenderer(pfd))))
        }.getOrNull()

        /** Open several files (PDFs and/or images) concatenated into one page sequence. */
        fun openFiles(files: List<File>): PdfDoc? = runCatching {
            val sources = files.mapNotNull { f ->
                runCatching {
                    if (isImage(f.name)) ImageSource(f)
                    else ParcelFileDescriptor.open(f, ParcelFileDescriptor.MODE_READ_ONLY).let { pfd -> PdfSource(pfd, PdfRenderer(pfd)) }
                }.getOrNull()
            }
            if (sources.isEmpty()) null else PdfDoc(sources)
        }.getOrNull()
    }
}
