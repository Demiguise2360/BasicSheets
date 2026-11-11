package ch.desgin.basicsheets.pdf

import android.graphics.Bitmap
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
 * Thin wrapper over Android's PdfRenderer: open the file once, render pages to bitmaps on demand.
 * PdfRenderer allows only one open page at a time and isn't thread-safe, so every render is
 * serialized by a mutex and runs off the main thread.
 */
class PdfDoc private constructor(
    private val pfd: ParcelFileDescriptor,
    private val renderer: PdfRenderer,
) : Closeable {

    val pageCount: Int get() = renderer.pageCount
    private val lock = Mutex()

    suspend fun render(index: Int, widthPx: Int): Bitmap? = lock.withLock {
        withContext(Dispatchers.IO) {
            if (index < 0 || index >= renderer.pageCount || widthPx <= 0) return@withContext null
            runCatching {
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

    companion object {
        fun open(file: File): PdfDoc? = runCatching {
            val pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
            PdfDoc(pfd, PdfRenderer(pfd))
        }.getOrNull()
    }
}
