package ru.spbchurch.biblelearning

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

/** Reads the bundled public-domain edition only; never contacts a network service. */
class SynodalPassageLoader(context: Context) : BiblePassageLoader {
    private val assets = context.applicationContext.assets
    override suspend fun load(reference: BibleReference): BiblePassage = withContext(Dispatchers.IO) {
        val book = BibleReferences.books.firstOrNull { it.code == reference.book }
            ?: throw BiblePassageException("Книга не найдена.")
        val parts = reference.location.split('-', limit = 2)
        val start = parts[0].split('.')
        val chapter = start.first().toIntOrNull() ?: throw BiblePassageException("Проверьте номер главы.")
        if (reference.endpoints.size > 1 || start.size > 2) throw BiblePassageException("Выберите одну главу отрывка.")
        val data = assets.open("bible-synodal/${book.code}.json").bufferedReader().use { JSONObject(it.readText()) }
        val verses = data.optJSONObject(chapter.toString()) ?: throw BiblePassageException("В этой книге нет главы $chapter.")
        val first = if (start.size == 2) start[1].toIntOrNull() else null
        val last = if (parts.size == 2) parts[1].toIntOrNull() else first
        if ((start.size == 2 && first == null) || (parts.size == 2 && last == null)) throw BiblePassageException("Проверьте номера стихов.")
        val numbers = verses.keys().asSequence().map { it.toInt() }.sorted().toList()
        if (first != null && (first !in numbers || last !in numbers || last!! < first)) {
            throw BiblePassageException("В этой главе нет указанного диапазона стихов.")
        }
        val selected = numbers.filter { first == null || it in first..last!! }
        BiblePassage("${book.name} ${reference.location.replace('.', ':')}",
            selected.joinToString("\n\n") { "$it ${verses.getString(it.toString())}" },
            "Синодальный перевод · общественное достояние\nИсточник: eBible.org. Текст встроен в приложение.")
    }
}
