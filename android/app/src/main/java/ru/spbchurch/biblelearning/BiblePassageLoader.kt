package ru.spbchurch.biblelearning

import java.net.URL
import javax.net.ssl.HttpsURLConnection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.jsoup.Jsoup
import androidx.core.text.HtmlCompat

data class BiblePassage(val reference: String, val text: String, val attribution: String)
class BiblePassageException(message: String) : Exception(message)
fun interface BiblePassageLoader { suspend fun load(reference: BibleReference): BiblePassage }

/** Official API, plain text rendering and current version attribution; no website scraping. */
class YouVersionPassageLoader(
    private val appKey: String = BuildConfig.YOUVERSION_APP_KEY,
    private val version: Int = BuildConfig.YOUVERSION_BIBLE_ID,
    private val fetch: (String, String) -> String = ::fetchYouVersion
) : BiblePassageLoader {
    override suspend fun load(reference: BibleReference): BiblePassage = withContext(Dispatchers.IO) {
        if (appKey.isBlank()) throw BiblePassageException("Встроенное чтение ещё не подключено к источнику текста.")
        require(reference.book in BibleReferences.books.map { it.code })
        require(reference.location.isNotEmpty() && reference.location.all { it in "0123456789.-" })
        if (reference.endpoints.size > 1) throw BiblePassageException("Выберите одну главу отрывка.")
        val base = "https://api.youversion.com/v1/bibles/$version"
        val passage = JSONObject(fetch("$base/passages/${reference.book}.${reference.location}?format=html&include_notes=true", appKey))
        val metadata = JSONObject(fetch(base, appKey))
        val text = renderPassageHtml(passage.stringOrEmpty("content"))
        val credit = metadata.stringOrEmpty("copyright").trim().ifBlank { metadata.stringOrEmpty("promotional_content").trim() }
        if (text.isBlank() || credit.isBlank()) throw BiblePassageException("Источник не вернул текст или сведения о переводе. Повторите позже.")
        BiblePassage(passage.optString("reference").ifBlank { "${reference.book} ${reference.location}" }, text, credit)
    }
}
private fun fetchYouVersion(url: String, appKey: String): String {
    val connection = URL(url).openConnection() as HttpsURLConnection
    connection.connectTimeout = 10_000
    connection.readTimeout = 15_000
    connection.instanceFollowRedirects = false
    connection.setRequestProperty("X-YVP-App-Key", appKey)
    connection.setRequestProperty("Accept", "application/json")
    try {
        when (connection.responseCode) {
            200 -> Unit
            401, 403 -> throw BiblePassageException("Источник пока не предоставил приложению доступ к этому переводу.")
            404 -> throw BiblePassageException("Отрывок не найден. Проверьте книгу, главу и стихи.")
            429 -> throw BiblePassageException("Источник временно ограничил запросы. Попробуйте чуть позже.")
            else -> throw BiblePassageException("Источник текста временно недоступен. Повторите позже.")
        }
        return connection.inputStream.use { input ->
            val bytes = input.readBytesBounded(1024 * 1024)
            bytes.toString(Charsets.UTF_8)
        }
    } finally { connection.disconnect() }
}
internal fun java.io.InputStream.readBytesBounded(limit: Int): ByteArray {
    val output = java.io.ByteArrayOutputStream()
    val buffer = ByteArray(8192)
    while (true) {
        val read = read(buffer)
        if (read < 0) break
        if (output.size() + read > limit) throw BiblePassageException("Отрывок слишком большой. Выберите меньше глав.")
        output.write(buffer, 0, read)
    }
    return output.toByteArray()
}

private fun JSONObject.stringOrEmpty(key: String): String = if (isNull(key)) "" else optString(key)

/** Notes stay available below the passage; no WebView, remote images or executable HTML. */
internal fun renderPassageHtml(html: String): String {
    val document = Jsoup.parseBodyFragment(html)
    document.select("script,style,img").remove()
    val notes = document.select(".yv-n").mapIndexed { index, note ->
        val text = note.text()
        note.empty().text(" [${index + 1}] ")
        "[${index + 1}] $text"
    }
    document.select(".yv-vlbl").forEach { it.after(" ") }
    val text = HtmlCompat.fromHtml(document.body().html(), HtmlCompat.FROM_HTML_MODE_LEGACY).toString().trim()
    return text + if (notes.isEmpty()) "" else "\n\nПримечания\n" + notes.joinToString("\n\n")
}

internal fun readingPages(reference: BibleReference): List<BibleReference> {
    if (reference.endpoints.size == 1) return listOf(reference)
    val first = reference.endpoints.first().substringBefore('.').toIntOrNull() ?: return listOf(reference)
    val last = reference.endpoints.last().substringBefore('.').toIntOrNull() ?: return listOf(reference)
    if (first !in 1..176 || last !in first..176) return listOf(reference)
    return (first..last).map { reference.copy(location = it.toString()) }
}
