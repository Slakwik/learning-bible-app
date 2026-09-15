package ru.spbchurch.biblelearning

import java.net.URL
import javax.net.ssl.HttpsURLConnection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

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
        val base = "https://api.youversion.com/v1/bibles/$version"
        val passage = JSONObject(fetch("$base/passages/${reference.book}.${reference.location}?format=text&include_notes=false", appKey))
        val metadata = JSONObject(fetch(base, appKey))
        val text = passage.optString("content").trim()
        val credit = metadata.optString("copyright").trim().ifBlank { metadata.optString("promotional_content").trim() }
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
