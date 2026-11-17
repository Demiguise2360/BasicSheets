package ch.desgin.basicsheets.net

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/** A composer's Wikipedia blurb. */
data class ComposerInfo(val extract: String, val imageUrl: String?, val pageUrl: String?)

/**
 * Minimal Wikipedia REST client (summary + thumbnail) for the composer view, using a generic
 * client name and built-in JSON — no extra dependency. Best-effort: any failure returns null.
 * Only called when the user has composer info enabled in settings.
 */
object Wikipedia {
    private const val UA = "BasicSheetsApp/1.0 (Android; sheet-music reader)"

    suspend fun summary(name: String, lang: String = "de"): ComposerInfo? = withContext(Dispatchers.IO) {
        runCatching {
            val title = URLEncoder.encode(name.trim().replace(' ', '_'), "UTF-8")
            val text = get("https://$lang.wikipedia.org/api/rest_v1/page/summary/$title") ?: return@runCatching null
            val o = JSONObject(text)
            val extract = o.optString("extract", "")
            val img = o.optJSONObject("thumbnail")?.optString("source")?.takeIf { it.isNotBlank() }
            val page = o.optJSONObject("content_urls")?.optJSONObject("desktop")?.optString("page")?.takeIf { it.isNotBlank() }
            if (extract.isBlank() && img == null) null else ComposerInfo(extract, img, page)
        }.getOrNull()
    }

    suspend fun image(url: String): Bitmap? = withContext(Dispatchers.IO) {
        runCatching {
            val conn = (URL(url).openConnection() as HttpURLConnection).apply {
                setRequestProperty("User-Agent", UA)
                connectTimeout = 8000
                readTimeout = 8000
            }
            conn.inputStream.use { BitmapFactory.decodeStream(it) }
        }.getOrNull()
    }

    private fun get(urlStr: String): String? {
        val conn = (URL(urlStr).openConnection() as HttpURLConnection).apply {
            setRequestProperty("User-Agent", UA)
            setRequestProperty("Accept", "application/json")
            connectTimeout = 8000
            readTimeout = 8000
        }
        return if (conn.responseCode in 200..299) {
            conn.inputStream.bufferedReader().use { it.readText() }
        } else {
            conn.disconnect()
            null
        }
    }
}
