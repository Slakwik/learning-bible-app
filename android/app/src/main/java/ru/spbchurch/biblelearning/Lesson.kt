package ru.spbchurch.biblelearning

import android.content.Context
import android.util.AtomicFile
import org.json.JSONObject
import java.io.File
import java.net.URL
import javax.net.ssl.HttpsURLConnection

sealed class LessonBlock {
    data class Reading(val markdown: String) : LessonBlock()
    data class Question(val id: String, val label: String) : LessonBlock()
}
data class Lesson(val slug: String, val title: String, val reference: String, val course: String,
                  val order: Int, val body: String) {
    val blocks: List<LessonBlock> get() = LessonParser.blocks(body)
    val questions get() = blocks.filterIsInstance<LessonBlock.Question>()
}
object LessonParser {
    fun parse(raw: String): Lesson {
        val pieces = raw.replace("\r\n", "\n").split(Regex("(?m)^---\\s*$"), limit = 3)
        require(pieces.size == 3) { "Missing lesson metadata" }
        val fields = pieces[1].lineSequence().filter { ':' in it }.associate {
            it.substringBefore(':').trim() to it.substringAfter(':').trim().removeSurrounding("\"")
        }
        return validate(Lesson(fields.getValue("slug"), fields.getValue("title"),
            fields["reference"].orEmpty(), fields.getValue("course"),
            fields.getValue("order").toInt(), pieces[2].trim()))
    }
    fun validate(lesson: Lesson): Lesson {
        require(lesson.slug.matches(Regex("[a-z0-9-]+")) && lesson.course.matches(Regex("[a-z0-9-]+")))
        require(lesson.title.isNotBlank() && lesson.order > 0 && lesson.body.isNotBlank())
        val questions = lesson.questions
        require(questions.map { it.id }.distinct().size == questions.size) { "Duplicate question IDs" }
        require(questions.size == Regex("<textarea\\b").findAll(lesson.body).count()) { "Unparsed question" }
        return lesson
    }
    fun blocks(body: String): List<LessonBlock> {
        val pattern = Regex("""<div\s+class="question-block"\s*>\s*<label\s+for="(q[0-9]+[a-z]*)"\s*>(.*?)</label>\s*<textarea\b[^>]*>.*?</textarea>\s*</div>""",
            RegexOption.DOT_MATCHES_ALL)
        val result = mutableListOf<LessonBlock>()
        var start = 0
        pattern.findAll(body).forEach { match ->
            body.substring(start, match.range.first).trim().takeIf { it.isNotEmpty() }
                ?.let { result += LessonBlock.Reading(it) }
            result += LessonBlock.Question(match.groupValues[1], match.groupValues[2])
            start = match.range.last + 1
        }
        body.substring(start).trim().takeIf { it.isNotEmpty() }?.let { result += LessonBlock.Reading(it) }
        return result
    }
}
object Courses {
    val names = linkedMapOf(
        "bible-book" to "Библия: Божья удивительная книга", "james" to "Послание Иакова",
        "mark" to "Евангелие от Марка", "ephesians" to "Послание к Ефесянам",
        "galatians" to "Послание к Галатам", "philippians" to "Послание к Филиппийцам",
        "proverbs" to "Мудрость Соломона", "christian-life" to "Жизнь христианина",
        "deuteronomy" to "Второзаконие", "genesis1" to "Бытие · Часть 1", "genesis2" to "Бытие · Часть 2",
        "joshua" to "Иисус Навин", "amos-isaiah" to "Амос и Исаия", "daniel" to "Даниил",
        "acts1" to "Деяния · Часть 1", "acts2" to "Деяния · Часть 2",
        "corinthians1" to "1 Коринфянам", "corinthians2" to "2 Коринфянам",
        "thessalonians" to "1–2 Фессалоникийцам", "first-john" to "Послание 1 Иоанна",
        "prayer" to "Если будете молиться")
    fun name(id: String) = names[id] ?: id
}
class LessonRepository(private val context: Context) {
    private val cache get() = AtomicFile(File(context.filesDir, "catalog-v1.json"))
    fun all(): List<Lesson> {
        val cached = runCatching { decode(cache.openRead().bufferedReader().use { it.readText() }) }.getOrNull()
        return cached ?: context.assets.list("lessons").orEmpty().filter { it.endsWith(".md") }.map { file ->
            context.assets.open("lessons/$file").bufferedReader().use { LessonParser.parse(it.readText()) }
        }
    }
    fun get(slug: String) = all().firstOrNull { it.slug == slug }
    private fun decode(raw: String): List<Lesson> {
        val json = JSONObject(raw)
        require(json.getInt("schema") == 1)
        val array = json.getJSONArray("lessons")
        require(array.length() in 1..1000)
        val lessons = (0 until array.length()).map { index ->
            val l = array.getJSONObject(index)
            LessonParser.validate(Lesson(l.getString("slug"), l.getString("title"),
                l.optString("reference"), l.getString("course"), l.getInt("order"), l.getString("body")))
        }
        require(lessons.map { it.slug }.distinct().size == lessons.size)
        return lessons
    }
    /** Download is validated fully before replacing the last working catalog. */
    fun update(): Int {
        val connection = URL("https://learning.spbchurch.ru/assets/mobile-catalog.json").openConnection() as HttpsURLConnection
        connection.connectTimeout = 15_000
        connection.readTimeout = 20_000
        connection.instanceFollowRedirects = false
        try {
            check(connection.responseCode == 200) { "Каталог обновлений пока недоступен на сайте." }
            val bytes = connection.inputStream.use { input ->
                val output = java.io.ByteArrayOutputStream()
                val buffer = ByteArray(8192)
                while (true) {
                    val read = input.read(buffer)
                    if (read == -1) break
                    check(output.size() + read <= 8 * 1024 * 1024) { "Catalog too large" }
                    output.write(buffer, 0, read)
                }
                output.toByteArray()
            }
            val lessons = decode(bytes.toString(Charsets.UTF_8))
            val file = cache
            val stream = file.startWrite()
            try { stream.write(bytes); file.finishWrite(stream) }
            catch (e: Exception) { file.failWrite(stream); throw e }
            return lessons.size
        } finally { connection.disconnect() }
    }
    fun downloadedBytes() = File(context.filesDir, "catalog-v1.json").length()
    fun clearDownloaded() { cache.delete() } // Only fetched content; bundled lessons and answers are untouched.
}
